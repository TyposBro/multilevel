const axios = require('axios');
const { v4: uuidv4 } = require('uuid');
const OneTimeToken = require('../models/oneTimeTokenModel');

const TELEGRAM_API = `https://api.telegram.org/bot${process.env.TELEGRAM_BOT_TOKEN}`;
const SERVER_BASE_URL = process.env.SERVER_URL;

const handleWebhook = async (req, res) => {
  const update = req.body;

  if (update.message && update.message.text === '/start') {
    const message = update.message;
    const userFrom = message.from;
    const chat = message.chat;

    if (!SERVER_BASE_URL) {
      console.error("FATAL: SERVER_URL environment variable is not set.");
      return res.sendStatus(500);
    }
    
    console.log(`Received /start command from user: ${userFrom.first_name} (ID: ${userFrom.id})`);

    try {
      const token = uuidv4();
      const redirectUrl = `${SERVER_BASE_URL}/api/auth/telegram/redirect?token=${token}`;
      const messageText = `✅ Welcome! Tap the button below to securely log in to the Multilevel App. This button will expire in 5 minutes.`;

      const sentMessageResponse = await axios.post(`${TELEGRAM_API}/sendMessage`, {
        chat_id: chat.id,
        text: messageText,
        reply_markup: {
          inline_keyboard: [[{ text: "Log In to App", login_url: { url: redirectUrl } }]]
        }
      });

      // You correctly store the ID in a variable named `botMessageId`.
      const botMessageId = sentMessageResponse.data.result.message_id;

      if (!botMessageId) {
        throw new Error("Failed to get message_id from Telegram response.");
      }
      
      const userMessageId = message.message_id; 

      // --- THIS IS THE FIX ---
      // Use the correct variable name `botMessageId` when creating the document.
      await OneTimeToken.create({
        token: token,
        telegramId: userFrom.id,
        botMessageId: botMessageId,   // Corrected from `messageId`
        userMessageId: userMessageId,
      });
      // --- END OF FIX ---

    } catch (error) {
      console.error("Error processing /start command:", error.response ? error.response.data : error.message);
      await axios.post(`${TELEGRAM_API}/sendMessage`, {
        chat_id: chat.id,
        text: 'Sorry, an error occurred. Please try again later.'
      });
    }
  }

  res.sendStatus(200);
};

module.exports = { handleWebhook };