// models/Chat.js
const mongoose = require('mongoose');

// Structure matching Gemini API's history format
const messageSchema = new mongoose.Schema({
  role: {
    type: String,
    enum: ['user', 'model'],
    required: true,
  },
  parts: [{
    text: {
      type: String,
      required: true,
    }
  }],
}, { _id: false });

const chatSchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true,
    index: true,
  },
  // Add a title for the chat session
  title: {
    type: String,
    trim: true,
    default: 'New Chat', // Provide a default title
  },
  history: [messageSchema], // History specific to this chat document
}, {
  timestamps: true, // Adds createdAt and updatedAt
});

module.exports = mongoose.model('Chat', chatSchema);