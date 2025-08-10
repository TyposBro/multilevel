// serverless/src/routes/examRoutes.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";
import * as gemini from "../utils/gemini";

// Mock the Gemini API and the DB client
vi.mock("../utils/gemini.js");
vi.mock("../db/d1-client.js");

describe("Multilevel Exam Routes", () => {
  const MOCK_ENV = {
    JWT_SECRET: "test-secret",
    DB: db,
    GEMINI_API_KEY: "fake-key", // Keep this for consistency, though gemini is mocked
    ENVIRONMENT: "production", // Test rate-limiting logic
  };

  beforeEach(() => {
    vi.clearAllMocks();
    // Setup default successful mocks that can be overridden by specific tests
    gemini.generateText.mockResolvedValue(
      JSON.stringify({ totalScore: 55, feedbackBreakdown: [] })
    );
    // The safeJsonParse mock needs to actually parse to avoid downstream errors
    gemini.safeJsonParse.mockImplementation((text) => (text ? JSON.parse(text) : null));
    db.updateUserUsage.mockResolvedValue({});
    db.updateUserSubscription.mockResolvedValue({});
    db.getRandomContent.mockResolvedValue([]); // Default to empty to prevent false positives
  });

  describe("GET /api/exam/multilevel/new", () => {
    it("should generate a new exam successfully", async () => {
      db.getUserById.mockResolvedValue({ id: "user-1", subscription_tier: "free" });
      db.getRandomContent.mockImplementation((d1, tableName) => {
        if (tableName === "content_part1_1") return Promise.resolve([{}, {}, {}]);
        return Promise.resolve([{}]);
      });
      const token = await generateToken({ env: MOCK_ENV }, "user-1");
      const res = await app.request(
        "/api/exam/multilevel/new",
        { headers: { Authorization: `Bearer ${token}` } },
        MOCK_ENV
      );
      expect(res.status).toBe(200);
    });
  });

  describe("POST /api/exam/multilevel/analyze", () => {
    it("should succeed for a paying user without checking limits", async () => {
      const mockGoldUser = {
        id: "gold-user-1",
        subscription_tier: "gold",
        subscription_expiresAt: new Date(Date.now() + 86400000).toISOString(),
      };
      db.getUserById.mockResolvedValue(mockGoldUser);
      const token = await generateToken({ env: MOCK_ENV }, mockGoldUser.id);

      const res = await app.request(
        "/api/exam/multilevel/analyze",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
          body: JSON.stringify({
            transcript: [{ speaker: "User", text: "Hello" }],
            practicePart: null,
          }),
        },
        MOCK_ENV
      );

      expect(res.status).toBe(201);
      expect(db.updateUserUsage).not.toHaveBeenCalled();
    });

    it("should return 403 if free user exceeds daily full exam limit", async () => {
      const freeUser = {
        id: "free-1",
        subscription_tier: "free",
        dailyUsage_fullExams_count: 1, // At the limit (1)
        dailyUsage_fullExams_lastReset: new Date().toISOString(),
      };
      db.getUserById.mockResolvedValue(freeUser);
      const token = await generateToken({ env: MOCK_ENV }, freeUser.id);
      const res = await app.request(
        "/api/exam/multilevel/analyze",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
          body: JSON.stringify({ transcript: [{}] }),
        },
        MOCK_ENV
      );
      expect(res.status).toBe(403);
    });

    it("should return 403 if free user exceeds daily part practice limit", async () => {
      const freeUser = {
        id: "free-2",
        subscription_tier: "free",
        dailyUsage_partPractices_count: 3, // At the limit (3)
        dailyUsage_partPractices_lastReset: new Date().toISOString(),
      };
      db.getUserById.mockResolvedValue(freeUser);
      const token = await generateToken({ env: MOCK_ENV }, freeUser.id);
      const res = await app.request(
        "/api/exam/multilevel/analyze",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
          body: JSON.stringify({ transcript: [{}], practicePart: "P1_1" }),
        },
        MOCK_ENV
      );
      expect(res.status).toBe(403);
    });

    it("should reset usage for a new day and succeed", async () => {
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);
      const freeUser = {
        id: "free-3",
        subscription_tier: "free",
        dailyUsage_fullExams_count: 5,
        dailyUsage_fullExams_lastReset: yesterday.toISOString(),
      };
      db.getUserById.mockResolvedValue(freeUser);
      const token = await generateToken({ env: MOCK_ENV }, freeUser.id);
      const res = await app.request(
        "/api/exam/multilevel/analyze",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
          body: JSON.stringify({ transcript: [{}] }),
        },
        MOCK_ENV
      );
      expect(res.status).toBe(201);
      expect(db.updateUserUsage).toHaveBeenCalledWith(
        expect.anything(),
        freeUser.id,
        expect.objectContaining({ fullExams: expect.objectContaining({ count: 1 }) })
      );
    });

    it("should return 500 if Gemini returns invalid JSON for single part analysis", async () => {
      const user = {
        id: "user-gemini-fail",
        subscription_tier: "gold",
      };
      db.getUserById.mockResolvedValue(user);
      gemini.safeJsonParse.mockReturnValue(null); // Simulate parsing failure
      const token = await generateToken({ env: MOCK_ENV }, user.id);
      const res = await app.request(
        "/api/exam/multilevel/analyze",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
          body: JSON.stringify({ transcript: [{}], practicePart: "P1_1" }),
        },
        MOCK_ENV
      );
      expect(res.status).toBe(500);
      expect(await res.json()).toEqual({
        message: "AI failed to generate a valid single-part analysis JSON.",
      });
    });

    it("should return 500 if Gemini returns invalid JSON for full exam analysis", async () => {
      const user = {
        id: "user-gemini-fail-2",
        subscription_tier: "gold",
      };
      db.getUserById.mockResolvedValue(user);
      gemini.safeJsonParse.mockReturnValue(null); // Simulate parsing failure
      const token = await generateToken({ env: MOCK_ENV }, user.id);
      const res = await app.request(
        "/api/exam/multilevel/analyze",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
          body: JSON.stringify({ transcript: [{}] }),
        },
        MOCK_ENV
      );
      expect(res.status).toBe(500);
      expect(await res.json()).toEqual({
        message: "AI failed to generate a valid full-exam analysis JSON.",
      });
    });

    // FIX: This test was flawed. It now correctly tests a non-DB failure for any user type.
    it("should return 500 if the AI call fails", async () => {
      const user = { id: "user-1", subscription_tier: "gold" };
      db.getUserById.mockResolvedValue(user);
      gemini.generateText.mockRejectedValue(new Error("AI service is down")); // Simulate AI failure
      const token = await generateToken({ env: MOCK_ENV }, user.id);

      const res = await app.request(
        "/api/exam/multilevel/analyze",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
          body: JSON.stringify({ transcript: [{}] }),
        },
        MOCK_ENV
      );

      expect(res.status).toBe(500);
      expect(await res.json()).toEqual({ message: "AI service is down" });
    });
  });
});
