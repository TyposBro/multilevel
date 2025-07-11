// serverless/src/utils/password.test.js
import { describe, it, expect, beforeEach, afterEach } from "vitest";
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

describe("Password Utility (without crypto.subtle.timingSafeEqual)", () => {
  const originalTimingSafeEqual = crypto.subtle.timingSafeEqual;

  beforeEach(() => {
    // Mock the function to be undefined for this suite of tests
    crypto.subtle.timingSafeEqual = undefined;
  });

  afterEach(() => {
    // Restore the original function after tests in this suite run
    crypto.subtle.timingSafeEqual = originalTimingSafeEqual;
  });

  it("should correctly verify a password using the manual fallback", async () => {
    const password = "password-for-fallback";
    const hashedPassword = await hashPassword(password);
    const isCorrect = await verifyPassword(password, hashedPassword);
    expect(isCorrect).toBe(true);
  });

  it("should reject an incorrect password using the manual fallback", async () => {
    const password = "password-for-fallback";
    const hashedPassword = await hashPassword(password);
    const isIncorrect = await verifyPassword("wrong-password", hashedPassword);
    expect(isIncorrect).toBe(false);
  });

  it("should return false if verifyPassword catches an error", async () => {
    // Pass a hash that will cause an error during hex parsing
    const willError = await verifyPassword("any-password", "salt:not-valid-hex");
    expect(willError).toBe(false);
  });
});
