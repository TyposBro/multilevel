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
});
