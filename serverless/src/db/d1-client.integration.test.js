// serverless/src/db/d1-client.integration.test.js
import { describe, it, expect, beforeEach, beforeAll, vi } from "vitest";
import { env } from "cloudflare:test";
import { db } from "./d1-client";

// This is a mock D1 object that will always fail.
const mockFailingD1 = {
  prepare: () => ({
    bind: () => ({
      first: vi.fn().mockRejectedValue(new Error("D1 query failed")),
      run: vi.fn().mockRejectedValue(new Error("D1 exec failed")),
      all: vi.fn().mockRejectedValue(new Error("D1 all failed")),
    }),
  }),
  batch: vi.fn().mockRejectedValue(new Error("D1 batch failed")),
};

describe("D1 Client Integration Tests", () => {
  let d1;
  let testUser;

  beforeAll(async () => {
    const schema = env.SCHEMA;
    const statements = schema.split(";").filter((query) => query.trim() !== "");
    await env.DB.batch(statements.map((statement) => env.DB.prepare(statement)));
  });

  beforeEach(async () => {
    d1 = env.DB;
    const tables = [
      "users",
      "admins",
      "one_time_tokens",
      "ielts_exam_results",
      "multilevel_exam_results",
      "words",
      "content_part1_1",
      "content_part1_2",
      "content_part3",
    ];
    const deleteStmts = tables.map((table) => d1.prepare(`DELETE FROM ${table}`));
    await d1.batch(deleteStmts);

    testUser = await db.createUser(d1, {
      email: "test-user@example.com",
      authProvider: "google",
      googleId: "google-test-user-id",
    });
  });

  describe("User and Token Functions", () => {
    it("should create a user and find them by provider ID", async () => {
      const foundUser = await db.findUserByProviderId(d1, {
        provider: "google",
        id: "google-test-user-id",
      });
      expect(foundUser).toBeDefined();
      expect(foundUser.id).toBe(testUser.id);
    });

    it("should update a user subscription tier", async () => {
      const expires = new Date();
      expires.setDate(expires.getDate() + 30);
      const subData = { tier: "gold", expiresAt: expires.toISOString() };
      await db.updateUserSubscription(d1, testUser.id, subData);
      const updatedUser = await db.getUserById(d1, testUser.id);
      expect(updatedUser.subscription_tier).toBe("gold");
    });

    it("should update user usage stats", async () => {
      const usageData = {
        fullExams: { count: 1, lastReset: new Date().toISOString() },
        partPractices: { count: 2, lastReset: new Date().toISOString() },
      };
      await db.updateUserUsage(d1, testUser.id, usageData);
      const updatedUser = await db.getUserById(d1, testUser.id);
      expect(updatedUser.dailyUsage_fullExams_count).toBe(1);
      expect(updatedUser.dailyUsage_partPractices_count).toBe(2);
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

    it("should delete a user", async () => {
      await db.deleteUser(d1, testUser.id);
      const foundUser = await db.getUserById(d1, testUser.id);
      expect(foundUser).toBeNull();
    });
  });

  describe("IELTS Exam Functions", () => {
    it("should create, retrieve history, and get details for an IELTS exam result", async () => {
      const resultData = {
        userId: testUser.id,
        overallBand: 7.5,
        criteria: [{ name: "Fluency" }],
        transcript: [{ text: "hello" }],
      };
      const createdResult = await db.createIeltsExamResult(d1, resultData);
      expect(createdResult.id).toBeDefined();
      const history = await db.getIeltsExamHistory(d1, testUser.id);
      expect(history).toHaveLength(1);
      const details = await db.getIeltsExamResultDetails(d1, createdResult.id, testUser.id);
      expect(details.criteria).toEqual([{ name: "Fluency" }]);
    });
  });

  describe("Multilevel Exam Functions", () => {
    it("should create, retrieve history, and get details for a Multilevel exam result", async () => {
      const resultData = {
        userId: testUser.id,
        totalScore: 60,
        feedbackBreakdown: [{ part: "P1" }],
        transcript: [{ text: "hi" }],
        examContent: { p1_id: 1 },
        practicedPart: "FULL",
      };
      const createdResult = await db.createMultilevelExamResult(d1, resultData);
      expect(createdResult.id).toBeDefined();
      const history = await db.getMultilevelExamHistory(d1, testUser.id);
      expect(history).toHaveLength(1);
      const details = await db.getMultilevelExamResultDetails(d1, createdResult.id, testUser.id);
      expect(details.examContent).toEqual({ p1_id: 1 });
    });
  });

  describe("Content Functions", () => {
    it("should create and get random content", async () => {
      const contentData = {
        questionText: "What is your name?",
        audioUrl: "http://example.com/audio.mp3",
        tags: JSON.stringify(["test"]),
      };
      const createdContent = await db.createContent(d1, "content_part1_1", contentData);
      expect(createdContent.questionText).toBe(contentData.questionText);
      const randomContent = await db.getRandomContent(d1, "content_part1_1", 1);
      expect(randomContent).toHaveLength(1);
      expect(randomContent[0].id).toBe(createdContent.id);
    });
  });

  describe("Word Bank Functions", () => {
    beforeEach(async () => {
      await d1.batch([
        d1.prepare(
          "INSERT INTO words (id, word, cefrLevel, topic, translation) VALUES ('w1', 'apple', 'A1', 'Food', 'olma')"
        ),
        d1.prepare(
          "INSERT INTO words (id, word, cefrLevel, topic, translation) VALUES ('w2', 'computer', 'B2', 'Technology', 'kompyuter')"
        ),
        d1.prepare(
          "INSERT INTO words (id, word, cefrLevel, topic, translation) VALUES ('w3', 'server', 'B2', 'Technology', 'server')"
        ),
      ]);
    });

    it("should get all distinct levels", async () => {
      const levels = await db.getWordBankLevels(d1);
      expect(levels).toEqual(["A1", "B2"]);
    });

    it("should get all distinct topics for a given level", async () => {
      const topics = await db.getWordBankTopics(d1, "B2");
      expect(topics).toEqual(["Technology"]);
    });

    it("should get all words for a given level and topic", async () => {
      const words = await db.getWordBankWords(d1, "B2", "Technology");
      expect(words).toHaveLength(2);
    });
  });

  // --- New tests for 100% coverage ---
  describe("Error Handling and Edge Cases", () => {
    it("should return null for unsupported provider in findUserByProviderId", async () => {
      const result = await db.findUserByProviderId(d1, { provider: "facebook", id: "123" });
      expect(result).toBeNull();
    });

    it("should handle history retention date in getMultilevelExamHistory", async () => {
      const sixDaysAgo = new Date();
      sixDaysAgo.setDate(sixDaysAgo.getDate() - 6);
      const history = await db.getMultilevelExamHistory(d1, testUser.id, sixDaysAgo.toISOString());
      expect(history).toEqual([]);
    });

    it("should return null for an expired token in findOneTimeTokenAndDelete", async () => {
      const token = "expired-token";

      // --- START OF FIX ---
      // Instead of calculating the time in JS, let's use SQL to insert a timestamp
      // that is definitively 6 minutes in the past. This is more robust.
      // The `strftime` function is SQLite's way of doing date/time manipulation.
      await d1
        .prepare(
          "INSERT INTO one_time_tokens (token, telegramId, botMessageId, userMessageId, createdAt) VALUES (?, 1, 1, 1, strftime('%Y-%m-%d %H:%M:%S', 'now', '-6 minutes'))"
        )
        .bind(token)
        .run();
      // --- END OF FIX ---

      const result = await db.findOneTimeTokenAndDelete(d1, token);
      expect(result).toBeNull();
    });

    it("should correctly parse JSON in getRandomContent for Part 3", async () => {
      // Seed Part 3 content
      await db.createContent(d1, "content_part3", {
        topic: "Test Topic",
        forPoints: JSON.stringify(["a", "b"]),
        againstPoints: JSON.stringify(["c", "d"]),
        tags: "[]",
      });
      const content = await db.getRandomContent(d1, "content_part3", 1);
      expect(content).toHaveLength(1);
      expect(content[0].forPoints).toEqual(["a", "b"]);
      expect(content[0].againstPoints).toEqual(["c", "d"]);
    });

    // Test for a D1 error to cover a catch block. We'll use a mock to simulate this.
    it("should return null from getUserById on D1 error", async () => {
      const mockBadD1 = {
        prepare: () => ({
          bind: () => ({
            first: vi.fn().mockRejectedValue(new Error("D1 unavailable")),
          }),
        }),
      };
      const result = await db.getUserById(mockBadD1, "any-id");
      expect(result).toBeNull();
    });

    it("should cover all error catch blocks", async () => {
      // Test catch blocks by passing the mock failing D1 instance
      await expect(db.getUserById(mockFailingD1, "1")).resolves.toBeNull();
      await expect(
        db.findUserByProviderId(mockFailingD1, { provider: "google", id: "1" })
      ).resolves.toBeNull();
      await expect(db.createUser(mockFailingD1, {})).rejects.toThrow();
      await expect(db.updateUserSubscription(mockFailingD1, "1", {})).rejects.toThrow();
      await expect(db.deleteUser(mockFailingD1, "1")).rejects.toThrow();
      await expect(db.findAdminByEmail(mockFailingD1, "e")).resolves.toBeNull();
      await expect(db.createMultilevelExamResult(mockFailingD1, {})).rejects.toThrow();
      await expect(db.getMultilevelExamHistory(mockFailingD1, "1")).resolves.toEqual([]);
      await expect(db.getMultilevelExamResultDetails(mockFailingD1, "1", "1")).resolves.toBeNull();
      await expect(db.createOneTimeToken(mockFailingD1, {})).rejects.toThrow();
      await expect(db.findOneTimeTokenAndDelete(mockFailingD1, "t")).resolves.toBeNull();
      await expect(db.getWordBankLevels(mockFailingD1)).resolves.toEqual([]);
      await expect(db.getWordBankTopics(mockFailingD1, "l")).resolves.toEqual([]);
      await expect(db.getWordBankWords(mockFailingD1, "l", "t")).resolves.toEqual([]);
      await expect(db.getRandomContent(mockFailingD1, "t", 1)).resolves.toEqual([]);
      await expect(db.createContent(mockFailingD1, "t", {})).rejects.toThrow();
      await expect(
        db.updateUserUsage(mockFailingD1, "1", { fullExams: {}, partPractices: {} })
      ).rejects.toThrow();
      await expect(db.createIeltsExamResult(mockFailingD1, {})).rejects.toThrow();
      await expect(db.getIeltsExamHistory(mockFailingD1, "1")).resolves.toEqual([]);
      await expect(db.getIeltsExamResultDetails(mockFailingD1, "1", "1")).resolves.toBeNull();
    });
  });
});
