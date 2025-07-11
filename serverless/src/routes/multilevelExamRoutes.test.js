// serverless/src/routes/multilevelExamRoutes.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";

// Mock the Gemini API
vi.mock("../utils/gemini.js", () => ({
  generateText: vi.fn().mockResolvedValue(
    JSON.stringify({
      totalScore: 55,
      feedbackBreakdown: [{ part: "Part 1.1", score: 10, feedback: "Good start." }],
    })
  ),
  safeJsonParse: (text) => JSON.parse(text),
}));

// Mock the entire d1-client module
vi.mock("../db/d1-client.js");

describe("Multilevel Exam Routes", () => {
  const MOCK_ENV = {
    JWT_SECRET: "test-secret",
    // We no longer need to mock DB here, it's handled by vi.mock above
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("POST /api/exam/multilevel/analyze should return 403 if free user exceeds daily full exam limit", async () => {
    const mockFreeUser = {
      id: "free-user-1",
      subscription_tier: "free",
      dailyUsage_fullExams_count: 1,
      dailyUsage_fullExams_lastReset: new Date().toISOString(),
    };
    db.getUserById.mockResolvedValue(mockFreeUser); // Setup the mock for this test case
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
    const mockGoldUser = {
      id: "gold-user-1",
      subscription_tier: "gold",
    };
    db.getUserById.mockResolvedValue(mockGoldUser); // Setup the mock
    db.createMultilevelExamResult.mockResolvedValue({ id: "result-xyz" }); // Mock the result creation

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
    const body = await res.json();
    expect(body.resultId).toBe("result-xyz");
    expect(db.createMultilevelExamResult).toHaveBeenCalled();
  });
});
