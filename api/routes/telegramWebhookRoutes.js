const express = require("express");
const router = express.Router();
const { handleWebhook } = require("../controllers/telegramWebhookController");

// This endpoint must be public for Telegram to reach it.
// Use a hard-to-guess path for simple security.
router.post(`/${process.env.TELEGRAM_BOT_TOKEN}`, handleWebhook);

module.exports = router;
