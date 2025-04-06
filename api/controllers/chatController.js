// controllers/chatController.js
const Chat = require('../models/Chat');
const { GoogleGenerativeAI } = require('@google/generative-ai');
const axios = require('axios');
require('dotenv').config();

// --- Gemini Setup --- (Keep as is)
if (!process.env.GEMINI_API_KEY) { /* ... */ }
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash"});

// --- Kokoro TTS Service URL ---
// Use the internal IP/hostname if running in the same network/cloud env
// Use localhost if running both on the same machine for testing
const KOKORO_TTS_URL = process.env.KOKORO_TTS_SERVICE_URL || 'http://localhost:5005/synthesize';

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
    const { chatId } = req.params;
    const userId = req.user._id;

    if (!prompt) return res.status(400).json({ message: 'Prompt is required' });
    if (!chatId) return res.status(400).json({ message: 'Chat ID is required' });

    try {
        // 1. Find Chat & Verify Ownership
        const chat = await Chat.findOne({ _id: chatId, userId: userId });
        if (!chat) return res.status(404).json({ message: 'Chat not found or permission denied.' });

        // 2. Prepare History for Gemini
        const geminiHistory = chat.history.map(msg => ({ /* ... */ }));

        // 3. Call Gemini
        console.log("Calling Gemini API...");
        const chatSession = model.startChat({ history: geminiHistory });
        const result = await chatSession.sendMessage(prompt);
        const response = await result.response;
        const modelResponseText = response.text();
        console.log("Received response from Gemini.");

        // 4. Update Chat History in DB
        chat.history.push({ role: 'user', parts: [{ text: prompt }] });
        chat.history.push({ role: 'model', parts: [{ text: modelResponseText }] });
        await chat.save();
        console.log("Chat history saved to DB.");

        // --- 5. Call Kokoro TTS Service ---
        let audioBase64Content = null; // Store base64 audio
        let ttsError = null;

        if (modelResponseText && modelResponseText.trim().length > 0) {
            try {
                console.log(`Sending text to Kokoro TTS: "${modelResponseText.substring(0, 50)}..."`);
                const ttsResponse = await axios.post(
                    KOKORO_TTS_URL,
                    { text: modelResponseText },
                    // Expect JSON response now
                    { responseType: 'json' }
                );

                // Check if the response has the expected audio content
                if (ttsResponse.data && ttsResponse.data.audioContent) {
                    audioBase64Content = ttsResponse.data.audioContent;
                    console.log(`Received Base64 audio data (length: ${audioBase64Content.length}) from Kokoro TTS.`);
                } else {
                    ttsError = 'Kokoro TTS response missing audioContent.';
                    console.error(ttsError, 'Response data:', ttsResponse.data);
                }

            } catch (error) {
                ttsError = `Failed to call Kokoro TTS service: ${error.message}`;
                console.error(ttsError, error.response?.data || '');
            }
        } else {
             console.log("No text content from model to synthesize.");
             ttsError = "No text content from model to synthesize.";
        }

        // --- 6. Send JSON Response to Client (always JSON now) ---
        console.log("Sending JSON response to client.");
        res.status(200).json({
            message: modelResponseText, // The original text response
            chatId: chat._id,
            audioContent: audioBase64Content, // Base64 audio string (null if TTS failed)
            ttsError: ttsError // Any error message from TTS process
        });

    } catch (error) {
        // ... (Existing Gemini/DB error handling) ...
         console.error('Error during Gemini call or DB save:', error);
        if (error.message?.includes('SAFETY')) { /* ... */ }
        if (error.message?.includes('API key not valid')) { /* ... */ }
        if (error.name === 'CastError') { /* ... */ }
        res.status(500).json({ message: 'Failed to process chat message' });
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