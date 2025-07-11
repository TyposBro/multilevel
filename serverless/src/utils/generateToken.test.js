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

    // --- START OF FINAL FIX ---
    // Let's log the values to be certain
    console.log("Decoded payload.id:", payload.id, `(Type: ${typeof payload.id})`);
    console.log("Original adminPayload.id:", adminPayload.id, `(Type: ${typeof adminPayload.id})`);

    // Use toEqual for a robust comparison and add a custom message for clarity if it fails
    expect(
      payload.id,
      "The ID from the decoded token should match the original payload ID"
    ).toEqual(adminPayload.id);
    expect(
      payload.email,
      "The email from the decoded token should match the original payload email"
    ).toEqual(adminPayload.email);
    expect(payload.exp, "The token should have an expiration time").toBeDefined();
    // --- END OF FINAL FIX ---
  });
});
