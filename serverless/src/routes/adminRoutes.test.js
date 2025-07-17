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
    it("POST /login - should fail if email is missing", async () => {
      const res = await app.request(
        "/api/admin/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ password: "pw" }),
        },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(400);
    });

    it("POST /login - should handle server error", async () => {
      db.findAdminByEmail.mockRejectedValue(new Error("DB Connection Failed"));
      const res = await app.request(
        "/api/admin/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: "admin@test.com", password: "pw" }),
        },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(500);
    });
  });

  // ===================================================================
  // Tests for adminContentController.js (Create)
  // ===================================================================
  describe("Controller: adminContentController (Create)", () => {
    it("POST /content/part1.1 - should succeed with valid data", async () => {
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

    it("POST /wordbank - should succeed with valid data", async () => {
      const formData = new FormData();
      formData.append("word", "ephemeral");
      formData.append("translation", "efemer");
      formData.append("cefrLevel", "C1");
      formData.append("topic", "Advanced");
      const res = await app.request(
        "/api/admin/wordbank", // Updated route
        { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: formData },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(201);
    });

    it("POST /wordbank - should return 409 for a duplicate word", async () => {
      db.createContent.mockRejectedValue(new Error("UNIQUE constraint failed"));
      const formData = new FormData();
      formData.append("word", "duplicate");
      formData.append("translation", "dup");
      formData.append("cefrLevel", "A1");
      formData.append("topic", "Test");
      const res = await app.request(
        "/api/admin/wordbank", // Updated route
        { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: formData },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(409);
    });
  });

  // ===================================================================
  // Tests for Admin Content CRUD (Read, Update, Delete)
  // ===================================================================
  describe("Admin Content CRUD (Read, Update, Delete)", () => {
    const mockContentItem = { id: "c1", questionText: "test question" };
    const mockContentList = [mockContentItem];

    it("GET /content/part1.1 - should list all items", async () => {
      db.listAllContent.mockResolvedValue(mockContentList);
      const res = await app.request(
        "/api/admin/content/part1.1",
        { headers: { Authorization: `Bearer ${token}` } },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(200);
      expect(await res.json()).toEqual(mockContentList);
      expect(db.listAllContent).toHaveBeenCalledWith(MOCK_ADMIN_ENV.DB, "content_part1_1");
    });

    it("GET /content/part1.1/:id - should get a single item", async () => {
      db.getContentById.mockResolvedValue(mockContentItem);
      const res = await app.request(
        `/api/admin/content/part1.1/${mockContentItem.id}`,
        { headers: { Authorization: `Bearer ${token}` } },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(200);
      expect(await res.json()).toEqual(mockContentItem);
      expect(db.getContentById).toHaveBeenCalledWith(
        MOCK_ADMIN_ENV.DB,
        "content_part1_1",
        mockContentItem.id
      );
    });

    it("GET /content/part1.1/:id - should return 404 if not found", async () => {
      db.getContentById.mockResolvedValue(null);
      const res = await app.request(
        "/api/admin/content/part1.1/not-found-id",
        { headers: { Authorization: `Bearer ${token}` } },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(404);
    });

    it("PUT /content/part1.1/:id - should update an item", async () => {
      const updateData = { questionText: "updated question" };
      const updatedItem = { ...mockContentItem, ...updateData };
      db.updateContent.mockResolvedValue(updatedItem);

      const res = await app.request(
        `/api/admin/content/part1.1/${mockContentItem.id}`,
        {
          method: "PUT",
          headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
          body: JSON.stringify(updateData),
        },
        MOCK_ADMIN_ENV
      );

      expect(res.status).toBe(200);
      expect(await res.json()).toEqual(updatedItem);
      expect(db.updateContent).toHaveBeenCalledWith(
        MOCK_ADMIN_ENV.DB,
        "content_part1_1",
        mockContentItem.id,
        updateData
      );
    });

    it("PUT /wordbank/:id - should return 409 on unique constraint violation", async () => {
      db.updateContent.mockRejectedValue(new Error("UNIQUE constraint failed"));
      const res = await app.request(
        "/api/admin/wordbank/some-id",
        {
          method: "PUT",
          headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
          body: JSON.stringify({ word: "existing-word" }),
        },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(409);
    });

    it("DELETE /content/part1.1/:id - should delete an item and return 204", async () => {
      db.deleteContent.mockResolvedValue({ success: true });
      const res = await app.request(
        `/api/admin/content/part1.1/${mockContentItem.id}`,
        { method: "DELETE", headers: { Authorization: `Bearer ${token}` } },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(204);
      expect(db.deleteContent).toHaveBeenCalledWith(
        MOCK_ADMIN_ENV.DB,
        "content_part1_1",
        mockContentItem.id
      );
    });

    it("GET /content/part1.1 - should return 500 on database error", async () => {
      db.listAllContent.mockRejectedValue(new Error("DB Error"));
      const res = await app.request(
        "/api/admin/content/part1.1",
        { headers: { Authorization: `Bearer ${token}` } },
        MOCK_ADMIN_ENV
      );
      expect(res.status).toBe(500);
    });
  });
});
