// routes/chatRoutes.js
const express = require('express');
const {
  sendMessage,
  getChatHistory,
  createNewChat,  
  listUserChats,
  deleteChat, updateChatTitle
} = require('../controllers/chatController');
const { protect } = require('../middleware/authMiddleware');

const router = express.Router();

// Apply protect middleware to all chat routes
router.use(protect);

// --- Routes ---

// POST /api/chat/          -> Create a new chat session
router.post('/', createNewChat); // Changed from /new for REST convention

// GET /api/chat/           -> List all chats for the logged-in user
router.get('/', listUserChats); // Changed from /list

// POST /api/chat/:chatId/message -> Send a message within a specific chat
router.post('/:chatId/message', sendMessage); // Added :chatId param

// GET /api/chat/:chatId/history  -> Get history for a specific chat
router.get('/:chatId/history', getChatHistory); // Added :chatId param

// DELETE /api/chat/:chatId -> Delete a specific chat
router.delete('/:chatId', deleteChat);
// PUT /api/chat/:chatId/title -> Update a chat's title
router.put('/:chatId/title', updateChatTitle);


module.exports = router;