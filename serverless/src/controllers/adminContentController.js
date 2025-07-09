// {PATH_TO_PROJECT}/src/controllers/adminContentController.js

import { db } from "../db/d1-client";
import { uploadToCDN } from "../services/storageService";

const DEFAULT_PROVIDER = "cloudflare";

/**
 * @desc    Upload content for a new Part 1.1 Question
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
    const newQuestion = await db.createContent(c.env.DB, "content_part1_1", {
      questionText,
      audioUrl,
      tags: JSON.stringify(["admin-upload"]),
    });

    return c.json({ message: "Part 1.1 question uploaded successfully!", data: newQuestion }, 201);
  } catch (error) {
    console.error(`[Part 1.1] Upload Error:`, error);
    return c.json({ message: error.message || "Server error during file upload." }, 500);
  }
};

/**
 * @desc    Upload content for a new Part 1.2 Set
 */
export const uploadPart1_2 = async (c) => {
  try {
    const formData = await c.req.formData();
    const provider = formData.get("provider")?.toString() || DEFAULT_PROVIDER;

    // Extract text fields
    const question1 = formData.get("question1");
    const question2 = formData.get("question2");
    const question3 = formData.get("question3");
    const imageDescription = formData.get("imageDescription");

    // Extract files
    const image1 = formData.get("image1");
    const image2 = formData.get("image2");
    const audio1 = formData.get("audio1");
    const audio2 = formData.get("audio2");
    const audio3 = formData.get("audio3");

    if (!question1 || !question2 || !question3 || !imageDescription) {
      return c.json({ message: "All text fields are required." }, 400);
    }
    if (!image1 || !image2 || !audio1 || !audio2 || !audio3) {
      return c.json({ message: "All 5 files (2 images, 3 audio) are required." }, 400);
    }

    // Upload all files in parallel
    const [image1Url, image2Url, audio1Url, audio2Url, audio3Url] = await Promise.all([
      uploadToCDN(c, provider, image1, "images"),
      uploadToCDN(c, provider, image2, "images"),
      uploadToCDN(c, provider, audio1, "audio"),
      uploadToCDN(c, provider, audio2, "audio"),
      uploadToCDN(c, provider, audio3, "audio"),
    ]);

    const newSet = await db.createContent(c.env.DB, "content_part1_2", {
      image1Url,
      image2Url,
      imageDescription,
      questions: JSON.stringify([
        { text: question1, audioUrl: audio1Url },
        { text: question2, audioUrl: audio2Url },
        { text: question3, audioUrl: audio3Url },
      ]),
      tags: JSON.stringify(["admin-upload"]),
    });

    return c.json({ message: "Part 1.2 content set uploaded successfully!", data: newSet }, 201);
  } catch (error) {
    console.error(`[Part 1.2] Upload Error:`, error);
    return c.json({ message: error.message || "Server error during file upload." }, 500);
  }
};

/**
 * @desc    Upload content for a new Part 2 Set
 */
export const uploadPart2 = async (c) => {
  try {
    const formData = await c.req.formData();
    const provider = formData.get("provider")?.toString() || DEFAULT_PROVIDER;

    const question1 = formData.get("question1");
    const question2 = formData.get("question2");
    const question3 = formData.get("question3");
    const imageDescription = formData.get("imageDescription"); // Optional
    const imageFile = formData.get("image"); // Optional
    const audioFile = formData.get("audio");

    if (!question1 || !question2 || !question3) {
      return c.json({ message: "All three question text fields are required." }, 400);
    }
    if (!(audioFile instanceof File)) {
      return c.json({ message: "A single combined audio file is required." }, 400);
    }

    let imageUrl = null;
    if (imageFile instanceof File) {
      imageUrl = await uploadToCDN(c, provider, imageFile, "images");
    }
    const audioUrl = await uploadToCDN(c, provider, audioFile, "audio");

    const newSet = await db.createContent(c.env.DB, "content_part2", {
      imageUrl,
      imageDescription: imageDescription || null,
      questions: JSON.stringify([
        { text: question1, audioUrl },
        { text: question2, audioUrl },
        { text: question3, audioUrl },
      ]),
      tags: JSON.stringify(["admin-upload"]),
    });

    return c.json({ message: "Part 2 content set uploaded successfully!", data: newSet }, 201);
  } catch (error) {
    console.error(`[Part 2] Upload Error:`, error);
    return c.json({ message: error.message || "Server error during file upload." }, 500);
  }
};

/**
 * @desc    Upload content for a new Part 3 Topic
 */
export const uploadPart3 = async (c) => {
  try {
    const formData = await c.req.formData();
    const provider = formData.get("provider")?.toString() || DEFAULT_PROVIDER;

    const topic = formData.get("topic");
    const forPoints = formData.get("forPoints");
    const againstPoints = formData.get("againstPoints");
    const imageFile = formData.get("image"); // Optional

    if (!topic || !forPoints || !againstPoints) {
      return c.json({ message: "Topic, FOR points, and AGAINST points are required." }, 400);
    }

    let imageUrl = null;
    if (imageFile instanceof File) {
      imageUrl = await uploadToCDN(c, provider, imageFile, "images");
    }

    // Convert newline-separated points into a JSON array of strings
    const forPointsArray = forPoints.split("\n").filter((p) => p.trim() !== "");
    const againstPointsArray = againstPoints.split("\n").filter((p) => p.trim() !== "");

    const newTopic = await db.createContent(c.env.DB, "content_part3", {
      topic,
      forPoints: JSON.stringify(forPointsArray),
      againstPoints: JSON.stringify(againstPointsArray),
      imageUrl,
      tags: JSON.stringify(["admin-upload"]),
    });

    return c.json({ message: "Part 3 topic uploaded successfully!", data: newTopic }, 201);
  } catch (error) {
    console.error(`[Part 3] Upload Error:`, error);
    return c.json({ message: error.message || "Server error during file upload." }, 500);
  }
};

/**
 * @desc    Uploads a new word to the Word Bank
 */
export const uploadWordBankWord = async (c) => {
  try {
    const formData = await c.req.formData();
    const wordData = Object.fromEntries(formData.entries());
    const { word, translation, cefrLevel, topic } = wordData;

    if (!word || !translation || !cefrLevel || !topic) {
      return c.json(
        { message: "Please fill all required fields: word, translation, cefrLevel, and topic." },
        400
      );
    }

    const createdWord = await db.createContent(c.env.DB, "words", {
      word,
      translation,
      cefrLevel,
      topic,
      example1: wordData.example1 || null,
      example1Translation: wordData.example1Translation || null,
      example2: wordData.example2 || null,
      example2Translation: wordData.example2Translation || null,
    });

    return c.json({ message: "Word successfully added to the Word Bank.", word: createdWord }, 201);
  } catch (error) {
    if (error.message?.includes("UNIQUE constraint failed")) {
      return c.json({ message: `Error: The word already exists.` }, 409);
    }
    console.error("Word Bank Upload Error:", error);
    return c.json({ message: "Server error while adding the word." }, 500);
  }
};
