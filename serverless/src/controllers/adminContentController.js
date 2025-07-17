// {PATH_TO_PROJECT}/src/controllers/adminContentController.js

import { db } from "../db/d1-client";
import { uploadToCDN } from "../services/storageService";

const DEFAULT_PROVIDER = "cloudflare";

// --- Generic CRUD Controller Factories ---

const listContent = (tableName) => async (c) => {
  try {
    const items = await db.listAllContent(c.env.DB, tableName);
    return c.json(items);
  } catch (error) {
    console.error(`[Admin] List ${tableName} Error:`, error);
    return c.json({ message: `Server error while fetching ${tableName}.` }, 500);
  }
};

const getContent = (tableName) => async (c) => {
  try {
    const { id } = c.req.param();
    const item = await db.getContentById(c.env.DB, tableName, id);
    if (!item) {
      return c.json({ message: "Content not found." }, 404);
    }
    return c.json(item);
  } catch (error) {
    console.error(`[Admin] Get ${tableName} Error:`, error);
    return c.json({ message: `Server error while fetching content.` }, 500);
  }
};

const deleteContent = (tableName) => async (c) => {
  try {
    const { id } = c.req.param();
    await db.deleteContent(c.env.DB, tableName, id);
    return c.body(null, 204); // No Content
  } catch (error) {
    console.error(`[Admin] Delete ${tableName} Error:`, error);
    return c.json({ message: `Server error while deleting content.` }, 500);
  }
};

// --- Create (Upload) Controllers ---
// ... uploadPart1_1, etc. are unchanged ...
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

export const uploadPart1_2 = async (c) => {
  try {
    const formData = await c.req.formData();
    const provider = formData.get("provider")?.toString() || DEFAULT_PROVIDER;

    const question1 = formData.get("question1");
    const question2 = formData.get("question2");
    const question3 = formData.get("question3");
    const imageDescription = formData.get("imageDescription");

    const image1 = formData.get("image1");
    const image2 = formData.get("image2");
    const audio1 = formData.get("audio1");
    const audio2 = formData.get("audio2");
    const audio3 = formData.get("audio3");

    if (!question1 || !question2 || !question3 || !imageDescription) {
      return c.json({ message: "All text fields are required." }, 400);
    }
    if (
      !(image1 instanceof File) ||
      !(image2 instanceof File) ||
      !(audio1 instanceof File) ||
      !(audio2 instanceof File) ||
      !(audio3 instanceof File)
    ) {
      return c.json({ message: "All 5 files (2 images, 3 audio) are required." }, 400);
    }

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

export const uploadPart2 = async (c) => {
  try {
    const formData = await c.req.formData();
    const provider = formData.get("provider")?.toString() || DEFAULT_PROVIDER;

    const question1 = formData.get("question1");
    const question2 = formData.get("question2");
    const question3 = formData.get("question3");
    const imageDescription = formData.get("imageDescription");
    const imageFile = formData.get("image");
    const audioFile = formData.get("audio");

    if (!question1 || !question2 || !question3) {
      return c.json({ message: "All three question text fields are required." }, 400);
    }
    if (!audioFile || !(audioFile instanceof File)) {
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

export const uploadPart3 = async (c) => {
  try {
    const formData = await c.req.formData();
    const provider = formData.get("provider")?.toString() || DEFAULT_PROVIDER;

    const topic = formData.get("topic");
    const forPoints = formData.get("forPoints");
    const againstPoints = formData.get("againstPoints");
    const imageFile = formData.get("image");

    if (!topic || !forPoints || !againstPoints) {
      return c.json({ message: "Topic, FOR points, and AGAINST points are required." }, 400);
    }

    let imageUrl = null;
    if (imageFile instanceof File) {
      imageUrl = await uploadToCDN(c, provider, imageFile, "images");
    }

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

export const createWordBankWord = async (c) => {
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

// --- Update Controllers ---

// A helper for simple updates (text fields only)
const updateTextContent = (tableName) => async (c) => {
  try {
    const { id } = c.req.param();
    const data = await c.req.json();
    const updatedItem = await db.updateContent(c.env.DB, tableName, id, data);
    if (!updatedItem) return c.json({ message: "Content not found" }, 404);
    return c.json(updatedItem);
  } catch (error) {
    console.error(`[Admin] Update ${tableName} Error:`, error);
    return c.json({ message: `Server error while updating content.` }, 500);
  }
};

// Specialized Update for Part 1.1 (simple file update)
export const updatePart1_1 = async (c) => {
  try {
    const { id } = c.req.param();
    const formData = await c.req.formData();
    const currentItem = await db.getContentById(c.env.DB, "content_part1_1", id);
    if (!currentItem) return c.json({ message: "Content not found" }, 404);

    const dataToUpdate = {
      questionText: formData.get("questionText") || currentItem.questionText,
      audioUrl: currentItem.audioUrl,
    };

    const audioFile = formData.get("audio");
    if (audioFile instanceof File && audioFile.size > 0) {
      dataToUpdate.audioUrl = await uploadToCDN(c, "cloudflare", audioFile, "audio");
    }

    const updatedItem = await db.updateContent(c.env.DB, "content_part1_1", id, dataToUpdate);
    return c.json(updatedItem);
  } catch (error) {
    console.error(`[Admin] Update Part 1.1 Error:`, error);
    return c.json({ message: `Server error while updating content.` }, 500);
  }
};

// Specialized Update for Part 2 (nested audio, single image)
export const updatePart2 = async (c) => {
  try {
    const { id } = c.req.param();
    const formData = await c.req.formData();
    const currentItem = await db.getContentById(c.env.DB, "content_part2", id);
    if (!currentItem) return c.json({ message: "Content not found" }, 404);

    let newAudioUrl = currentItem.questions[0].audioUrl;
    const audioFile = formData.get("audio");
    if (audioFile instanceof File && audioFile.size > 0) {
      newAudioUrl = await uploadToCDN(c, "cloudflare", audioFile, "audio");
    }

    let newImageUrl = currentItem.imageUrl;
    const imageFile = formData.get("image");
    if (imageFile instanceof File && imageFile.size > 0) {
      newImageUrl = await uploadToCDN(c, "cloudflare", imageFile, "images");
    }

    const dataToUpdate = {
      imageDescription: formData.get("imageDescription") || currentItem.imageDescription,
      imageUrl: newImageUrl,
      questions: JSON.stringify([
        { text: formData.get("question1"), audioUrl: newAudioUrl },
        { text: formData.get("question2"), audioUrl: newAudioUrl },
        { text: formData.get("question3"), audioUrl: newAudioUrl },
      ]),
    };

    const updatedItem = await db.updateContent(c.env.DB, "content_part2", id, dataToUpdate);
    return c.json(updatedItem);
  } catch (error) {
    console.error(`[Admin] Update Part 2 Error:`, error);
    return c.json({ message: `Server error while updating content.` }, 500);
  }
};

// --- Export specific instances of the generic CRUD controllers ---
export const listPart1_1 = listContent("content_part1_1");
export const getPart1_1 = getContent("content_part1_1");
export const deletePart1_1 = deleteContent("content_part1_1");

// For Part 1.2, an update is too complex for a generic form. A dedicated page would be better.
// We provide a text-only update for now.
export const listPart1_2 = listContent("content_part1_2");
export const getPart1_2 = getContent("content_part1_2");
export const updatePart1_2 = updateTextContent("content_part1_2");
export const deletePart1_2 = deleteContent("content_part1_2");

export const listPart2 = listContent("content_part2");
export const getPart2 = getContent("content_part2");
export const deletePart2 = deleteContent("content_part2");

export const listPart3 = listContent("content_part3");
export const getPart3 = getContent("content_part3");
export const updatePart3 = updateTextContent("content_part3"); // Assuming text-only for now
export const deletePart3 = deleteContent("content_part3");

export const listWordBankWords = listContent("words");
export const getWordBankWord = getContent("words");
export const updateWordBankWord = updateTextContent("words");
export const deleteWordBankWord = deleteContent("words");
