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

  it("POST /api/exam/multilevel/analyze should return 403 if free user exceeds daily part practice limit", async () => {
    // Arrange
    const mockFreeUser = {
      id: "free-user-2",
      subscription_tier: "free",
      // Mock the part practice counter, not the full exam counter
      dailyUsage_partPractices_count: 3, // The limit is 3
      dailyUsage_partPractices_lastReset: new Date().toISOString(),
    };
    db.getUserById.mockResolvedValue(mockFreeUser);
    const token = await generateToken({ env: MOCK_ENV }, mockFreeUser.id);

    // Act
    const res = await app.request(
      "/api/exam/multilevel/analyze",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          transcript: [{ speaker: "User", text: "hello" }],
          practicePart: "P1_1", // Specify this is a part practice
        }),
      },
      MOCK_ENV
    );

    // Assert
    expect(res.status).toBe(403);
    const body = await res.json();
    expect(body.message).toContain("all 3 of your free part practices");
  });

  it("should return 500 if database fails during usage update", async () => {
    const mockFreeUser = {
      id: "free-user-1",
      subscription_tier: "free",
      dailyUsage_fullExams_count: 0,
    };
    db.getUserById.mockResolvedValue(mockFreeUser);
    db.updateUserUsage.mockRejectedValue(new Error("DB Error")); // Simulate failure
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

    expect(res.status).toBe(500); // It will fail when trying to update usage
  });

  it("should return 400 if transcript is missing", async () => {
    const token = await generateToken({ env: MOCK_ENV }, { id: "user" });
    const res = await app.request(
      "/api/exam/multilevel/analyze",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ transcript: [] }), // Empty transcript
      },
      MOCK_ENV
    );
    expect(res.status).toBe(400);
  });
});
