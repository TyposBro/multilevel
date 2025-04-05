// ---
// controllers/chatController.js
const Chat = require('../models/Chat');
const { GoogleGenerativeAI } = require('@google/generative-ai');
require('dotenv').config();

// --- Gemini Setup ---
if (!process.env.GEMINI_API_KEY) {
  console.error("FATAL ERROR: GEMINI_API_KEY environment variable not set.");
  process.exit(1);
}
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash"}); // Or your preferred model

// --- Helper to get or create chat ---
async function getOrCreateChat(userId) {
    let chat = await Chat.findOne({ userId });
    if (!chat) {
        console.log(`No chat found for user ${userId}, creating new one.`);
        chat = new Chat({ userId, history: [] });
        await chat.save(); // Save the initial empty chat
    }
    return chat;
}


// @desc    Send a message to Gemini and update chat history
// @route   POST /api/chat/message
// @access  Private (requires user to be logged in)
const sendMessage = async (req, res) => {
  const { prompt } = req.body;
  const userId = req.user._id; // From authMiddleware

  if (!prompt) {
    return res.status(400).json({ message: 'Prompt is required' });
  }

  try {
    // 1. Get existing chat history or create a new chat document
    const chat = await getOrCreateChat(userId);

    // 2. Prepare history for Gemini API (using the format from our schema)
    const geminiHistory = chat.history.map(msg => ({
        role: msg.role,
        parts: msg.parts.map(p => ({ text: p.text })), // Ensure parts is an array of objects
    }));

    // 3. Start chat session with Gemini using the history
    const chatSession = model.startChat({
      history: geminiHistory,
      // Optional: Add generationConfig if needed
      // generationConfig: { maxOutputTokens: 100 }
    });

    // 4. Send the new user prompt to Gemini
    const result = await chatSession.sendMessage(prompt);
    const response = await result.response;
    const modelResponseText = response.text();

    // 5. Update chat history in MongoDB
    chat.history.push({ role: 'user', parts: [{ text: prompt }] });
    chat.history.push({ role: 'model', parts: [{ text: modelResponseText }] });

    await chat.save();

    // 6. Send response back to client
    res.json({
        message: modelResponseText, // Send just the latest model response
        // Optionally send the full updated history if the frontend needs it
        // fullHistory: chat.history
    });

  } catch (error) {
    console.error('Gemini API or DB Error:', error);
    // Check for specific Gemini errors if needed
    if (error.message.includes('SAFETY')) {
         return res.status(400).json({ message: 'Request blocked due to safety concerns.' });
    }
     if (error.message.includes('API key not valid')) {
         return res.status(500).json({ message: 'Server configuration error: Invalid Gemini API Key.' });
     }
    res.status(500).json({ message: 'Failed to get response from AI or save chat' });
  }
};


// @desc    Get chat history for the logged-in user
// @route   GET /api/chat/history
// @access  Private
const getChatHistory = async (req, res) => {
  const userId = req.user._id;

  try {
    const chat = await Chat.findOne({ userId }); // Find the chat document

    if (!chat) {
      // If no chat document exists yet, return empty history
      return res.json({ history: [] });
    }

    res.json({ history: chat.history }); // Return the history array
  } catch (error) {
    console.error('Error fetching chat history:', error);
    res.status(500).json({ message: 'Failed to retrieve chat history' });
  }
};

module.exports = {
  sendMessage,
  getChatHistory,
};