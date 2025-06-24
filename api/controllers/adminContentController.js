const Part1_1_Question = require("../models/content/Part1_1_QuestionModel");
const Part1_2_Set = require("../models/content/Part1_2_SetModel");
const Part2_Set = require("../models/content/Part2_SetModel");
const Part3_Topic = require("../models/content/Part3_TopicModel");
const { uploadToCDN } = require("../services/storageService");
const Word = require("../models/wordModel");

const DEFAULT_PROVIDER = "supabase";

// ... (uploadPart1_1 and uploadPart1_2 are unchanged) ...

/**
 * @desc    Upload content for a new Part 1.1 Question
 * @route   POST /api/admin/content/part1.1
 * @access  Private/Admin
 */
const uploadPart1_1 = async (req, res) => {
  const { questionText, provider = DEFAULT_PROVIDER } = req.body;
  const file = req.file;

  if (!questionText || !file) {
    return res.status(400).json({ message: "Question text and an audio file are required." });
  }

  try {
    console.log(`[Part 1.1] Using storage provider: ${provider}`);
    const audioUrl = await uploadToCDN(provider, file, "audio");

    const newQuestion = new Part1_1_Question({ questionText, audioUrl });
    await newQuestion.save();

    res
      .status(201)
      .json({ message: "Part 1.1 question uploaded successfully!", data: newQuestion });
  } catch (error) {
    console.error(`[Part 1.1] Upload Error:`, error);
    res.status(500).json({ message: error.message || "Server error during file upload." });
  }
};

/**
 * @desc    Upload content for a new Part 1.2 Set
 * @route   POST /api/admin/content/part1.2
 * @access  Private/Admin
 */
const uploadPart1_2 = async (req, res) => {
  const {
    question1,
    question2,
    question3,
    imageDescription,
    provider = DEFAULT_PROVIDER,
  } = req.body;
  const files = req.files;

  if (!question1 || !question2 || !question3 || !imageDescription) {
    return res.status(400).json({ message: "All text fields are required." });
  }
  if (!files.image1 || !files.image2 || !files.audio1 || !files.audio2 || !files.audio3) {
    return res.status(400).json({ message: "All 5 files (2 images, 3 audio) are required." });
  }

  try {
    console.log(`[Part 1.2] Using storage provider: ${provider}`);
    const [image1Url, image2Url, audio1Url, audio2Url, audio3Url] = await Promise.all([
      uploadToCDN(provider, files.image1[0], "images"),
      uploadToCDN(provider, files.image2[0], "images"),
      uploadToCDN(provider, files.audio1[0], "audio"),
      uploadToCDN(provider, files.audio2[0], "audio"),
      uploadToCDN(provider, files.audio3[0], "audio"),
    ]);

    const newSet = new Part1_2_Set({
      image1Url,
      image2Url,
      imageDescription,
      questions: [
        { text: question1, audioUrl: audio1Url },
        { text: question2, audioUrl: audio2Url },
        { text: question3, audioUrl: audio3Url },
      ],
      tags: ["admin-upload"],
    });

    await newSet.save();
    res.status(201).json({ message: "Part 1.2 content set uploaded successfully!", data: newSet });
  } catch (error) {
    console.error(`[Part 1.2] Upload Error:`, error);
    res.status(500).json({ message: error.message || "Server error during file upload." });
  }
};

/**
 * @desc    Upload content for a new Part 2 Set (Image and Description are optional)
 * @route   POST /api/admin/content/part2
 * @access  Private/Admin
 */
const uploadPart2 = async (req, res) => {
  // --- START OF MODIFIED LOGIC ---
  const {
    question1,
    question2,
    question3,
    imageDescription, // This is now optional
    provider = DEFAULT_PROVIDER,
  } = req.body;
  const files = req.files;

  // 1. Update validation: only questions and audio are mandatory.
  if (!question1 || !question2 || !question3) {
    return res.status(400).json({ message: "All three question text fields are required." });
  }
  if (!files.audio) {
    return res.status(400).json({ message: "A single combined audio file is required." });
  }

  try {
    console.log(`[Part 2] Using storage provider: ${provider}`);

    // 2. Conditionally upload image only if it exists
    let imageUrl = null;
    if (files.image && files.image[0]) {
      console.log("[Part 2] Image found, uploading...");
      imageUrl = await uploadToCDN(provider, files.image[0], "images");
    }

    // 3. Upload the mandatory audio file
    const audioUrl = await uploadToCDN(provider, files.audio[0], "audio");

    // 4. Create the new set, saving the data to the database
    const newSet = new Part2_Set({
      imageUrl, // This will be the URL or null
      imageDescription: imageDescription || null, // Save description or null
      questions: [
        { text: question1, audioUrl: audioUrl },
        { text: question2, audioUrl: audioUrl },
        { text: question3, audioUrl: audioUrl },
      ],
      tags: ["admin-upload"],
    });

    await newSet.save(); // The data is being saved correctly.

    res.status(201).json({ message: "Part 2 content set uploaded successfully!", data: newSet });
  } catch (error) {
    console.error(`[Part 2] Upload Error:`, error);
    res.status(500).json({ message: error.message || "Server error during file upload." });
  }
  // --- END OF MODIFIED LOGIC ---
};

/**
 * @desc    Upload content for a new Part 3 Topic (Image is optional)
 * @route   POST /api/admin/content/part3
 * @access  Private/Admin
 */
const uploadPart3 = async (req, res) => {
  // --- THIS LOGIC IS ALREADY CORRECT FOR OPTIONAL IMAGES ---
  const { topic, forPoints, againstPoints, provider = DEFAULT_PROVIDER } = req.body;
  const file = req.file; // From `upload.single('image')`, this will be undefined if no file is sent.

  if (!topic || !forPoints || !againstPoints) {
    return res.status(400).json({ message: "Topic, FOR points, and AGAINST points are required." });
  }

  try {
    console.log(`[Part 3] Using storage provider: ${provider}`);
    let imageUrl = null;
    // This `if (file)` check is the correct way to handle an optional single upload.
    if (file) {
      console.log("[Part 3] Image found, uploading...");
      imageUrl = await uploadToCDN(provider, file, "images");
    }

    const newTopic = new Part3_Topic({
      topic,
      forPoints: forPoints.split("\n").filter((p) => p.trim() !== ""),
      againstPoints: againstPoints.split("\n").filter((p) => p.trim() !== ""),
      imageUrl, // This will correctly be the URL or null.
      tags: ["admin-upload"],
    });

    await newTopic.save(); // The data is being saved correctly.

    res.status(201).json({ message: "Part 3 topic uploaded successfully!", data: newTopic });
  } catch (error) {
    console.error(`[Part 3] Upload Error:`, error);
    res.status(500).json({ message: error.message || "Server error during file upload." });
  }
};

// ... (uploadWordBankWord is unchanged) ...

/**
 * @desc    Uploads a new word to the Word Bank
 * @route   POST /api/admin/wordbank/add
 * @access  Private (Admin only)
 */
const uploadWordBankWord = async (req, res) => {
  const {
    word,
    translation,
    cefrLevel,
    topic,
    example1,
    example1Translation,
    example2,
    example2Translation,
  } = req.body;

  if (!word || !translation || !cefrLevel || !topic) {
    return res.status(400).json({
      message: "Please fill all required fields: word, translation, cefrLevel, and topic.",
    });
  }

  try {
    const newWord = new Word({
      word,
      translation,
      cefrLevel,
      topic,
      example1: example1 || null,
      example1Translation: example1Translation || null,
      example2: example2 || null,
      example2Translation: example2Translation || null,
    });

    const createdWord = await newWord.save();

    res.status(201).json({
      message: "Word successfully added to the Word Bank.",
      word: createdWord,
    });
  } catch (error) {
    if (error.code === 11000) {
      return res.status(409).json({ message: `Error: The word "${word}" already exists.` });
    }
    console.error("Word Bank Upload Error:", error);
    res.status(500).json({ message: "Server error while adding the word." });
  }
};

module.exports = {
  uploadPart1_1,
  uploadPart1_2,
  uploadPart2,
  uploadPart3,
  uploadWordBankWord,
};
