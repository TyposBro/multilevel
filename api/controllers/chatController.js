// {PATH_TO_PROJECT}/api/controllers/chatController.js
const Chat = require("../models/ChatModel");
const { GoogleGenerativeAI } = require("@google/generative-ai");
const axios = require("axios");
require("dotenv").config();

// --- Gemini Setup ---
if (!process.env.GEMINI_API_KEY) {
  console.error("FATAL ERROR: GEMINI_API_KEY is not set in .env file.");
  process.exit(1);
}
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" }); // Or your preferred streaming model

// --- Kokoro Preprocessing Service URL ---
const KOKORO_PREPROCESS_URL =
  process.env.KOKORO_PREPROCESS_URL || "http://localhost:8000/preprocess";
const DEFAULT_KOKORO_LANG_CODE = process.env.DEFAULT_KOKORO_LANG_CODE || "b";
const DEFAULT_KOKORO_CONFIG_KEY = process.env.DEFAULT_KOKORO_CONFIG_KEY || "hexgrad/Kokoro-82M";

// Helper to send SSE data
function sendSseChunk(res, eventName, data) {
  if (res.writableEnded) {
    console.warn(
      `[SSE Helper] Attempted to write to an already ended SSE stream. Event: ${eventName}`
    );
    return;
  }
  try {
    res.write(`event: ${eventName}\n`);
    res.write(`data: ${JSON.stringify(data)}\n\n`);
  } catch (e) {
    console.error(`[SSE Helper] Error writing to SSE stream: ${e.message}`);
    // Potentially end the stream if writing fails critically
    if (!res.writableEnded) {
      res.end();
    }
  }
}

// Simple sentence tokenizer (customize as needed)
const sentenceTerminators = /[.!?\n]/; // Include newline as a terminator

// @desc    Create a new empty chat session for the user
// @route   POST /api/chat/
// @access  Private
const createNewChat = async (req, res) => {
  const userId = req.user._id;
  const { title } = req.body;

  try {
    const newChat = new Chat({
      userId,
      history: [],
      title: title || `Chat started ${new Date().toLocaleDateString()}`,
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
    const chats = await Chat.find({ userId })
      .select("_id title createdAt updatedAt")
      .sort({ updatedAt: -1 });
    res.json({ chats });
  } catch (error) {
    console.error("Error fetching user chats:", error);
    res.status(500).json({ message: "Failed to retrieve chat list" });
  }
};

// @desc    Send a message within a specific chat and stream its response (sentence by sentence for Kokoro)
// @route   POST /api/chat/:chatId/message
// @access  Private
const sendMessage = async (req, res) => {
  const { prompt, lang_code, config_key } = req.body;
  const { chatId } = req.params;
  const userId = req.user._id; // Assuming protect middleware adds user to req

  console.log(
    `[${chatId}] sendMessage called with prompt: "${prompt ? prompt.substring(0, 30) : "N/A"}..."`
  );

  // --- Initial Validations (can send JSON error before SSE setup) ---
  if (!prompt) {
    console.error(`[${chatId}] Validation Error: Prompt is required.`);
    return res.status(400).json({ message: "Prompt is required" });
  }
  if (!chatId) {
    console.error(`[${chatId}] Validation Error: Chat ID is required.`);
    return res.status(400).json({ message: "Chat ID is required" });
  }

  let chat;
  try {
    chat = await Chat.findOne({ _id: chatId, userId: userId });
    if (!chat) {
      console.log(`[${chatId}] Chat not found or permission denied for user ${userId}.`);
      return res.status(404).json({ message: "Chat not found or permission denied." });
    }
  } catch (dbError) {
    console.error(`[${chatId}] Database error finding chat:`, dbError);
    if (dbError.name === "CastError" && dbError.kind === "ObjectId") {
      // More specific CastError check
      return res.status(400).json({ message: "Invalid Chat ID format." });
    }
    return res.status(500).json({ message: "Error accessing chat data." });
  }

  // --- If chat is found, THEN set up SSE ---
  console.log(`[${chatId}] Chat found for user ${userId}. Setting up SSE response.`);
  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("Connection", "keep-alive");
  // res.flushHeaders(); // Option 1: Flush immediately
  res.write(": SSE connection initiated\n\n"); // Option 2: Write a comment to flush

  let fullModelResponseText = "";
  let sentenceBuffer = "";

  const processSentenceBuffer = async (textToProcess) => {
    if (res.writableEnded || !textToProcess || textToProcess.trim().length === 0) {
      return;
    }
    console.log(
      `[${chatId}] Processing sentence for Kokoro: "${textToProcess.substring(0, 50)}..."`
    );
    try {
      const preprocessResponse = await axios.post(
        KOKORO_PREPROCESS_URL,
        {
          text: textToProcess,
          lang_code: lang_code || DEFAULT_KOKORO_LANG_CODE,
          config_key: config_key || DEFAULT_KOKORO_CONFIG_KEY,
        },
        { responseType: "json", timeout: 7000 }
      );

      if (
        preprocessResponse.data &&
        preprocessResponse.data.results &&
        preprocessResponse.data.results.length > 0 &&
        preprocessResponse.data.results[0].input_ids
      ) {
        const preprocessedData = preprocessResponse.data.results[0];
        sendSseChunk(res, "input_ids_chunk", {
          sentence: textToProcess,
          input_ids: preprocessedData.input_ids[0], // Assuming input_ids is [[...]]
        });
        console.log(
          `[${chatId}] Kokoro preprocessed sentence, input_ids length: ${preprocessedData.input_ids[0]?.length}`
        );
      } else {
        console.warn(
          `[${chatId}] Kokoro did not return valid input_ids for sentence: "${textToProcess.substring(
            0,
            50
          )}..."`,
          preprocessResponse.data
        );
        sendSseChunk(res, "preprocess_warning", {
          message: "Sentence may not have TTS.",
          sentenceText: textToProcess.substring(0, 50),
        });
      }
    } catch (kokoroError) {
      console.error(
        `[${chatId}] Error calling Kokoro for sentence "${textToProcess.substring(0, 50)}...": ${
          kokoroError.message
        }`
      );
      sendSseChunk(res, "preprocess_error", {
        message: `TTS preprocessing failed for a sentence: ${kokoroError.message}`,
        sentenceText: textToProcess.substring(0, 50),
      });
    }
  };

  try {
    const geminiHistory = chat.history;
    const chatSession = model.startChat({ history: geminiHistory });

    console.log(
      `[${chatId}] Calling Gemini API for streaming with prompt: "${prompt.substring(0, 50)}..."`
    );
    const resultStream = await chatSession.sendMessageStream(prompt);

    for await (const chunk of resultStream.stream) {
      if (res.writableEnded) {
        console.log(`[${chatId}] Client disconnected during Gemini stream. Stopping.`);
        break;
      }
      if (chunk && chunk.candidates && chunk.candidates[0]) {
        const contentPart = chunk.candidates[0].content;
        if (contentPart && contentPart.parts && contentPart.parts[0]) {
          const textChunk = contentPart.parts[0].text;
          if (textChunk) {
            fullModelResponseText += textChunk;
            sendSseChunk(res, "text_chunk", { text: textChunk });

            sentenceBuffer += textChunk;
            let match;
            while ((match = sentenceTerminators.exec(sentenceBuffer)) !== null) {
              if (res.writableEnded) break;
              const sentence = sentenceBuffer.substring(0, match.index + 1).trim();
              sentenceBuffer = sentenceBuffer.substring(match.index + 1);
              if (sentence) {
                await processSentenceBuffer(sentence);
              }
            }
          }
        }
      }
    }
    if (res.writableEnded) {
      console.log(`[${chatId}] Stream ended due to client disconnection before Gemini completion.`);
      // No further processing if client disconnected
    } else {
      console.log(`[${chatId}] Gemini stream finished.`);
      // Process any remaining text in the buffer
      if (sentenceBuffer.trim()) {
        console.log(
          `[${chatId}] Processing remaining buffer: "${sentenceBuffer.substring(0, 50)}..."`
        );
        await processSentenceBuffer(sentenceBuffer.trim());
      }

      // Save full history to DB only if stream was not prematurely ended by client
      if (fullModelResponseText.trim().length > 0) {
        chat.history.push({ role: "user", parts: [{ text: prompt }] });
        chat.history.push({ role: "model", parts: [{ text: fullModelResponseText }] });
        await chat.save();
        console.log(`[${chatId}] Full chat history saved to DB.`);
      }
      sendSseChunk(res, "stream_end", {
        chatId: chat._id,
        message: "Stream completed successfully.",
      });
    }
  } catch (error) {
    // This catch is for errors *after* SSE setup has begun
    console.error(`[${chatId}] Error during Gemini streaming/Kokoro/DB save (SSE phase):`, error);
    let errorMessage = "Stream processing error";
    if (error.message?.includes("SAFETY")) {
      errorMessage = "Response stream blocked due to safety concerns.";
    }
    // Only send SSE error if stream hasn't already ended
    if (!res.writableEnded) {
      sendSseChunk(res, "error", { message: errorMessage, details: error.message });
    }
  } finally {
    if (!res.writableEnded) {
      console.log(`[${chatId}] Ending SSE response stream in finally block.`);
      res.end();
    } else {
      console.log(`[${chatId}] SSE response stream already ended (writableEnded is true).`);
    }
  }
};

// @desc    Get chat history for a specific chat session
// @route   GET /api/chat/:chatId/history
// @access  Private
const getChatHistory = async (req, res) => {
  const { chatId } = req.params;
  const userId = req.user._id;

  if (!chatId) {
    return res.status(400).json({ message: "Chat ID is required" });
  }
  try {
    const chat = await Chat.findOne({ _id: chatId, userId: userId });
    if (!chat) {
      return res.status(404).json({ message: "Chat not found or permission denied." });
    }
    res.json({
      chatId: chat._id,
      title: chat.title,
      history: chat.history,
      createdAt: chat.createdAt,
      updatedAt: chat.updatedAt,
    });
  } catch (error) {
    console.error(`[${chatId}] Error fetching chat history:`, error);
    if (error.name === "CastError" && error.kind === "ObjectId") {
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
    const chat = await Chat.findOne({ _id: chatId, userId: userId });
    if (!chat) {
      return res.status(404).json({ message: "Chat not found or permission denied." });
    }
    await Chat.deleteOne({ _id: chatId, userId: userId });
    res.json({ message: "Chat session deleted successfully.", chatId: chatId });
  } catch (error) {
    console.error(`[${chatId}] Error deleting chat:`, error);
    if (error.name === "CastError" && error.kind === "ObjectId") {
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
  const { title } = req.body;
  const userId = req.user._id;

  if (!chatId) {
    return res.status(400).json({ message: "Chat ID is required" });
  }
  if (!title || typeof title !== "string" || title.trim() === "") {
    return res.status(400).json({ message: "A valid title is required" });
  }
  try {
    const chat = await Chat.findOne({ _id: chatId, userId: userId });
    if (!chat) {
      return res.status(404).json({ message: "Chat not found or permission denied." });
    }
    chat.title = title.trim();
    const updatedChat = await chat.save();
    res.json({
      message: "Chat title updated successfully.",
      chatId: updatedChat._id,
      title: updatedChat.title,
      updatedAt: updatedChat.updatedAt,
    });
  } catch (error) {
    console.error(`[${chatId}] Error updating chat title:`, error);
    if (error.name === "CastError" && error.kind === "ObjectId") {
      return res.status(400).json({ message: "Invalid Chat ID format." });
    }
    if (error.name === "ValidationError") {
      return res.status(400).json({ message: error.message });
    }
    res.status(500).json({ message: "Failed to update chat title" });
  }
};

module.exports = {
  createNewChat,
  listUserChats,
  sendMessage,
  getChatHistory,
  deleteChat,
  updateChatTitle,
};
