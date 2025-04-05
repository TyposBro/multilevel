// ---
// models/Chat.js
const mongoose = require('mongoose');

// Structure matching Gemini API's history format
const messageSchema = new mongoose.Schema({
  role: {
    type: String,
    enum: ['user', 'model'], // Or 'function' if you use function calling
    required: true,
  },
  parts: [{
    text: {
      type: String,
      required: true,
    }
  }],
}, { _id: false }); // Don't create separate _id for each message part

const chatSchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true,
    index: true, // Index for faster lookups by user
  },
  // Store conversation history directly
  history: [messageSchema],
  // Could add a title, summary, etc. later if needed
  // title: String,
}, {
  timestamps: true, // Adds createdAt and updatedAt automatically
});

module.exports = mongoose.model('Chat', chatSchema);