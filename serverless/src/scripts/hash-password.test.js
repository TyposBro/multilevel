import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { hashPassword } from "./hash-password.js";

describe("hash-password.js Script", () => {
  it("should generate a valid hash string in 'salt:hash' format", async () => {
    const password = "my-test-password";
    const hashedPassword = await hashPassword(password);

    expect(typeof hashedPassword).toBe("string");
    expect(hashedPassword).toContain(":");
    const [salt, hash] = hashedPassword.split(":");
    expect(salt).toHaveLength(32); // 16 bytes * 2 hex chars/byte
    expect(hash).toHaveLength(64); // 32 bytes * 2 hex chars/byte
  });
});
