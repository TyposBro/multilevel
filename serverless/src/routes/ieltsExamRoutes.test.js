import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";
import * as gemini from "../utils/gemini";
import * as kokoro from "../utils/kokoro";

vi.mock("../db/d1-client.js");
vi.mock("../utils/gemini.js");
vi.mock("../utils/kokoro.js");

describe("IELTS Exam Routes", () => {
  const mockUser = { id: "user-ielts-123", email: "test@ielts.com" };
  const MOCK_ENV = {
    JWT_SECRET: "a-secure-test-secret-for-users",
    DB: db,
    GEMINI_API_KEY: "fake-gemini-key",
  };
  let token;

  beforeEach(async () => {
    vi.clearAllMocks();
    db.getUserById.mockResolvedValue(mockUser);
    token = await generateToken({ env: MOCK_ENV }, mockUser.id);
  });

  it("POST /api/exam/ielts/start should start a new exam", async () => {
    const mockGeminiResponse = {
      examiner_line: "Hello",
      next_part: 1,
      cue_card: null,
      is_final_question: false,
    };
    gemini.generateText.mockResolvedValue(JSON.stringify(mockGeminiResponse));
    gemini.safeJsonParse.mockReturnValue(mockGeminiResponse);
    kokoro.getKokoroInputIds.mockResolvedValue([1, 2, 3]);

    const res = await app.request(
      "/api/exam/ielts/start",
      { method: "POST", headers: { Authorization: `Bearer ${token}` } },
      MOCK_ENV
    );
    expect(res.status).toBe(200);
  });

  it("POST /api/exam/ielts/step handles server error", async () => {
    gemini.generateText.mockRejectedValue(new Error("API Error"));
    const res = await app.request(
      "/api/exam/ielts/step",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({}),
      },
      MOCK_ENV
    );
    expect(res.status).toBe(500);
  });

  it("POST /api/exam/ielts/analyze should add 'type' to examples if missing", async () => {
    const mockAnalysis = {
      overallBand: 7.0,
      criteria: [{ criterionName: "Fluency", examples: [{ userQuote: "..." }] }], // Example is missing 'type'
    };
    gemini.generateText.mockResolvedValue(JSON.stringify(mockAnalysis));
    gemini.safeJsonParse.mockReturnValue(mockAnalysis);

    await app.request(
      "/api/exam/ielts/analyze",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ transcript: [] }),
      },
      MOCK_ENV
    );

    const requestCall = gemini.generateText.mock.calls[0][1];
    expect(requestCall).toContain("Fluency & Coherence");
  });

  // --- REPLACE THE "Edge Cases" BLOCK IN ieltsExamRoutes.test.js WITH THIS ---

  describe("Controller: ieltsExamController Edge Cases", () => {
    it("startExam should handle Gemini API errors", async () => {
      gemini.generateText.mockRejectedValue(new Error("API Down"));
      const res = await app.request(
        "/api/exam/ielts/start",
        { method: "POST", headers: { Authorization: `Bearer ${token}` } },
        MOCK_ENV
      );
      expect(res.status).toBe(500);
    });

    it("startExam should handle invalid JSON from Gemini", async () => {
      gemini.generateText.mockResolvedValue("this is not json");
      gemini.safeJsonParse.mockReturnValue(null);
      const res = await app.request(
        "/api/exam/ielts/start",
        { method: "POST", headers: { Authorization: `Bearer ${token}` } },
        MOCK_ENV
      );
      expect(res.status).toBe(500);
      expect(await res.json()).toEqual({
        message: "AI failed to generate a valid starting question.",
      });
    });

    it("handleExamStep should handle Gemini API errors", async () => {
      gemini.generateText.mockRejectedValue(new Error("API Down"));
      const res = await app.request(
        "/api/exam/ielts/step",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
          body: JSON.stringify({}),
        },
        MOCK_ENV
      );
      expect(res.status).toBe(500);
    });

    it("analyzeExam should add 'General' type for unknown criteria", async () => {
      const mockAnalysis = {
        overallBand: 6.5,
        criteria: [{ criterionName: "Overall Impression", examples: [{ userQuote: "..." }] }],
      };
      // --- THIS IS THE FIX ---
      // The controller calls generateText FIRST. We must mock it.
      gemini.generateText.mockResolvedValue(JSON.stringify(mockAnalysis));
      // We also mock safeJsonParse to ensure the exact object is returned.
      gemini.safeJsonParse.mockReturnValue(mockAnalysis);
      // --- END OF FIX ---

      await app.request(
        "/api/exam/ielts/analyze",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
          body: JSON.stringify({ transcript: [] }),
        },
        MOCK_ENV
      );

      expect(gemini.generateText).toHaveBeenCalled();
    });

    it("analyzeExam should handle database errors", async () => {
      gemini.safeJsonParse.mockReturnValue({ overallBand: 7.0, criteria: [] });
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
});
