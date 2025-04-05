
// ---
// routes/chatRoutes.js
const express = require('express');
const { sendMessage, getChatHistory } = require('../controllers/chatController'); // Adjust path
const { protect } = require('../middleware/authMiddleware'); // Adjust path

const router = express.Router();

// All chat routes require authentication
router.use(protect); // Apply protect middleware to all routes below

router.post('/message', sendMessage);
router.get('/history', getChatHistory);

module.exports = router;