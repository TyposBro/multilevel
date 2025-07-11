// serverless/src/routes/multilevelExamRoutes.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";
// --- 1. IMPORT THE GEMINI MODULE ---
import * as gemini from "../utils/gemini";

// Mock the Gemini API and the DB client
vi.mock("../utils/gemini.js");
vi.mock("../db/d1-client.js");

describe("Multilevel Exam Routes", () => {
  const MOCK_ENV = {
    JWT_SECRET: "test-secret",
    DB: db,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    // A default successful mock for tests that need it
    gemini.generateText.mockResolvedValue(
      JSON.stringify({ totalScore: 55, feedbackBreakdown: [] })
    );
    gemini.safeJsonParse.mockImplementation((text) => (text ? JSON.parse(text) : null));
  });

  it("POST /api/exam/multilevel/analyze should return 403 if free user exceeds daily full exam limit", async () => {
    const mockFreeUser = {
      id: "free-user-1",
      subscription_tier: "free",
      dailyUsage_fullExams_count: 1,
      dailyUsage_fullExams_lastReset: new Date().toISOString(),
    };
    db.getUserById.mockResolvedValue(mockFreeUser);
    const token = await generateToken({ env: MOCK_ENV }, mockFreeUser.id);
    const res = await app.request(
      "/api/exam/multilevel/analyze",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          transcript: [{ speaker: "User", text: "hello" }],
          practicedPart: null,
        }),
      },
      MOCK_ENV
    );
    expect(res.status).toBe(403);
  });

  it("POST /api/exam/multilevel/analyze should succeed for a paying user", async () => {
    const mockGoldUser = { id: "gold-user-1", subscription_tier: "gold" };
    db.getUserById.mockResolvedValue(mockGoldUser);
    db.createMultilevelExamResult.mockResolvedValue({ id: "result-xyz" });
    const token = await generateToken({ env: MOCK_ENV }, mockGoldUser.id);
    const res = await app.request(
      "/api/exam/multilevel/analyze",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          transcript: [{ speaker: "User", text: "hello" }],
          practicedPart: "FULL",
        }),
      },
      MOCK_ENV
    );
    expect(res.status).toBe(201);
  });

  // --- START OF FIXES ---
  it("generateNewExam should return 500 if not enough content in DB", async () => {
    db.getRandomContent.mockResolvedValue([]);
    const token = await generateToken({ env: MOCK_ENV }, { id: "user" });
    const res = await app.request(
      "/api/exam/multilevel/new",
      { headers: { Authorization: `Bearer ${token}` } },
      MOCK_ENV
    );
    expect(res.status).toBe(500);
  });

  it("analyzeExam should handle single part practice", async () => {
    // 2. Configure the mock for this specific test case
    const mockSinglePartResponse = { part: "Part 1.1", score: 10, feedback: "Good." };
    gemini.generateText.mockResolvedValue(JSON.stringify(mockSinglePartResponse));
    gemini.safeJsonParse.mockReturnValue(mockSinglePartResponse);

    const mockGoldUser = { id: "gold-user-1", subscription_tier: "gold" };
    db.getUserById.mockResolvedValue(mockGoldUser);
    db.createMultilevelExamResult.mockResolvedValue({ id: "result-xyz" });
    const token = await generateToken({ env: MOCK_ENV }, mockGoldUser.id);

    const res = await app.request(
      "/api/exam/multilevel/analyze",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          transcript: [{ speaker: "User", text: "hello" }],
          practicePart: "P1_1",
        }),
      },
      MOCK_ENV
    );

    expect(res.status).toBe(201);
  });

  it("analyzeExam should return 500 on Gemini error for single part", async () => {
    const mockGoldUser = { id: "gold-user-1", subscription_tier: "gold" };
    db.getUserById.mockResolvedValue(mockGoldUser);
    gemini.generateText.mockResolvedValue("bad json");
    gemini.safeJsonParse.mockReturnValue(null);
    const token = await generateToken({ env: MOCK_ENV }, mockGoldUser.id);

    const res = await app.request(
      "/api/exam/multilevel/analyze",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          transcript: [{ speaker: "User", text: "hello" }],
          practicePart: "P1_1",
        }),
      },
      MOCK_ENV
    );

    expect(res.status).toBe(500);
  });

  it("analyzeExam should return 500 on Gemini error for full exam", async () => {
    const mockGoldUser = { id: "gold-user-1", subscription_tier: "gold" };
    db.getUserById.mockResolvedValue(mockGoldUser);
    gemini.generateText.mockResolvedValue("bad json");
    gemini.safeJsonParse.mockReturnValue(null);
    const token = await generateToken({ env: MOCK_ENV }, mockGoldUser.id);

    const res = await app.request(
      "/api/exam/multilevel/analyze",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          transcript: [{ speaker: "User", text: "hello" }],
          practicedPart: "FULL",
        }),
      },
      MOCK_ENV
    );

    expect(res.status).toBe(500);
  });
  // --- END OF FIXES ---
});
