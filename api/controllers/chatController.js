// controllers/chatController.js
const Chat = require("../models/Chat");
const { GoogleGenerativeAI } = require("@google/generative-ai");
const axios = require("axios");
require("dotenv").config();

// --- Gemini Setup --- (Keep as is)
if (!process.env.GEMINI_API_KEY) {
  console.error("FATAL ERROR: GEMINI_API_KEY is not set in .env file.");
  process.exit(1);
}
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });

// --- Kokoro Preprocessing Service URL ---
// This should point to your Python FastAPI service (e.g., running on port 8000)
const KOKORO_PREPROCESS_URL =
  process.env.KOKORO_PREPROCESS_URL || "http://localhost:8000/preprocess";
const DEFAULT_KOKORO_LANG_CODE = process.env.DEFAULT_KOKORO_LANG_CODE || "b"; // Or 'j', 'z', etc.
const DEFAULT_KOKORO_CONFIG_KEY = process.env.DEFAULT_KOKORO_CONFIG_KEY || "hexgrad/Kokoro-82M"; // Match your Python config

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
      message: "New chat created successfully.",
      chatId: savedChat._id,
      title: savedChat.title,
      createdAt: savedChat.createdAt,
    });
  } catch (error) {
    console.error("Error creating new chat:", error);
    res.status(500).json({ message: "Failed to create new chat session" });
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
      .select("_id title createdAt updatedAt") // Only send summary info
      .sort({ updatedAt: -1 }); // Show most recent first

    res.json({ chats });
  } catch (error) {
    console.error("Error fetching user chats:", error);
    res.status(500).json({ message: "Failed to retrieve chat list" });
  }
};

// @desc    Send a message within a specific chat and update its history
// @route   POST /api/chat/:chatId/message
// @access  Private
const sendMessage = async (req, res) => {
  const { prompt, lang_code, config_key } = req.body; // Allow lang_code and config_key from request
  const { chatId } = req.params;
  const userId = req.user._id;

  if (!prompt) return res.status(400).json({ message: "Prompt is required" });
  if (!chatId) return res.status(400).json({ message: "Chat ID is required" });

  try {
    // 1. Find Chat & Verify Ownership
    const chat = await Chat.findOne({ _id: chatId, userId: userId });
    if (!chat) return res.status(404).json({ message: "Chat not found or permission denied." });

    // 2. Prepare History for Gemini (if your Chat model stores it in Gemini format directly)
    // If not, you'll need a mapping like:
    // const geminiHistory = chat.history.map(msg => ({
    //    role: msg.role,
    //    parts: [{ text: msg.parts[0].text }] // Assuming simple text parts
    // }));
    const geminiHistory = chat.history; // Assuming chat.history is already in {role, parts} format

    // 3. Call Gemini
    console.log("Calling Gemini API...");
    const chatSession = model.startChat({ history: geminiHistory });
    const result = await chatSession.sendMessage(prompt);
    const response = await result.response;
    const modelResponseText = response.text();
    console.log("Received response from Gemini.");

    // 4. Update Chat History in DB
    chat.history.push({ role: "user", parts: [{ text: prompt }] });
    chat.history.push({ role: "model", parts: [{ text: modelResponseText }] });
    await chat.save();
    console.log("Chat history saved to DB.");

    // --- 5. Call Kokoro Preprocessing Service ---
    let preprocessedData = null;
    let preprocessingError = null;
    const kokoroLangCodeToUse = lang_code || DEFAULT_KOKORO_LANG_CODE;
    const kokoroConfigKeyToUse = config_key || DEFAULT_KOKORO_CONFIG_KEY;

    if (modelResponseText && modelResponseText.trim().length > 0) {
      try {
        console.log(
          `Sending text to Kokoro Preprocess (lang: ${kokoroLangCodeToUse}, config: ${kokoroConfigKeyToUse}): "${modelResponseText.substring(
            0,
            50
          )}..."`
        );
        const preprocessResponse = await axios.post(
          KOKORO_PREPROCESS_URL,
          {
            text: modelResponseText,
            lang_code: kokoroLangCodeToUse,
            config_key: kokoroConfigKeyToUse,
            // split_pattern: "\\n+" // Optional: if you want specific splitting
          },
          { responseType: "json" }
        );

        if (
          preprocessResponse.data &&
          preprocessResponse.data.results &&
          preprocessResponse.data.results.length > 0
        ) {
          // Assuming we are interested in the first result if multiple are returned (e.g. due to splitting)
          // Or you might want to concatenate phonemes/input_ids if that makes sense for your use case
          preprocessedData = {
            phonemes: preprocessResponse.data.results[0].phonemes,
            input_ids: preprocessResponse.data.results[0].input_ids,
            graphemes: preprocessResponse.data.results[0].graphemes,
            lang_code_used: preprocessResponse.data.lang_code_used,
            config_key_used: preprocessResponse.data.config_key_used,
          };
          console.log(
            `Received preprocessed data from Kokoro: Phonemes length ${preprocessedData.phonemes?.length}, Input IDs count ${preprocessedData.input_ids?.[0]?.length}`
          );
        } else {
          preprocessingError =
            "Kokoro Preprocess response missing results or results array is empty.";
          console.error(preprocessingError, "Response data:", preprocessResponse.data);
        }
      } catch (error) {
        preprocessingError = `Failed to call Kokoro Preprocess service: ${error.message}`;
        console.error(
          preprocessingError,
          error.response?.data || (error.isAxiosError ? error.toJSON() : "")
        );
      }
    } else {
      console.log("No text content from model to preprocess.");
      preprocessingError = "No text content from model to preprocess.";
    }

    // --- 6. Send JSON Response to Client ---
    console.log("Sending JSON response to client.");
    res.status(200).json({
      message: modelResponseText, // The original text response from Gemini
      chatId: chat._id,
      preprocessed: preprocessedData, // Contains phonemes, input_ids, etc.
      preprocessingError: preprocessingError, // Any error message from preprocessing
    });
  } catch (error) {
    console.error("Error during Gemini call, DB save, or other processing:", error);
    let errorMessage = "Failed to process chat message";
    let statusCode = 500;

    if (error.message?.includes("SAFETY")) {
      errorMessage = "Response blocked due to safety concerns from the generative model.";
      statusCode = 400; // Or a more specific code if available
    } else if (error.message?.includes("API key not valid")) {
      errorMessage = "Generative model API key is invalid or missing.";
      statusCode = 500; // Internal server configuration issue
    } else if (error.name === "CastError" && error.kind === "ObjectId") {
      errorMessage = "Invalid Chat ID format.";
      statusCode = 400;
    }
    // Add more specific error handling if needed

    res.status(statusCode).json({ message: errorMessage, details: error.message });
  }
};

// @desc    Get chat history for a specific chat session
// @route   GET /api/chat/:chatId/history
// @access  Private
const getChatHistory = async (req, res) => {
  const { chatId } = req.params; // Get chatId from URL parameters
  const userId = req.user._id;

  if (!chatId) {
    return res.status(400).json({ message: "Chat ID is required" });
  }

  try {
    // Find the specific chat AND verify ownership
    const chat = await Chat.findOne({ _id: chatId, userId: userId });

    if (!chat) {
      return res
        .status(404)
        .json({ message: "Chat session not found or you do not have permission to access it." });
    }

    res.json({
      chatId: chat._id,
      title: chat.title,
      history: chat.history,
      createdAt: chat.createdAt,
      updatedAt: chat.updatedAt,
    });
  } catch (error) {
    console.error("Error fetching specific chat history:", error);
    if (error.name === "CastError") {
      return res.status(400).json({ message: "Invalid Chat ID format." });
    }
    res.status(500).json({ message: "Failed to retrieve chat history" });
  }
};

// @desc    Delete a specific chat session
// @route   DELETE /api/chat/:chatId
// @access  Private
const deleteChat = async (req, res) => {
  const { chatId } = req.params;
  const userId = req.user._id;

  if (!chatId) {
    return res.status(400).json({ message: "Chat ID is required" });
  }

  try {
    // Find the chat to ensure it exists and belongs to the user before deleting
    const chat = await Chat.findOne({ _id: chatId, userId: userId });

    if (!chat) {
      // Use 404 even if the chat exists but belongs to another user for security
      return res
        .status(404)
        .json({ message: "Chat session not found or you do not have permission." });
    }

    // Perform the deletion
    await Chat.deleteOne({ _id: chatId, userId: userId }); // Redundant userId check, but safe

    res.json({ message: "Chat session deleted successfully.", chatId: chatId });
  } catch (error) {
    console.error("Error deleting chat session:", error);
    if (error.name === "CastError") {
      return res.status(400).json({ message: "Invalid Chat ID format." });
    }
    res.status(500).json({ message: "Failed to delete chat session" });
  }
};

// @desc    Update the title of a specific chat session
// @route   PUT /api/chat/:chatId/title
// @access  Private
const updateChatTitle = async (req, res) => {
  const { chatId } = req.params;
  const { title } = req.body; // Get the new title from the request body
  const userId = req.user._id;

  if (!chatId) {
    return res.status(400).json({ message: "Chat ID is required" });
  }
  if (!title || typeof title !== "string" || title.trim() === "") {
    return res.status(400).json({ message: "A valid title is required" });
  }

  try {
    // Find the chat, ensuring it belongs to the logged-in user
    const chat = await Chat.findOne({ _id: chatId, userId: userId });

    if (!chat) {
      return res
        .status(404)
        .json({ message: "Chat session not found or you do not have permission." });
    }

    // Update the title
    chat.title = title.trim();
    const updatedChat = await chat.save(); // Save the changes (also updates 'updatedAt')

    res.json({
      message: "Chat title updated successfully.",
      chatId: updatedChat._id,
      title: updatedChat.title,
      updatedAt: updatedChat.updatedAt,
    });
  } catch (error) {
    console.error("Error updating chat title:", error);
    if (error.name === "CastError") {
      return res.status(400).json({ message: "Invalid Chat ID format." });
    }
    if (error.name === "ValidationError") {
      // In case future validation is added to title
      return res.status(400).json({ message: error.message });
    }
    res.status(500).json({ message: "Failed to update chat title" });
  }
};

// --- Make sure to export all functions ---
module.exports = {
  createNewChat,
  listUserChats,
  sendMessage,
  getChatHistory,
  deleteChat,
  updateChatTitle,
};
