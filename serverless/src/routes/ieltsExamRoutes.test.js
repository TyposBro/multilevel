// serverless/src/routes/ieltsExamRoutes.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";
import * as gemini from "../utils/gemini";
import * as kokoro from "../utils/kokoro";

// Mock all external dependencies
vi.mock("../db/d1-client.js");
vi.mock("../utils/gemini.js");
vi.mock("../utils/kokoro.js");

describe("IELTS Exam Routes", () => {
  const mockUser = { id: "user-ielts-123", email: "test@ielts.com" };
  const MOCK_ENV = {
    JWT_SECRET: "a-secure-test-secret-for-users",
    DB: db,
    GEMINI_API_KEY: "fake-gemini-key", // Needed for the controller logic
  };
  let token;

  beforeEach(async () => {
    vi.clearAllMocks();
    // For protected routes, always mock the user lookup
    db.getUserById.mockResolvedValue(mockUser);
    token = await generateToken({ env: MOCK_ENV }, mockUser.id);
  });

  it("POST /api/exam/ielts/start should start a new exam", async () => {
    // Arrange: Mock the external API calls
    const mockGeminiResponse = {
      examiner_line: "Hello, my name is Alex. What is your full name?",
      next_part: 1,
      cue_card: null,
      is_final_question: false,
    };
    gemini.generateText.mockResolvedValue(JSON.stringify(mockGeminiResponse));
    gemini.safeJsonParse.mockReturnValue(mockGeminiResponse);
    kokoro.getKokoroInputIds.mockResolvedValue([1, 2, 3]);

    // Act: Make the request
    const res = await app.request(
      "/api/exam/ielts/start",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      },
      MOCK_ENV
    );

    // Assert: Check the response and mock calls
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.examiner_line).toBe(mockGeminiResponse.examiner_line);
    expect(body.input_ids).toEqual([1, 2, 3]);
    expect(gemini.generateText).toHaveBeenCalledOnce();
    expect(kokoro.getKokoroInputIds).toHaveBeenCalledWith(
      expect.any(Object),
      mockGeminiResponse.examiner_line
    );
  });

  it("GET /api/exam/ielts/history should retrieve user exam history", async () => {
    // Arrange: Mock the database response
    const mockHistory = [
      { id: "hist-1", createdAt: "2024-01-01T10:00:00Z", overallBand: 7.5 },
      { id: "hist-2", createdAt: "2024-01-05T12:00:00Z", overallBand: 8.0 },
    ];
    db.getIeltsExamHistory.mockResolvedValue(mockHistory);

    // Act: Make the request
    const res = await app.request(
      "/api/exam/ielts/history",
      {
        headers: { Authorization: `Bearer ${token}` },
      },
      MOCK_ENV
    );

    // Assert: Check the response and mock calls
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.history).toHaveLength(2);
    expect(body.history[0].id).toBe("hist-1");
    expect(body.history[0].overallBand).toBe(7.5);
    // Note: The controller converts date strings to timestamps
    expect(body.history[0].examDate).toBe(new Date("2024-01-01T10:00:00Z").getTime());

    expect(db.getIeltsExamHistory).toHaveBeenCalledOnce();
    expect(db.getIeltsExamHistory).toHaveBeenCalledWith(MOCK_ENV.DB, mockUser.id);
  });

  it("GET /api/exam/ielts/result/:resultId should retrieve specific exam details", async () => {
    // Arrange
    const resultId = "result-abc";
    const mockResultDetails = {
      id: resultId,
      userId: mockUser.id,
      overallBand: 7.0,
      criteria: [{ criterionName: "Fluency", bandScore: 7.0, feedback: "Good." }],
      transcript: [{ speaker: "User", text: "Hello." }],
    };
    db.getIeltsExamResultDetails.mockResolvedValue(mockResultDetails);

    // Act
    const res = await app.request(
      `/api/exam/ielts/result/${resultId}`,
      {
        headers: { Authorization: `Bearer ${token}` },
      },
      MOCK_ENV
    );

    // Assert
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.id).toBe(resultId);
    expect(body.overallBand).toBe(7.0);

    expect(db.getIeltsExamResultDetails).toHaveBeenCalledOnce();
    expect(db.getIeltsExamResultDetails).toHaveBeenCalledWith(MOCK_ENV.DB, resultId, mockUser.id);
  });

  it("should return 500 if Gemini fails to start an exam", async () => {
    gemini.generateText.mockRejectedValue(new Error("API Down"));
    const res = await app.request(
      "/api/exam/ielts/start",
      { method: "POST", headers: { Authorization: `Bearer ${token}` } },
      MOCK_ENV
    );
    expect(res.status).toBe(500);
  });

  it("should return 500 if Gemini returns invalid JSON during start", async () => {
    gemini.generateText.mockResolvedValue("this is not json");
    gemini.safeJsonParse.mockReturnValue(null);
    const res = await app.request(
      "/api/exam/ielts/start",
      { method: "POST", headers: { Authorization: `Bearer ${token}` } },
      MOCK_ENV
    );
    expect(res.status).toBe(500);
  });

  it("should return 500 if database fails on analyze", async () => {
    gemini.generateText.mockResolvedValue(JSON.stringify({ overallBand: 7.0, criteria: [] }));
    gemini.safeJsonParse.mockReturnValue({ overallBand: 7.0, criteria: [] });
    db.createIeltsExamResult.mockRejectedValue(new Error("DB Error"));

    const res = await app.request(
      "/api/exam/ielts/analyze",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ transcript: [] }),
      },
      MOCK_ENV
    );

    expect(res.status).toBe(500);
  });
});
