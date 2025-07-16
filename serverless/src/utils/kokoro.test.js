import { describe, it, expect, vi, afterEach } from "vitest";
import { getKokoroInputIds } from "./kokoro";

describe("Kokoro Utility", () => {
  const originalFetch = global.fetch;

  afterEach(() => {
    global.fetch = originalFetch;
  });

  const mockContext = {
    env: {
      KOKORO_PREPROCESS_URL: "http://fake-kokoro.com/preprocess",
    },
  };

  it("should return an array of input_ids on success", async () => {
    const mockResponse = { results: [{ input_ids: [[1, 2, 3]] }] };
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockResponse),
    });

    const ids = await getKokoroInputIds(mockContext, "test text");
    expect(ids).toEqual([1, 2, 3]);
  });

  it("should return an empty array if text is empty or null", async () => {
    expect(await getKokoroInputIds(mockContext, "")).toEqual([]);
    expect(await getKokoroInputIds(mockContext, null)).toEqual([]);
  });

  it("should return an empty array if the fetch response is not ok", async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: false, status: 500 });
    const ids = await getKokoroInputIds(mockContext, "test text");
    expect(ids).toEqual([]);
  });

  it("should return an empty array if fetch itself throws an error", async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error("Network Error"));
    const ids = await getKokoroInputIds(mockContext, "test text");
    expect(ids).toEqual([]);
  });

  it("should return an empty array if the response JSON is malformed", async () => {
    const mockResponse = { some: "other structure" }; // Missing results/input_ids
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockResponse),
    });

    const ids = await getKokoroInputIds(mockContext, "test text");
    expect(ids).toEqual([]);
  });
});
