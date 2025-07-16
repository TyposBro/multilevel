// serverless/src/routes/authRoutes.middleware.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import { sign } from "hono/jwt";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";

vi.mock("../db/d1-client.js");

describe("Auth Middleware (protectAndLoadUser)", () => {
  const MOCK_ENV = {
    JWT_SECRET: "a-secure-test-secret-for-users",
    DB: db,
  };
  const mockUser = { id: "user-123", email: "test@example.com" };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("GET /api/auth/profile should succeed for a valid token", async () => {
    const token = await generateToken({ env: MOCK_ENV }, mockUser.id);
    db.getUserById.mockResolvedValue(mockUser);

    const res = await app.request(
      "/api/auth/profile",
      { headers: { Authorization: `Bearer ${token}` } },
      MOCK_ENV
    );

    expect(res.status).toBe(200);
  });

  it("should return 401 if Authorization header is missing", async () => {
    const res = await app.request("/api/auth/profile", {}, MOCK_ENV);
    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({
      message: "Authorization header is missing or malformed",
    });
  });

  it("should return 401 for a malformed Authorization header (not starting with 'Bearer ')", async () => {
    const res = await app.request(
      "/api/auth/profile",
      { headers: { Authorization: "Basic some-other-token" } },
      MOCK_ENV
    );
    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({
      message: "Authorization header is missing or malformed",
    });
  });

  it("should return 500 if JWT_SECRET is not configured", async () => {
    const token = await generateToken({ env: MOCK_ENV }, mockUser.id);
    const envWithoutSecret = { DB: db }; // JWT_SECRET is missing

    const res = await app.request(
      "/api/auth/profile",
      { headers: { Authorization: `Bearer ${token}` } },
      envWithoutSecret
    );

    expect(res.status).toBe(500);
    expect(await res.json()).toEqual({ message: "Server configuration error" });
  });

  it("should return 401 for an invalid or expired token", async () => {
    const res = await app.request(
      "/api/auth/profile",
      { headers: { Authorization: "Bearer a-bad-token-that-will-not-verify" } },
      MOCK_ENV
    );
    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.message).toContain("Token is invalid or expired");
    expect(body.error).toBeDefined();
  });

  it("should return 401 if token payload is invalid (missing id)", async () => {
    const badPayload = { user: "some-user" }; // No 'id' field
    const token = await sign(badPayload, MOCK_ENV.JWT_SECRET, "HS256");

    const res = await app.request(
      "/api/auth/profile",
      { headers: { Authorization: `Bearer ${token}` } },
      MOCK_ENV
    );

    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({ message: "Invalid token payload" });
  });

  it("should return 401 if user from token is not found in database", async () => {
    const token = await generateToken({ env: MOCK_ENV }, mockUser.id);
    db.getUserById.mockResolvedValue(null); // Simulate user deleted after token issued

    const res = await app.request(
      "/api/auth/profile",
      { headers: { Authorization: `Bearer ${token}` } },
      MOCK_ENV
    );

    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({ message: "User not found" });
  });

  it("DELETE /api/auth/profile should handle server errors during deletion", async () => {
    db.getUserById.mockResolvedValue(mockUser);
    db.deleteUser.mockRejectedValue(new Error("DB Error"));
    const token = await generateToken({ env: MOCK_ENV }, mockUser.id);

    const res = await app.request(
      "/api/auth/profile",
      { method: "DELETE", headers: { Authorization: `Bearer ${token}` } },
      MOCK_ENV
    );

    expect(res.status).toBe(500);
  });
});
