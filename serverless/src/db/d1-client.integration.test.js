import { describe, it, expect, beforeEach } from "vitest";
// 1. Import `env` from the special "cloudflare:test" module
import { env } from "cloudflare:test";
import { db } from "./d1-client";

// This describe block contains tests that run against a live, in-memory D1 database.
// The test runner (`@cloudflare/vitest-pool-workers`) automatically starts
// the worker and provides the bindings via `env`. We don't need to do it manually.
describe("D1 Client Integration Tests", () => {
  let d1;

  // 2. Before each test, get the D1 binding from the magical `env` object.
  //    Your global `tests/setup.js` has already applied the schema.
  beforeEach(async () => {
    d1 = env.DB;
    // Clear all data to ensure tests are isolated.
    await d1.batch([
      d1.prepare("DELETE FROM users"),
      d1.prepare("DELETE FROM admins"),
      d1.prepare("DELETE FROM one_time_tokens"),
      d1.prepare("DELETE FROM ielts_exam_results"),
      d1.prepare("DELETE FROM multilevel_exam_results"),
    ]);
  });

  // 3. Your tests now work perfectly, as `d1` is a real D1 binding.
  //    No other changes are needed here.

  it("should create a user and then retrieve it by ID", async () => {
    const userData = { email: "test@example.com", authProvider: "google", googleId: "google-123" };
    const createdUser = await db.createUser(d1, userData);
    expect(createdUser).toBeDefined();
    expect(createdUser.email).toBe(userData.email);
    const foundUser = await db.getUserById(d1, createdUser.id);
    expect(foundUser).toBeDefined();
    expect(foundUser.id).toBe(createdUser.id);
  });

  it("should update a user subscription tier", async () => {
    const userData = { authProvider: "telegram", telegramId: 12345 };
    const createdUser = await db.createUser(d1, userData);
    const expires = new Date();
    expires.setDate(expires.getDate() + 30);
    const subData = { tier: "gold", expiresAt: expires.toISOString() };
    await db.updateUserSubscription(d1, createdUser.id, subData);
    const updatedUser = await db.getUserById(d1, createdUser.id);
    expect(updatedUser.subscription_tier).toBe("gold");
    expect(updatedUser.subscription_expiresAt).toBe(subData.expiresAt);
  });

  it("should create and find a one-time token, then return null after it is deleted", async () => {
    const tokenData = {
      token: "test-token-123",
      telegramId: 12345,
      botMessageId: 101,
      userMessageId: 100,
    };
    await db.createOneTimeToken(d1, tokenData);
    const foundToken = await db.findOneTimeTokenAndDelete(d1, tokenData.token);
    expect(foundToken).toBeDefined();
    expect(foundToken.telegramId).toBe(tokenData.telegramId);
    const notFoundToken = await db.findOneTimeTokenAndDelete(d1, tokenData.token);
    expect(notFoundToken).toBeNull();
  });
});
