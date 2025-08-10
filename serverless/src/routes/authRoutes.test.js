// serverless/src/routes/authRoutes.test.js
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import app from "../index";
import { db } from "../db/d1-client";
import { generateToken } from "../utils/generateToken";
import { getUserProfile } from "../controllers/authController";

vi.mock("../db/d1-client.js");

describe("Auth Routes", () => {
  const MOCK_ENV = {
    JWT_SECRET: "a-secure-test-secret-for-users",
    TELEGRAM_BOT_TOKEN: "fake-token",
    SERVER_URL: "https://test.com",
    FRONTEND_ACCOUNT_URL: "https://example.com/account.html",
    DB: db,
  };
  const mockUser = { id: "user-123", email: "test@example.com", firstName: "Test" };
  const originalFetch = global.fetch;

  beforeEach(() => {
    vi.clearAllMocks();
    db.getUserById.mockResolvedValue(mockUser);
    db.createUser.mockResolvedValue(mockUser);
    db.findUserByProviderId.mockResolvedValue(mockUser);
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve({ sub: "google-id", email: "google@test.com", name: "Google User" }),
    });
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  describe("Protected Routes", () => {
    it("GET /api/auth/profile should succeed", async () => {
      const token = await generateToken({ env: MOCK_ENV }, mockUser.id);
      const res = await app.request(
        "/api/auth/profile",
        { headers: { Authorization: `Bearer ${token}` } },
        MOCK_ENV
      );
      expect(res.status).toBe(200);
      expect(await res.json()).toHaveProperty("_id", mockUser.id);
    });

    it("DELETE /api/auth/profile should succeed", async () => {
      db.deleteUser.mockResolvedValue(undefined);
      const token = await generateToken({ env: MOCK_ENV }, mockUser.id);
      const res = await app.request(
        "/api/auth/profile",
        { method: "DELETE", headers: { Authorization: `Bearer ${token}` } },
        MOCK_ENV
      );
      expect(res.status).toBe(200);
    });
  });

  describe("Public Routes", () => {
    it("POST /google-signin should fail if idToken is missing", async () => {
      const res = await app.request(
        "/api/auth/google-signin",
        {
          method: "POST",
          body: JSON.stringify({}),
          headers: { "Content-Type": "application/json" },
        },
        MOCK_ENV
      );
      expect(res.status).toBe(400);
    });

    it("POST /google-signin should fail if Google API verification fails", async () => {
      global.fetch.mockResolvedValue({ ok: false });
      const res = await app.request(
        "/api/auth/google-signin",
        {
          method: "POST",
          body: JSON.stringify({ idToken: "bad-token" }),
          headers: { "Content-Type": "application/json" },
        },
        MOCK_ENV
      );
      expect(res.status).toBe(401);
    });

    it("POST /google-signin should handle server errors", async () => {
      db.findUserByProviderId.mockRejectedValue(new Error("DB Error"));
      const res = await app.request(
        "/api/auth/google-signin",
        {
          method: "POST",
          body: JSON.stringify({ idToken: "good-token" }),
          headers: { "Content-Type": "application/json" },
        },
        MOCK_ENV
      );
      expect(res.status).toBe(401); // Throws and gets caught as a sign-in failure
    });

    it("POST /verify-telegram-token should fail if token is missing", async () => {
      const res = await app.request(
        "/api/auth/verify-telegram-token",
        {
          method: "POST",
          body: JSON.stringify({}),
          headers: { "Content-Type": "application/json" },
        },
        MOCK_ENV
      );
      expect(res.status).toBe(400);
    });

    it("POST /verify-telegram-token should fail if token is invalid", async () => {
      db.findOneTimeTokenAndDelete.mockResolvedValue(null);
      const res = await app.request(
        "/api/auth/verify-telegram-token",
        {
          method: "POST",
          body: JSON.stringify({ oneTimeToken: "invalid" }),
          headers: { "Content-Type": "application/json" },
        },
        MOCK_ENV
      );
      expect(res.status).toBe(401);
    });

    it("POST /verify-telegram-token should handle server errors", async () => {
      db.findOneTimeTokenAndDelete.mockRejectedValue(new Error("DB Error"));
      const res = await app.request(
        "/api/auth/verify-telegram-token",
        {
          method: "POST",
          body: JSON.stringify({ oneTimeToken: "any-token" }),
          headers: { "Content-Type": "application/json" },
        },
        MOCK_ENV
      );
      expect(res.status).toBe(500);
    });

    it("GET /telegram/redirect should return redirect HTML with a token", async () => {
      const res = await app.request("/api/auth/telegram/redirect?token=test-token", {}, MOCK_ENV);
      expect(res.status).toBe(200);
      const text = await res.text();
      // FIX: The redirect is now performed with `window.location.href`.
      expect(text).toContain('window.location.href = "multilevelapp://login?token=test-token"');
    });

    it("GET /telegram/redirect should return error HTML without a token", async () => {
      const res = await app.request("/api/auth/telegram/redirect", {}, MOCK_ENV);
      expect(res.status).toBe(400);
      const text = await res.text();
      expect(text).toContain("Error: Missing login token");
    });
  });

  describe("Controller Unit Test", () => {
    it("getUserProfile should return 404 if user is not in context", async () => {
      const mockContext = {
        get: vi.fn().mockReturnValue(null),
        json: vi.fn(),
      };
      await getUserProfile(mockContext);
      expect(mockContext.json).toHaveBeenCalledWith({ message: "User not found in context" }, 404);
    });
  });
});
