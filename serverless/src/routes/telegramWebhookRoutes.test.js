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

  it("should handle Telegram API errors gracefully", async () => {
    // Mock fetch to return a failed response from Telegram
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ ok: false, description: "Chat not found" }), {
          status: 400,
        })
      )
    );

    const res = await app.request(
      "/api/telegram/webhook/test-bot-token",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: { text: "/start", from: { id: 1 }, chat: { id: 1 } } }),
      },
      MOCK_ENV
    );

    expect(res.status).toBe(200); // The webhook itself always returns 200
    expect(db.createOneTimeToken).not.toHaveBeenCalled(); // But the DB call should not happen
  });

  it("should handle missing message_id from Telegram response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ ok: true, result: {} }), { status: 200 }) // No message_id in result
      )
    );

    const res = await app.request(
      "/api/telegram/webhook/test-bot-token",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: { text: "/start", from: { id: 1 }, chat: { id: 1 } } }),
      },
      MOCK_ENV
    );

    expect(res.status).toBe(200);
    expect(db.createOneTimeToken).not.toHaveBeenCalled();
  });
});
