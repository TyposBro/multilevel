// {PATH_TO_PROJECT}/src/controllers/telegramWebhookController.js

import { db } from "../db/d1-client";

/**
 * A factory function that creates a Hono request handler for a specific Telegram bot.
 * @param {object} config - Configuration for this bot instance.
 * @param {string} config.secretKeyName - The name of the environment variable holding the bot's token (e.g., 'TELEGRAM_BOT_TOKEN').
 * @param {string} config.redirectPath - The API path for the login redirect (e.g., '/api/auth/telegram/redirect').
 * @returns {Function} An async Hono handler function.
 */
export const createTelegramWebhookHandler = (config) => {
  return async (c) => {
    const sentToken = c.req.param("token");
    const expectedToken = c.env[config.secretKeyName];

    if (!expectedToken) {
      console.error(`FATAL: Secret for ${config.secretKeyName} is not set in environment.`);
      return c.newResponse(null, 500);
    }

    if (sentToken !== expectedToken) {
      return c.json({ message: "Unauthorized" }, 401);
    }

    const TELEGRAM_API = `https://api.telegram.org/bot${expectedToken}`;

    try {
      const update = await c.req.json();

      if (update.message && update.message.text && update.message.text.startsWith("/start")) {
        const message = update.message;
        const userFrom = message.from;
        const chat = message.chat;

        const SERVER_BASE_URL = c.env.SERVER_URL;
        if (!SERVER_BASE_URL) {
          console.error("FATAL: SERVER_URL environment variable is not set.");
          return c.newResponse(null, 500);
        }

        console.log(
          `Received /start command for ${config.secretKeyName} from user: ${userFrom.id}`
        );

        const oneTimeToken = crypto.randomUUID();
        // Use the redirectPath from the configuration to build the final URL
        const redirectUrl = `${SERVER_BASE_URL}${config.redirectPath}?token=${oneTimeToken}`;
        const messageText = `✅ Welcome! Tap the button below to securely log in. This button will expire in 5 minutes.`;

        const response = await fetch(`${TELEGRAM_API}/sendMessage`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            chat_id: chat.id,
            text: messageText,
            reply_markup: {
              inline_keyboard: [[{ text: "Log In", login_url: { url: redirectUrl } }]],
            },
          }),
        });

        const responseData = await response.json();

        if (!responseData.ok) {
          console.error("Telegram API Error:", JSON.stringify(responseData));
          throw new Error(`Telegram API responded with an error: ${responseData.description}`);
        }

        const botMessageId = responseData.result?.message_id;
        if (!botMessageId) {
          console.error(
            "Could not find message_id in Telegram's response:",
            JSON.stringify(responseData)
          );
          throw new Error("Failed to get message_id from Telegram response.");
        }

        await db.createOneTimeToken(c.env.DB, {
          token: oneTimeToken,
          telegramId: userFrom.id,
          botMessageId: botMessageId,
          userMessageId: message.message_id,
        });
      }
    } catch (error) {
      console.error(`Error processing webhook for ${config.secretKeyName}:`, error.message);
    }

    // Always return 200 OK to Telegram to acknowledge receipt of the webhook.
    return c.newResponse(null, 200);
  };
};
