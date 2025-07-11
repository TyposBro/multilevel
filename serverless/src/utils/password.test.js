// serverless/src/utils/password.test.js
import { describe, it, expect } from "vitest";
import { hashPassword, verifyPassword } from "./password";

describe("Password Utility", () => {
  it("should correctly hash and verify a correct password", async () => {
    const password = "my-super-secret-password-123";

    // 1. Hash the password
    const hashedPassword = await hashPassword(password);

    // 2. Assert that the hash is a string containing the salt separator
    expect(typeof hashedPassword).toBe("string");
    expect(hashedPassword).toContain(":");

    // 3. Verify that the correct password works
    const isCorrect = await verifyPassword(password, hashedPassword);
    expect(isCorrect).toBe(true);
  });

  it("should reject an incorrect password", async () => {
    const password = "my-super-secret-password-123";
    const hashedPassword = await hashPassword(password);

    // Verify that an incorrect password fails
    const isIncorrect = await verifyPassword("this-is-wrong", hashedPassword);
    expect(isIncorrect).toBe(false);
  });
});
