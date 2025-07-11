// serverless/src/db/d1-client.integration.test.js
import { describe, it, expect, beforeEach, beforeAll } from "vitest";
import { env } from "cloudflare:test";
import { db } from "./d1-client";

describe("D1 Client Integration Tests", () => {
  let d1;

  beforeAll(async () => {
    // --- THIS IS THE FIX ---
    // The `exec()` command doesn't handle multi-statement SQL files with comments well.
    // Instead, we split the schema into individual statements and run them in a batch.

    const schema = env.SCHEMA;
    // Split the schema by the semicolon at the end of each statement.
    // Filter out any empty strings that result from the split.
    const statements = schema.split(";").filter((query) => query.trim() !== "");

    // Prepare each statement and execute them in a single batch transaction.
    await env.DB.batch(statements.map((statement) => env.DB.prepare(statement)));
    // --- END OF FIX ---
  });

  beforeEach(async () => {
    d1 = env.DB;
    await d1.batch([
      d1.prepare("DELETE FROM users"),
      d1.prepare("DELETE FROM admins"),
      d1.prepare("DELETE FROM one_time_tokens"),
      d1.prepare("DELETE FROM ielts_exam_results"),
      d1.prepare("DELETE FROM multilevel_exam_results"),
    ]);
  });

  // No changes needed for the tests themselves.

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
