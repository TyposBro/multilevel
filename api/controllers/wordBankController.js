const asyncHandler = require("express-async-handler");
const Word = require("../models/wordModel");

// @desc    Get all distinct CEFR levels
// @route   GET /api/wordbank/levels
// @access  Public
const getLevels = asyncHandler(async (req, res) => {
  const levels = await Word.distinct("cefrLevel");
  res.json(levels.sort()); // Sort them e.g., A1, A2, B1...
});

// @desc    Get all distinct topics for a given level
// @route   GET /api/wordbank/topics?level=B2
// @access  Public
const getTopics = asyncHandler(async (req, res) => {
  const { level } = req.query;
  if (!level) {
    res.status(400);
    throw new Error("Level query parameter is required");
  }
  const topics = await Word.distinct("topic", { cefrLevel: level });
  res.json(topics.sort());
});

// @desc    Get words for a given level and topic
// @route   GET /api/wordbank/words?level=B2&topic=Technology
// @access  Public
const getWords = asyncHandler(async (req, res) => {
  const { level, topic } = req.query;
  if (!level || !topic) {
    res.status(400);
    throw new Error("Level and topic query parameters are required");
  }
  const words = await Word.find({ cefrLevel: level, topic: topic }).select(
    "-createdAt -updatedAt -__v"
  );
  res.json(words);
});

module.exports = { getLevels, getTopics, getWords };
