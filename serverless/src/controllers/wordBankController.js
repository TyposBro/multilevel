// {PATH_TO_PROJECT}/src/controllers/wordBankController.js

import { db } from "../db/d1-client";

/**
 * @desc    Get all distinct CEFR levels
 * @route   GET /api/wordbank/levels
 * @access  Public
 */
export const getLevels = async (c) => {
  try {
    const levels = await db.getWordBankLevels(c.env.DB);
    return c.json(levels);
  } catch (error) {
    console.error("Error fetching word bank levels:", error);
    return c.json({ message: "Server error fetching levels." }, 500);
  }
};

/**
 * @desc    Get all distinct topics for a given level
 * @route   GET /api/wordbank/topics?level=B2
 * @access  Public
 */
export const getTopics = async (c) => {
  try {
    const level = c.req.query("level");
    if (!level) {
      return c.json({ message: "Level query parameter is required" }, 400);
    }
    const topics = await db.getWordBankTopics(c.env.DB, level);
    return c.json(topics);
  } catch (error) {
    console.error("Error fetching word bank topics:", error);
    return c.json({ message: "Server error fetching topics." }, 500);
  }
};

/**
 * @desc    Get words for a given level and topic
 * @route   GET /api/wordbank/words?level=B2&topic=Technology
 * @access  Public
 */
export const getWords = async (c) => {
  try {
    const level = c.req.query("level");
    const topic = c.req.query("topic");
    if (!level || !topic) {
      return c.json({ message: "Level and topic query parameters are required" }, 400);
    }
    const words = await db.getWordBankWords(c.env.DB, level, topic);
    return c.json(words);
  } catch (error) {
    console.error("Error fetching word bank words:", error);
    return c.json({ message: "Server error fetching words." }, 500);
  }
};
