// serverless/src/utils/generateToken.test.js
import { describe, it, expect } from "vitest";
import { generateToken } from "./generateToken";
import { verify } from "hono/jwt";

describe("Generate Token Utility", () => {
  it("should generate a valid user token with the correct ID", async () => {
    const userId = "user-abc-123";
    const mockContext = { env: { JWT_SECRET: "test-user-secret" } };
    const token = await generateToken(mockContext, userId, false);
    const payload = await verify(token, mockContext.env.JWT_SECRET, "HS256");
    expect(payload.id).toBe(userId);
    expect(payload.exp).toBeDefined();
  });

  it("should generate a valid admin token with the correct payload", async () => {
    const adminPayload = { id: "admin-xyz-789", email: "admin@test.com" };
    const mockContext = { env: { JWT_SECRET_ADMIN: "test-admin-secret" } };
    const token = await generateToken(mockContext, adminPayload, true);
    const payload = await verify(token, mockContext.env.JWT_SECRET_ADMIN, "HS256");
    expect(payload.id).toEqual(adminPayload.id);
    expect(payload.email).toEqual(adminPayload.email);
    expect(payload.exp).toBeDefined();
  });

  it("should throw an error for an invalid payload type", async () => {
    const mockContext = { env: { JWT_SECRET: "test-user-secret" } };
    // Pass null as payload
    await expect(generateToken(mockContext, null)).rejects.toThrow(
      "Invalid payload data provided to generateToken"
    );
  });

  it("should throw an error if the JWT secret is missing", async () => {
    const mockContext = { env: {} }; // No JWT_SECRET
    await expect(generateToken(mockContext, "user-id")).rejects.toThrow(
      "Server configuration error: Missing JWT secret."
    );
  });
});
