// serverless/src/routes/authRoutes.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";

vi.mock("../db/d1-client.js");

describe("Auth Routes & Middleware", () => {
  const MOCK_ENV = {
    JWT_SECRET: "a-secure-test-secret-for-users",
    DB: db,
    TELEGRAM_BOT_TOKEN: "fake-token",
    SERVER_URL: "http://localhost",
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("GET /api/auth/profile should succeed and return user data for a valid token", async () => {
    const mockUser = { id: "user-123", email: "test@example.com" };
    const token = await generateToken({ env: MOCK_ENV }, mockUser.id);

    db.getUserById.mockResolvedValue(mockUser);

    const res = await app.request(
      "/api/auth/profile",
      {
        headers: { Authorization: `Bearer ${token}` },
      },
      MOCK_ENV
    );

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body._id).toBe(mockUser.id);

    // --- THIS IS THE FIX ---
    // Assert that the function was called with the mocked DB object and the correct ID.
    expect(db.getUserById).toHaveBeenCalledWith(MOCK_ENV.DB, mockUser.id);
    // --- END OF FIX ---
  });

  // The rest of your tests in this file are correct.
  it("GET /api/auth/profile should fail with 401 if no token is provided", async () => {
    const res = await app.request("/api/auth/profile", {}, MOCK_ENV);
    expect(res.status).toBe(401);
  });

  it("GET /api/auth/profile should fail with 401 if token is valid but user does not exist", async () => {
    const mockUser = { id: "user-that-was-deleted" };
    const token = await generateToken({ env: MOCK_ENV }, mockUser.id);
    db.getUserById.mockResolvedValue(null);
    const res = await app.request(
      "/api/auth/profile",
      {
        headers: { Authorization: `Bearer ${token}` },
      },
      MOCK_ENV
    );
    expect(res.status).toBe(401);
  });

  it("DELETE /api/auth/profile should delete the user", async () => {
    // Arrange
    const mockUser = { id: "user-to-delete-789", email: "delete@me.com" };
    db.getUserById.mockResolvedValue(mockUser);
    db.deleteUser.mockResolvedValue(undefined); // deleteUser doesn't return anything
    const token = await generateToken({ env: MOCK_ENV }, mockUser.id);

    // Act
    const res = await app.request(
      "/api/auth/profile",
      {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
      },
      MOCK_ENV
    );

    // Assert
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.message).toContain("deleted successfully");

    expect(db.deleteUser).toHaveBeenCalledOnce();
    expect(db.deleteUser).toHaveBeenCalledWith(MOCK_ENV.DB, mockUser.id);
  });

  it("should return 500 if JWT_SECRET is not configured", async () => {
    // Arrange: Create a mock environment WITHOUT the JWT_SECRET
    const MOCK_BAD_ENV = { DB: db };
    const token = "any-token";

    // Act
    const res = await app.request(
      "/api/auth/profile",
      {
        headers: { Authorization: `Bearer ${token}` },
      },
      MOCK_BAD_ENV // Use the bad environment
    );

    // Assert
    expect(res.status).toBe(500);
    const body = await res.json();
    expect(body.message).toBe("Server configuration error");
  });

  it("googleSignIn should fail if idToken is missing", async () => {
    const res = await app.request(
      "/api/auth/google-signin",
      { method: "POST", body: JSON.stringify({}), headers: { "Content-Type": "application/json" } },
      MOCK_ENV
    );
    expect(res.status).toBe(400);
  });

  it("googleSignIn should handle token verification failure", async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: false }); // Mock Google API failure
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

  it("verifyTelegramToken should fail with missing token", async () => {
    const res = await app.request(
      "/api/auth/verify-telegram-token",
      { method: "POST", body: JSON.stringify({}), headers: { "Content-Type": "application/json" } },
      MOCK_ENV
    );
    expect(res.status).toBe(400);
  });

  it("verifyTelegramToken should fail with invalid one-time token", async () => {
    db.findOneTimeTokenAndDelete.mockResolvedValue(null);
    const res = await app.request(
      "/api/auth/verify-telegram-token",
      {
        method: "POST",
        body: JSON.stringify({ oneTimeToken: "bad-token" }),
        headers: { "Content-Type": "application/json" },
      },
      MOCK_ENV
    );
    expect(res.status).toBe(401);
  });

  it("telegramRedirect should return error if token is missing", async () => {
    const res = await app.request("/api/auth/telegram/redirect", {}, MOCK_ENV);
    expect(res.status).toBe(400);
  });
});
