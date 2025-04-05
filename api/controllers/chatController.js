// controllers/chatController.js
const Chat = require('../models/Chat');
const { GoogleGenerativeAI } = require('@google/generative-ai');
require('dotenv').config();

// --- Gemini Setup --- (Keep as is)
if (!process.env.GEMINI_API_KEY) { /* ... */ }
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash"});


// @desc    Create a new empty chat session for the user
// @route   POST /api/chat/
// @access  Private
const createNewChat = async (req, res) => {
  const userId = req.user._id;
  const { title } = req.body; // Optional title from request body

  try {
    const newChat = new Chat({
      userId,
      history: [], // Start with empty history
      title: title || `Chat started ${new Date().toLocaleDateString()}`, // Use provided title or generate default
    });

    const savedChat = await newChat.save();

    res.status(201).json({
      message: 'New chat created successfully.',
      chatId: savedChat._id,
      title: savedChat.title,
      createdAt: savedChat.createdAt,
    });
  } catch (error) {
    console.error('Error creating new chat:', error);
    res.status(500).json({ message: 'Failed to create new chat session' });
  }
};


// @desc    List all chat sessions for the logged-in user
// @route   GET /api/chat/
// @access  Private
const listUserChats = async (req, res) => {
  const userId = req.user._id;

  try {
    // Find chats for the user, select only necessary fields, sort by most recently updated
    const chats = await Chat.find({ userId })
                            .select('_id title createdAt updatedAt') // Only send summary info
                            .sort({ updatedAt: -1 }); // Show most recent first

    res.json({ chats });
  } catch (error) {
    console.error('Error fetching user chats:', error);
    res.status(500).json({ message: 'Failed to retrieve chat list' });
  }
};


// @desc    Send a message within a specific chat and update its history
// @route   POST /api/chat/:chatId/message
// @access  Private
const sendMessage = async (req, res) => {
  const { prompt } = req.body;
  const { chatId } = req.params; // Get chatId from URL parameters
  const userId = req.user._id;

  if (!prompt) {
    return res.status(400).json({ message: 'Prompt is required' });
  }
  if (!chatId) {
    return res.status(400).json({ message: 'Chat ID is required' });
  }

  try {
    // 1. Find the specific chat AND verify ownership
    const chat = await Chat.findOne({ _id: chatId, userId: userId });

    if (!chat) {
      return res.status(404).json({ message: 'Chat session not found or you do not have permission to access it.' });
    }

    // 2. Prepare history for Gemini API (using the format from our schema)
    const geminiHistory = chat.history.map(msg => ({
        role: msg.role,
        parts: msg.parts.map(p => ({ text: p.text })),
    }));

    // 3. Start chat session with Gemini using the specific chat's history
    const chatSession = model.startChat({ history: geminiHistory });

    // 4. Send the new user prompt to Gemini
    const result = await chatSession.sendMessage(prompt);
    const response = await result.response;
    const modelResponseText = response.text();

    // 5. Update *this specific chat's* history in MongoDB
    chat.history.push({ role: 'user', parts: [{ text: prompt }] });
    chat.history.push({ role: 'model', parts: [{ text: modelResponseText }] });

    // Mongoose automatically updates 'updatedAt' timestamp on save
    await chat.save();

    // 6. Send response back to client
    res.json({
        message: modelResponseText,
        chatId: chat._id, // Include chatId in response for clarity
        // Optionally send updated history if needed: fullHistory: chat.history
    });

  } catch (error) {
    console.error('Gemini API or DB Error in specific chat:', error);
    if (error.message.includes('SAFETY')) { /* ... */ }
    if (error.message.includes('API key not valid')) { /* ... */ }
    // Handle CastError if chatId format is invalid
    if (error.name === 'CastError') {
         return res.status(400).json({ message: 'Invalid Chat ID format.' });
    }
    res.status(500).json({ message: 'Failed to get response from AI or save chat' });
  }
};


// @desc    Get chat history for a specific chat session
// @route   GET /api/chat/:chatId/history
// @access  Private
const getChatHistory = async (req, res) => {
  const { chatId } = req.params; // Get chatId from URL parameters
  const userId = req.user._id;

  if (!chatId) {
    return res.status(400).json({ message: 'Chat ID is required' });
  }

  try {
    // Find the specific chat AND verify ownership
    const chat = await Chat.findOne({ _id: chatId, userId: userId });

    if (!chat) {
      return res.status(404).json({ message: 'Chat session not found or you do not have permission to access it.' });
    }

    res.json({
        chatId: chat._id,
        title: chat.title,
        history: chat.history,
        createdAt: chat.createdAt,
        updatedAt: chat.updatedAt
    });

  } catch (error) {
    console.error('Error fetching specific chat history:', error);
     if (error.name === 'CastError') {
         return res.status(400).json({ message: 'Invalid Chat ID format.' });
    }
    res.status(500).json({ message: 'Failed to retrieve chat history' });
  }
};


// @desc    Delete a specific chat session
// @route   DELETE /api/chat/:chatId
// @access  Private
const deleteChat = async (req, res) => {
  const { chatId } = req.params;
  const userId = req.user._id;

  if (!chatId) {
    return res.status(400).json({ message: 'Chat ID is required' });
  }

  try {
    // Find the chat to ensure it exists and belongs to the user before deleting
    const chat = await Chat.findOne({ _id: chatId, userId: userId });

    if (!chat) {
      // Use 404 even if the chat exists but belongs to another user for security
      return res.status(404).json({ message: 'Chat session not found or you do not have permission.' });
    }

    // Perform the deletion
    await Chat.deleteOne({ _id: chatId, userId: userId }); // Redundant userId check, but safe

    res.json({ message: 'Chat session deleted successfully.', chatId: chatId });

  } catch (error) {
    console.error('Error deleting chat session:', error);
    if (error.name === 'CastError') {
      return res.status(400).json({ message: 'Invalid Chat ID format.' });
    }
    res.status(500).json({ message: 'Failed to delete chat session' });
  }
};


// @desc    Update the title of a specific chat session
// @route   PUT /api/chat/:chatId/title  (or PUT /api/chat/:chatId)
// @access  Private
const updateChatTitle = async (req, res) => {
  const { chatId } = req.params;
  const { title } = req.body; // Get the new title from the request body
  const userId = req.user._id;

  if (!chatId) {
    return res.status(400).json({ message: 'Chat ID is required' });
  }
  if (!title || typeof title !== 'string' || title.trim() === '') {
    return res.status(400).json({ message: 'A valid title is required' });
  }

  try {
    // Find the chat, ensuring it belongs to the logged-in user
    const chat = await Chat.findOne({ _id: chatId, userId: userId });

    if (!chat) {
      return res.status(404).json({ message: 'Chat session not found or you do not have permission.' });
    }

    // Update the title
    chat.title = title.trim();
    const updatedChat = await chat.save(); // Save the changes (also updates 'updatedAt')

    res.json({
      message: 'Chat title updated successfully.',
      chatId: updatedChat._id,
      title: updatedChat.title,
      updatedAt: updatedChat.updatedAt,
    });

  } catch (error) {
    console.error('Error updating chat title:', error);
    if (error.name === 'CastError') {
      return res.status(400).json({ message: 'Invalid Chat ID format.' });
    }
    if (error.name === 'ValidationError') { // In case future validation is added to title
        return res.status(400).json({ message: error.message });
    }
    res.status(500).json({ message: 'Failed to update chat title' });
  }
};


// --- Make sure to export the new functions ---
module.exports = {
  createNewChat,
  listUserChats,
  sendMessage,
  getChatHistory,
  deleteChat, 
  updateChatTitle,
};