import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { unstable_dev } from "wrangler";
import { db } from "./d1-client";
import { readFileSync } from "fs";
import path from "path";

// This describe block contains tests that run against a live, in-memory D1 database.
// It is named with `.integration.test.js` to be picked up by the integration test runner.
describe("D1 Client Integration Tests", () => {
  let worker;
  let d1;

  // Before any tests in this file run, start a local dev worker.
  // This gives us a real D1 instance to test against.
  beforeAll(async () => {
    worker = await unstable_dev("src/index.js", {
      experimental: { disableExperimentalWarning: true },
    });
    // Get the D1 binding directly from the running worker's environment.
    d1 = worker.env.DB;

    // Read the master schema file and execute it to create all tables.
    const schema = readFileSync(path.resolve(__dirname, "./schema.sql"), "utf-8");
    await d1.exec(schema);
  });

  // After all tests in this file have run, stop the worker.
  afterAll(async () => {
    if (worker) {
      await worker.stop();
    }
  });

  // Before each individual test, clear all data to ensure tests are isolated.
  beforeEach(async () => {
    await d1.exec(`
      DELETE FROM users;
      DELETE FROM admins;
      DELETE FROM one_time_tokens;
      DELETE FROM ielts_exam_results;
      DELETE FROM multilevel_exam_results;
    `);
  });

  it("should create a user and then retrieve it by ID", async () => {
    const userData = { email: "test@example.com", authProvider: "google", googleId: "google-123" };

    // 1. Create the user using the db client
    const createdUser = await db.createUser(d1, userData);
    expect(createdUser).toBeDefined();
    expect(createdUser.email).toBe(userData.email);

    // 2. Retrieve the user to confirm it was saved correctly
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

    // The first time, it should be found and deleted
    const foundToken = await db.findOneTimeTokenAndDelete(d1, tokenData.token);
    expect(foundToken).toBeDefined();
    expect(foundToken.telegramId).toBe(tokenData.telegramId);

    // The second time, it should be null because it was deleted
    const notFoundToken = await db.findOneTimeTokenAndDelete(d1, tokenData.token);
    expect(notFoundToken).toBeNull();
  });
});
