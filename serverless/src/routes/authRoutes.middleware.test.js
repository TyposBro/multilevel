// serverless/src/routes/authRoutes.middleware.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";

vi.mock("../db/d1-client.js");

describe("Auth Middleware Protected Routes", () => {
  const MOCK_ENV = {
    JWT_SECRET: "a-secure-test-secret-for-users",
    DB: db,
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
      { headers: { Authorization: `Bearer ${token}` } },
      MOCK_ENV
    );
    expect(res.status).toBe(200);
  });

  it("DELETE /api/auth/profile should delete the user", async () => {
    const mockUser = { id: "user-to-delete-789", email: "delete@me.com" };
    db.getUserById.mockResolvedValue(mockUser);
    db.deleteUser.mockResolvedValue(undefined);
    const token = await generateToken({ env: MOCK_ENV }, mockUser.id);
    const res = await app.request(
      "/api/auth/profile",
      { method: "DELETE", headers: { Authorization: `Bearer ${token}` } },
      MOCK_ENV
    );
    expect(res.status).toBe(200);
  });

  it("deleteUserProfile should handle server errors", async () => {
    const mockUser = { id: "user-to-delete-789" };
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
