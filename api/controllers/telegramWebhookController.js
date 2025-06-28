const axios = require("axios");
const { v4: uuidv4 } = require("uuid");
const OneTimeToken = require("../models/oneTimeTokenModel");

const TELEGRAM_API = `https://api.telegram.org/bot${process.env.TELEGRAM_BOT_TOKEN}`;
// The base URL of your publicly accessible server (from ngrok or production)
const SERVER_BASE_URL = process.env.SERVER_URL; // e.g., https://b4a7-123.ngrok-free.app

const handleWebhook = async (req, res) => {
  const update = req.body;

  if (update.message && update.message.text === "/start") {
    const chat = update.message.chat;
    const userFrom = update.message.from;

    if (!SERVER_BASE_URL) {
      console.error("FATAL: SERVER_URL environment variable is not set. Cannot create login URL.");
      return res.sendStatus(500);
    }

    console.log(`Received /start command from user: ${userFrom.first_name} (ID: ${userFrom.id})`);

    try {
      const token = uuidv4();
      await OneTimeToken.create({ token, telegramId: userFrom.id });

      // --- THIS IS THE KEY CHANGE ---
      // 1. Construct the URL to your new redirect endpoint.
      const redirectUrl = `${SERVER_BASE_URL}/api/auth/telegram/redirect?token=${token}`;

      const messageText = `✅ Welcome! Tap the button below to securely log in to the Multilevel App.`;

      // 2. Use a `login_url` button type instead of a `url` button.
      await axios.post(`${TELEGRAM_API}/sendMessage`, {
        chat_id: chat.id,
        text: messageText,
        reply_markup: {
          inline_keyboard: [
            [
              {
                text: "Log In to App",
                login_url: {
                  url: redirectUrl,
                },
              },
            ],
          ],
        },
      });
      // --- END OF CHANGE ---
    } catch (error) {
      console.error(
        "Error processing /start command:",
        error.response ? error.response.data : error.message
      );
      await axios.post(`${TELEGRAM_API}/sendMessage`, {
        chat_id: chat.id,
        text: "Sorry, an error occurred. Please try again later.",
      });
    }
  }

  res.sendStatus(200);
};

module.exports = { handleWebhook };
