// serverless/src/routes/authRoutes.public.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import { verifyTelegramToken } from "../controllers/authController";
import app from "../index";
import { db } from "../db/d1-client";

vi.mock("../db/d1-client.js");
// This mock is now safe because it's contained to this file.
vi.mock("../utils/generateToken.js", () => ({
  generateToken: vi.fn().mockResolvedValue("mock-jwt-token"),
}));

describe("Public Auth Routes", () => {
  const MOCK_ENV = {
    DB: db,
    TELEGRAM_BOT_TOKEN: "fake-token",
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("googleSignIn should create a new user if one does not exist", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve({ sub: "new-google-id", email: "new@google.com", name: "New User" }),
    });
    db.findUserByProviderId.mockResolvedValue(null);
    db.createUser.mockResolvedValue({ id: "new-user-id" });
    const res = await app.request(
      "/api/auth/google-signin",
      {
        method: "POST",
        body: JSON.stringify({ idToken: "valid-token" }),
        headers: { "Content-Type": "application/json" },
      },
      MOCK_ENV
    );
    expect(res.status).toBe(200);
  });

  it("verifyTelegramToken controller should create new user and handle background errors", async () => {
    const tokenData = { telegramId: 123, botMessageId: 456, userMessageId: 789 };
    db.findOneTimeTokenAndDelete.mockResolvedValue(tokenData);
    db.findUserByProviderId.mockResolvedValue(null);
    db.createUser.mockResolvedValue({ id: "new-tg-user" });
    global.fetch = vi.fn().mockRejectedValue(new Error("Telegram API down"));

    const mockContext = {
      req: { json: async () => ({ oneTimeToken: "valid-token" }) },
      env: MOCK_ENV,
      executionCtx: { waitUntil: vi.fn() },
      json: (data, status) =>
        new Response(JSON.stringify(data), {
          status: status || 200,
          headers: { "Content-Type": "application/json" },
        }),
    };
    const response = await verifyTelegramToken(mockContext);
    expect(response.status).toBe(200);
  });
});
