// {PATH_TO_PROJECT}/src/controllers/telegramWebhookController.js

import { db } from "../db/d1-client";

/**
 * @desc    Handles incoming webhooks from Telegram.
 * @route   POST /api/telegram/webhook/:token
 * @access  Public
 */
export const handleWebhook = async (c) => {
  const sentToken = c.req.param("token");
  const expectedToken = c.env.TELEGRAM_BOT_TOKEN;

  // Basic security check to ensure the request is from Telegram
  if (sentToken !== expectedToken) {
    return c.json({ message: "Unauthorized" }, 401);
  }

  const TELEGRAM_API = `https://api.telegram.org/bot${expectedToken}`;

  try {
    const update = await c.req.json();

    if (update.message && update.message.text === "/start") {
      const message = update.message;
      const userFrom = message.from;
      const chat = message.chat;

      const SERVER_BASE_URL = c.env.SERVER_URL;
      if (!SERVER_BASE_URL) {
        console.error("FATAL: SERVER_URL environment variable is not set in wrangler.toml.");
        return c.newResponse(null, 500);
      }

      const token = crypto.randomUUID();
      const redirectUrl = `${SERVER_BASE_URL}/api/auth/telegram/redirect?token=${token}`;
      const messageText = `✅ Welcome! Tap the button below to securely log in to the Multilevel App. This button will expire in 5 minutes.`;

      const response = await fetch(`${TELEGRAM_API}/sendMessage`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          chat_id: chat.id,
          text: messageText,
          reply_markup: {
            inline_keyboard: [[{ text: "Log In to App", login_url: { url: redirectUrl } }]],
          },
        }),
      });

      const sentMessageData = await response.json();
      const botMessageId = sentMessageData.result?.message_id;

      if (!botMessageId) {
        throw new Error("Failed to get message_id from Telegram response.");
      }

      // Use our D1 client to create the token
      await db.createOneTimeToken(c.env.DB, {
        token,
        telegramId: userFrom.id,
        botMessageId: botMessageId,
        userMessageId: message.message_id,
      });
    }
  } catch (error) {
    console.error("Error processing /start command:", error.message);
    // In case of an error, we don't want to throw and break the webhook flow.
    // We just log it and respond successfully to Telegram.
  }

  // Always respond to Telegram with a 200 OK to acknowledge receipt of the webhook.
  return c.newResponse(null, 200);
};
