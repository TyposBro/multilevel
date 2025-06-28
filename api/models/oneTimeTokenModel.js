const mongoose = require("mongoose");

const oneTimeTokenSchema = new mongoose.Schema({
  token: { type: String, required: true, unique: true, index: true },
  telegramId: { type: Number, required: true },
  botMessageId: { type: Number, required: true }, // Renamed for clarity: The ID of the bot's message
  userMessageId: { type: Number, required: true }, // <-- NEW FIELD: The ID of the user's /start message
  createdAt: { type: Date, expires: "5m", default: Date.now },
});

module.exports = mongoose.model("OneTimeToken", oneTimeTokenSchema);
