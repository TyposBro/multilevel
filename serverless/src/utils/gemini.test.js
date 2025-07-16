// serverless/src/utils/gemini.test.js
import { describe, it, expect, vi } from "vitest";
import { safeJsonParse, generateText } from "./gemini";

describe("Gemini Utility", () => {
  describe("safeJsonParse", () => {
    it("should parse valid JSON", () => {
      const json = safeJsonParse('```json\n{"key":"value"}\n```');
      expect(json).toEqual({ key: "value" });
    });

    it("should return null for invalid JSON", () => {
      expect(safeJsonParse("not json")).toBeNull();
      expect(safeJsonParse('{"key":')).toBeNull();
      expect(safeJsonParse(null)).toBeNull();
    });

    it("should return null for a malformed JSON string that contains brackets", () => {
      const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
      const json = safeJsonParse('```json\n{"key": "value\'}\n```'); // Invalid JSON
      expect(json).toBeNull();
      expect(consoleErrorSpy).toHaveBeenCalledWith(
        expect.stringContaining("[safeJsonParse] CRITICAL: Failed to parse extracted JSON string."),
        expect.any(Object)
      );
      consoleErrorSpy.mockRestore();
    });
  });

  describe("generateText", () => {
    it("should call fetch and return text", async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve({ candidates: [{ content: { parts: [{ text: "response text" }] } }] }),
      });
      global.fetch = mockFetch;

      const mockContext = { env: { GEMINI_API_KEY: "fake-key" } };
      const response = await generateText(mockContext, "prompt");

      expect(response).toBe("response text");
      expect(mockFetch).toHaveBeenCalledOnce();
    });

    it("should throw error if API key is missing", async () => {
      const mockContext = { env: {} }; // No API key
      await expect(generateText(mockContext, "prompt")).rejects.toThrow(
        "FATAL ERROR: GEMINI_API_KEY is not set"
      );
    });

    it("should throw error on fetch failure", async () => {
      global.fetch = vi.fn().mockResolvedValue({ ok: false, status: 500, text: () => "error" });
      const mockContext = { env: { GEMINI_API_KEY: "fake-key" } };
      await expect(generateText(mockContext, "prompt")).rejects.toThrow(
        "Gemini API request failed with status 500"
      );
    });
  });
});
