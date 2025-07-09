// {PATH_TO_PROJECT}/src/controllers/adminContentController.js
import { db } from "../db/d1-client";
import { uploadToCDN } from "../services/storageService";

const DEFAULT_PROVIDER = "cloudflare";

/**
 * @desc    Upload content for a new Part 1.1 Question
 * @route   POST /api/admin/content/part1.1
 * @access  Private/Admin
 */
export const uploadPart1_1 = async (c) => {
  try {
    const formData = await c.req.formData();
    const questionText = formData.get("questionText");
    const file = formData.get("audio");
    const provider = formData.get("provider")?.toString() || DEFAULT_PROVIDER;

    if (!questionText || !(file instanceof File)) {
      return c.json({ message: "Question text and an audio file are required." }, 400);
    }

    const audioUrl = await uploadToCDN(c, provider, file, "audio");

    // Use the generic `createContent` function from our DB client
    const newQuestion = await db.createContent(c.env.DB, "content_part1_1", {
      questionText,
      audioUrl,
      tags: ["admin-upload"],
    });

    return c.json({ message: "Part 1.1 question uploaded successfully!", data: newQuestion }, 201);
  } catch (error) {
    console.error(`[Part 1.1] Upload Error:`, error);
    return c.json({ message: error.message || "Server error during file upload." }, 500);
  }
};

// ... other content upload functions (uploadPart1_2, uploadPart2, etc.) would be refactored
// in a very similar way, parsing formData and calling db.createContent with the correct table name.

/**
 * @desc    Uploads a new word to the Word Bank
 * @route   POST /api/admin/wordbank/add
 * @access  Private (Admin only)
 */
export const uploadWordBankWord = async (c) => {
  try {
    const body = await c.req.json();
    const {
      word,
      translation,
      cefrLevel,
      topic,
      example1,
      example1Translation,
      example2,
      example2Translation,
    } = body;

    if (!word || !translation || !cefrLevel || !topic) {
      return c.json(
        { message: "Please fill all required fields: word, translation, cefrLevel, and topic." },
        400
      );
    }

    const newWordData = {
      word,
      translation,
      cefrLevel,
      topic,
      example1: example1 || null,
      example1Translation: example1Translation || null,
      example2: example2 || null,
      example2Translation: example2Translation || null,
    };

    const createdWord = await db.createContent(c.env.DB, "words", newWordData);
    return c.json({ message: "Word successfully added to the Word Bank.", word: createdWord }, 201);
  } catch (error) {
    // D1 unique constraint errors have a specific message
    if (error.message?.includes("UNIQUE constraint failed")) {
      return c.json({ message: `Error: The word already exists.` }, 409);
    }
    console.error("Word Bank Upload Error:", error);
    return c.json({ message: "Server error while adding the word." }, 500);
  }
};
