// serverless/src/routes/adminRoutes.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import { sign } from "hono/jwt";
import app from "../index";
import { db } from "../db/d1-client";
import { uploadToCDN } from "../services/storageService";
import { verifyPassword } from "../utils/password";

// --- MOCK ALL MODULES AT THE TOP LEVEL ---
vi.mock("../db/d1-client.js");
vi.mock("../services/storageService.js");
vi.mock("../utils/password.js");

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

describe("Admin Routes (Controllers and Middleware)", () => {
  let token;

  beforeEach(async () => {
    vi.clearAllMocks();
    // Default happy path for middleware: a valid token finds a valid admin
    db.findAdminByEmail.mockResolvedValue(mockAdmin);
    db.createContent.mockResolvedValue({ id: "new-content-id" });
    uploadToCDN.mockResolvedValue("http://fake.url/file.ext");
    vi.mocked(verifyPassword).mockResolvedValue(true);
    token = await sign(
      { id: mockAdmin.id, email: mockAdmin.email },
      MOCK_ADMIN_ENV.JWT_SECRET_ADMIN
    );
  });

  // ===================================================================
  // Tests for adminMiddleware.js
  // We test the middleware by calling a protected route with bad data.
  // ===================================================================
  describe("Middleware: protectAdmin", () => {
    it("should return 401 if token has an invalid signature", async () => {
      const badToken = await sign({ id: "admin-1" }, "wrong-secret");
      const res = await app.request(
        "/api/admin/content/part1.1",
        { method: "POST", headers: { Authorization: `Bearer ${badToken}` } },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(401);
      expect(await res.json()).toEqual({ message: "Unauthorized" });
    });

    it("should return 401 if token payload is invalid (missing id)", async () => {
      const tokenWithBadPayload = await sign(
        { email: "admin@test.com" },
        MOCK_ADMIN_ENV.JWT_SECRET_ADMIN
      );
      const res = await app.request(
        "/api/admin/content/part1.1",
        { method: "POST", headers: { Authorization: `Bearer ${tokenWithBadPayload}` } },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(401);
      expect(await res.json()).toEqual({ message: "Token payload is invalid" });
    });

    it("should return 401 if admin from token is not found in DB", async () => {
      db.findAdminByEmail.mockResolvedValue(null);
      const res = await app.request(
        "/api/admin/content/part1.1",
        { method: "POST", headers: { Authorization: `Bearer ${token}` } },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(401);
      expect(await res.json()).toEqual({ message: "Admin not found" });
    });

    it("should return 500 if database lookup fails", async () => {
      db.findAdminByEmail.mockRejectedValue(new Error("DB Connection Error"));
      const res = await app.request(
        "/api/admin/content/part1.1",
        { method: "POST", headers: { Authorization: `Bearer ${token}` } },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(500);
      expect(await res.json()).toEqual({ message: "Server error during admin authorization" });
    });
  });

  // ===================================================================
  // Tests for adminAuthController.js
  // ===================================================================
  describe("Controller: adminAuthController", () => {
    it("POST /login - should succeed with valid credentials", async () => {
      const res = await app.request(
        "/api/admin/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: "admin@test.com", password: "pw" }),
        },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(200);
      expect(await res.json()).toHaveProperty("token");
    });

    it("POST /login - should fail with invalid password", async () => {
      vi.mocked(verifyPassword).mockResolvedValue(false);
      const res = await app.request(
        "/api/admin/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: "admin@test.com", password: "wrong-pw" }),
        },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(401);
    });

    it("POST /login - should fail if email is not found", async () => {
      db.findAdminByEmail.mockResolvedValue(null);
      const res = await app.request(
        "/api/admin/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: "notfound@test.com", password: "pw" }),
        },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(401);
    });
  });

  // ===================================================================
  // Tests for adminContentController.js
  // All these tests assume the middleware has passed.
  // ===================================================================
  describe("Controller: adminContentController", () => {
    it("POST /part1.1 - should succeed with valid data", async () => {
      const formData = new FormData();
      formData.append("questionText", "A question");
      formData.append("audio", new File(["audio"], "a.mp3"));
      const res = await app.request(
        "/api/admin/content/part1.1",
        { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: formData },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(201);
      expect(db.createContent).toHaveBeenCalled();
    });

    it("POST /part1.2 - should fail with missing files", async () => {
      const formData = new FormData();
      formData.append("question1", "q1");
      formData.append("image1", new File([], "f1.jpg"));
      const res = await app.request(
        "/api/admin/content/part1.2",
        { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: formData },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(400);
    });

    it("POST /part2 - should succeed with optional image", async () => {
      const formData = new FormData();
      formData.append("question1", "q1");
      formData.append("question2", "q2");
      formData.append("question3", "q3");
      formData.append("image", new File([], "img.jpg"));
      formData.append("audio", new File([], "audio.mp3"));
      const res = await app.request(
        "/api/admin/content/part2",
        { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: formData },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(201);
      expect(uploadToCDN).toHaveBeenCalledTimes(2);
    });

    it("POST /part2 - should fail with missing audio file", async () => {
      const formData = new FormData();
      formData.append("question1", "q1");
      formData.append("question2", "q2");
      formData.append("question3", "q3");
      const res = await app.request(
        "/api/admin/content/part2",
        { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: formData },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(400);
    });

    it("POST /part3 - should succeed with optional image", async () => {
      const formData = new FormData();
      formData.append("topic", "A topic");
      formData.append("forPoints", "Point 1\nPoint 2");
      formData.append("againstPoints", "Point A\nPoint B");
      formData.append("image", new File([], "img.jpg"));
      const res = await app.request(
        "/api/admin/content/part3",
        { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: formData },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(201);
      expect(uploadToCDN).toHaveBeenCalledTimes(1);
    });

    it("POST /wordbank/add - should succeed with valid data", async () => {
      const formData = new FormData();
      formData.append("word", "ephemeral");
      formData.append("translation", "efemer");
      formData.append("cefrLevel", "C1");
      formData.append("topic", "Advanced");
      const res = await app.request(
        "/api/admin/wordbank/add",
        { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: formData },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(201);
    });
  });
});
