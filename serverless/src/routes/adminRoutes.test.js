// serverless/src/routes/adminRoutes.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import { sign } from "hono/jwt";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";
import { uploadToCDN } from "../services/storageService";
import { verifyPassword } from "../utils/password";

// --- MOCK ALL MODULES AT THE TOP LEVEL ---
vi.mock("../db/d1-client.js");
vi.mock("../services/storageService.js");
vi.mock("../utils/password.js"); // Mock the password utility

// --- DEFINE CONSTANTS AT THE TOP LEVEL ---
const MOCK_ADMIN_ENV = {
  JWT_SECRET_ADMIN: "a-secure-admin-secret",
  R2_PUBLIC_URL: "https://fake-r2-url.com",
  DB: db,
};
const mockAdmin = {
  id: "admin-1",
  email: "admin@test.com",
  password: "hashed-password",
  role: "admin",
};

describe("Admin Routes", () => {
  // This runs before each test in BOTH describe blocks
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("Admin Content Routes", () => {
    beforeEach(() => {
      // For protected routes, ensure the admin lookup is mocked successfully
      db.findAdminByEmail.mockResolvedValue(mockAdmin);
    });

    it("POST /api/admin/content/part1.1 should be protected", async () => {
      const res = await app.request(
        "/api/admin/content/part1.1",
        { method: "POST" },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(401);
    });

    it("POST /api/admin/content/part1.1 should upload content and save to DB", async () => {
      const fakeAudioUrl = "https://fake-r2-url.com/audio/12345-test-audio.mp3";
      const mockQuestionText = "What is your favorite color?";

      uploadToCDN.mockResolvedValue(fakeAudioUrl);
      db.createContent.mockResolvedValue({
        id: "content-xyz-123",
        questionText: mockQuestionText,
        audioUrl: fakeAudioUrl,
      });

      const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);

      const formData = new FormData();
      const audioFile = new File(["fake audio content"], "test-audio.mp3", { type: "audio/mpeg" });
      formData.append("questionText", mockQuestionText);
      formData.append("audio", audioFile);

      const res = await app.request(
        "/api/admin/content/part1.1",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}` },
          body: formData,
        },
        MOCK_ADMIN_ENV
      );

      expect(res.status).toBe(201);
      const body = await res.json();
      expect(body.message).toContain("uploaded successfully");
    });

    it("POST /api/admin/content/part1.2 should return 400 if a required file is missing", async () => {
      const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);
      const formData = new FormData();
      formData.append("question1", "q1");
      formData.append("image1", new File([""], "img1.jpg"));
      // OMITTING other required fields

      const res = await app.request(
        "/api/admin/content/part1.2",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}` },
          body: formData,
        },
        MOCK_ADMIN_ENV
      );

      expect(res.status).toBe(400);
      expect(uploadToCDN).not.toHaveBeenCalled();
      expect(db.createContent).not.toHaveBeenCalled();
    });

    it("POST /api/admin/content/part3 should upload a new topic", async () => {
      uploadToCDN.mockResolvedValue(null);
      db.createContent.mockResolvedValue({ id: "part3-abc", topic: "Technology" });
      const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);

      const formData = new FormData();
      formData.append("topic", "The Impact of AI");
      formData.append("forPoints", "Increases efficiency\nCreates new jobs");
      formData.append("againstPoints", "Job displacement\nEthical concerns");

      const res = await app.request(
        "/api/admin/content/part3",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}` },
          body: formData,
        },
        MOCK_ADMIN_ENV
      );

      expect(res.status).toBe(201);
    });

    it("should return 500 on database error during part1.1 upload", async () => {
      uploadToCDN.mockResolvedValue("http://fake.url");
      db.createContent.mockRejectedValue(new Error("DB Error"));
      const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);

      const formData = new FormData();
      formData.append("questionText", "text");
      formData.append("audio", new File([""], "f.mp3"));

      const res = await app.request(
        "/api/admin/content/part1.1",
        { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: formData },
        MOCK_ADMIN_ENV
      );

      expect(res.status).toBe(500);
    });

    it("should return 500 on database error during word bank upload", async () => {
      db.createContent.mockRejectedValue(new Error("DB Error"));
      const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);

      const formData = new FormData();
      formData.append("word", "test");
      formData.append("translation", "test");
      formData.append("cefrLevel", "A1");
      formData.append("topic", "test");

      const res = await app.request(
        "/api/admin/wordbank/add",
        { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: formData },
        MOCK_ADMIN_ENV
      );

      expect(res.status).toBe(500);
    });

    it("should return 409 if word already exists", async () => {
      db.createContent.mockRejectedValue(new Error("UNIQUE constraint failed"));
      const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);

      const formData = new FormData();
      formData.append("word", "test");
      formData.append("translation", "test");
      formData.append("cefrLevel", "A1");
      formData.append("topic", "test");

      const res = await app.request(
        "/api/admin/wordbank/add",
        { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: formData },
        MOCK_ADMIN_ENV
      );

      expect(res.status).toBe(409);
    });
  });

  describe("Admin Auth Controller", () => {
    it("POST /api/admin/auth/login should successfully log in an admin", async () => {
      db.findAdminByEmail.mockResolvedValue(mockAdmin);
      vi.mocked(verifyPassword).mockResolvedValue(true);

      const res = await app.request(
        "/api/admin/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: mockAdmin.email, password: "password123" }),
        },
        MOCK_ADMIN_ENV
      );

      expect(res.status).toBe(200);
      const body = await res.json();
      expect(body.token).toBeDefined();
    });

    it("POST /api/admin/auth/login should fail with invalid credentials (user not found)", async () => {
      db.findAdminByEmail.mockResolvedValue(null);
      const res = await app.request(
        "/api/admin/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: "wrong@test.com", password: "wrong" }),
        },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(401);
    });

    it("POST /api/admin/auth/login should fail if password verification fails", async () => {
      db.findAdminByEmail.mockResolvedValue(mockAdmin);
      vi.mocked(verifyPassword).mockResolvedValue(false);
      const res = await app.request(
        "/api/admin/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: mockAdmin.email, password: "wrong-password" }),
        },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(401);
    });

    it("POST /api/admin/auth/login should fail with missing email or password", async () => {
      const res = await app.request(
        "/api/admin/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: "admin@test.com" }),
        },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(400);
    });

    it("should return 500 on database error during login", async () => {
      db.findAdminByEmail.mockRejectedValue(new Error("DB Error"));
      const res = await app.request(
        "/api/admin/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: "admin@test.com", password: "password" }),
        },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(500);
    });
  });
});
