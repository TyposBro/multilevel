// serverless/src/db/d1-client.integration.test.js
import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { unstable_dev, getMiniflareBindings } from "wrangler";
import { db } from "./d1-client";
import { readFileSync } from "fs";
import path from "path";

describe("D1 Client Integration Tests", () => {
  let worker;
  let d1; // This will hold our D1 binding

  beforeAll(async () => {
    // Start a dev worker, which will provide a real D1 instance.
    worker = await unstable_dev("src/index.js", {
      experimental: { disableExperimentalWarning: true },
    });

    // --- START OF FIX ---
    // Use getMiniflareBindings to reliably get the D1 binding.
    const bindings = await getMiniflareBindings();
    d1 = bindings.DB;
    // --- END OF FIX ---

    // Ensure the binding was found before proceeding
    if (!d1) {
      throw new Error("Could not get D1 binding from Miniflare.");
    }

    // Apply the schema to the (now confirmed to exist) D1 instance.
    const schema = readFileSync(path.resolve(__dirname, "./schema.sql"), "utf-8");
    await d1.exec(schema);
  });

  afterAll(async () => {
    if (worker) {
      await worker.stop();
    }
  });

  // Before each test, clear all data to ensure tests are isolated.
  beforeEach(async () => {
    // Use batching for efficiency
    await d1.batch([
      d1.prepare("DELETE FROM users"),
      d1.prepare("DELETE FROM admins"),
      d1.prepare("DELETE FROM one_time_tokens"),
      d1.prepare("DELETE FROM ielts_exam_results"),
      d1.prepare("DELETE FROM multilevel_exam_results"),
    ]);
  });

  // --- Your tests below this line are correct and do not need changes ---
  // They will now run because `d1` is correctly initialized.

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
