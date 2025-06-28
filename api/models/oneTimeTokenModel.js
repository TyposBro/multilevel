const mongoose = require("mongoose");

const oneTimeTokenSchema = new mongoose.Schema({
  token: { type: String, required: true, unique: true, index: true },
  telegramId: { type: Number, required: true },
  // Automatically delete this document after 5 minutes
  createdAt: { type: Date, expires: "5m", default: Date.now },
});

module.exports = mongoose.model("OneTimeToken", oneTimeTokenSchema);
