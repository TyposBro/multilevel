import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { db } from "../db/d1-client";

vi.mock("../db/d1-client.js");

describe("Telegram Webhook Route", () => {
  const MOCK_ENV = {
    TELEGRAM_BOT_TOKEN: "test-bot-token",
    SERVER_URL: "https://test.server.com",
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            ok: true,
            result: { message_id: 12345 },
          }),
          { status: 200 }
        )
      )
    );
  });

  it("should reject requests with the wrong bot token", async () => {
    const res = await app.request(
      "/api/telegram/webhook/wrong-token",
      { method: "POST" },
      MOCK_ENV
    );
    expect(res.status).toBe(401);
  });

  it("should process a /start command correctly", async () => {
    const telegramUpdate = {
      update_id: 1,
      message: {
        message_id: 100,
        from: { id: 555, is_bot: false, first_name: "Test" },
        chat: { id: 555, type: "private" },
        text: "/start",
      },
    };

    const res = await app.request(
      "/api/telegram/webhook/test-bot-token",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(telegramUpdate),
      },
      MOCK_ENV
    );

    expect(res.status).toBe(200);
    expect(global.fetch).toHaveBeenCalled();
    expect(db.createOneTimeToken).toHaveBeenCalledOnce();
  });
});
