import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";

vi.mock("../db/d1-client.js");

describe("Auth Routes & Middleware", () => {
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
});
