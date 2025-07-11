// serverless/src/utils/password.test.js
import { describe, it, expect } from "vitest";
import { hashPassword, verifyPassword } from "./password";

describe("Password Utility", () => {
  it("should correctly hash and verify a correct password", async () => {
    const password = "my-super-secret-password-123";
    const hashedPassword = await hashPassword(password);
    expect(typeof hashedPassword).toBe("string");
    expect(hashedPassword).toContain(":");
    const isCorrect = await verifyPassword(password, hashedPassword);
    expect(isCorrect).toBe(true);
  });

  it("should reject an incorrect password", async () => {
    const password = "my-super-secret-password-123";
    const hashedPassword = await hashPassword(password);
    const isIncorrect = await verifyPassword("this-is-wrong", hashedPassword);
    expect(isIncorrect).toBe(false);
  });

  it("should return false for a malformed stored hash", async () => {
    const isMalformed = await verifyPassword("any-password", "malformed-hash-no-colon");
    expect(isMalformed).toBe(false);
  });
});
