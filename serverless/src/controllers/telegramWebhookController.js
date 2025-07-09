// {PATH_TO_PROJECT}/src/controllers/telegramWebhookController.js

import { db } from "../db/d1-client";

export const handleWebhook = async (c) => {
  const sentToken = c.req.param("token");
  const expectedToken = c.env.TELEGRAM_BOT_TOKEN;

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

      console.log(`Received /start command from user: ${userFrom.first_name} (ID: ${userFrom.id})`);

      const token = crypto.randomUUID();
      const redirectUrl = `${SERVER_BASE_URL}/api/auth/telegram/redirect?token=${token}`;
      const messageText = `✅ Welcome! Tap the button below to securely log in to the Multilevel App. This button will expire in 5 minutes.`;

      // --- START OF FIX ---
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

      // ALWAYS get the body, whether it's an error or success
      const responseData = await response.json();

      // Check if Telegram reported an error
      if (!responseData.ok) {
        console.error("Telegram API Error:", JSON.stringify(responseData));
        // Throw a more informative error
        throw new Error(`Telegram API responded with an error: ${responseData.description}`);
      }

      const botMessageId = responseData.result?.message_id;

      if (!botMessageId) {
        // This log will now contain the full response from Telegram, which will help us debug further
        console.error(
          "Could not find message_id in Telegram's response:",
          JSON.stringify(responseData)
        );
        throw new Error("Failed to get message_id from Telegram response.");
      }
      // --- END OF FIX ---

      await db.createOneTimeToken(c.env.DB, {
        token,
        telegramId: userFrom.id,
        botMessageId: botMessageId,
        userMessageId: message.message_id,
      });
    }
  } catch (error) {
    // This will now log the more specific error message from the 'throw' statements above
    console.error("Error processing /start command:", error.message);
  }

  return c.newResponse(null, 200);
};
