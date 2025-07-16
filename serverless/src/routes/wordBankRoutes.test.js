// serverless/src/routes/wordBankRoutes.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { db } from "../db/d1-client";

vi.mock("../db/d1-client.js");

describe("Word Bank Routes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("GET /api/wordbank/levels should return a list of levels", async () => {
    db.getWordBankLevels.mockResolvedValue(["A1", "B2", "C1"]);

    // --- THIS IS THE FIX ---
    // Create a mock environment object. Even though `db` is mocked at the module
    // level, Hono's `c.env` needs an object to be populated from during the request.
    const MOCK_ENV = {
      DB: db, // Pass the mocked db object here
    };

    // Pass the mock environment as the third argument to app.request
    const res = await app.request("/api/wordbank/levels", {}, MOCK_ENV);
    // --- END OF FIX ---

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toEqual(["A1", "B2", "C1"]);
    expect(db.getWordBankLevels).toHaveBeenCalledOnce();
  });

  it("GET /api/wordbank/topics should return topics for a given level", async () => {
    // Arrange
    const MOCK_ENV = { DB: db };
    db.getWordBankTopics.mockResolvedValue(["Technology", "Health"]);
    const level = "B2";

    // Act
    const res = await app.request(`/api/wordbank/topics?level=${level}`, {}, MOCK_ENV);

    // Assert
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toEqual(["Technology", "Health"]);
    expect(db.getWordBankTopics).toHaveBeenCalledWith(MOCK_ENV.DB, level);
  });

  it("GET /api/wordbank/words should return words for a given level and topic", async () => {
    // Arrange
    const MOCK_ENV = { DB: db };
    const mockWords = [{ id: "word-1", word: "algorithm", topic: "Technology" }];
    db.getWordBankWords.mockResolvedValue(mockWords);
    const level = "B2";
    const topic = "Technology";

    // Act
    const res = await app.request(
      `/api/wordbank/words?level=${level}&topic=${topic}`,
      {},
      MOCK_ENV
    );

    // Assert
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toEqual(mockWords);
    expect(db.getWordBankWords).toHaveBeenCalledWith(MOCK_ENV.DB, level, topic);
  });

  it("GET /api/wordbank/words should return 400 if topic is missing", async () => {
    // Arrange
    const MOCK_ENV = { DB: db };
    const level = "B2";

    // Act
    const res = await app.request(`/api/wordbank/words?level=${level}`, {}, MOCK_ENV);

    // Assert
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.message).toContain("topic query parameters are required");
  });

  it("GET /api/wordbank/topics should fail if level is missing", async () => {
    const MOCK_ENV = { DB: db };
    const res = await app.request(`/api/wordbank/topics`, {}, MOCK_ENV);
    expect(res.status).toBe(400);
  });

  it("should return 500 on database error", async () => {
    db.getWordBankLevels.mockRejectedValue(new Error("DB Error"));
    const MOCK_ENV = { DB: db };
    const res = await app.request("/api/wordbank/levels", {}, MOCK_ENV);
    expect(res.status).toBe(500);
  });
});
