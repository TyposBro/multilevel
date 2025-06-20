const Part1_1_Question = require("../models/content/Part1_1_QuestionModel");
const Part1_2_Set = require("../models/content/Part1_2_SetModel");
const Part2_Set = require("../models/content/Part2_SetModel");
const Part3_Topic = require("../models/content/Part3_TopicModel");
const { uploadToCDN } = require("../services/storageService");

// Default provider if none is specified in the request body. Can be 'firebase' or 'supabase'.
const DEFAULT_PROVIDER = "supabase"; // Change this to 'firebase' if needed

/**
 * @desc    Upload content for a new Part 1.1 Question
 * @route   POST /api/admin/content/part1.1
 * @access  Private/Admin
 */
const uploadPart1_1 = async (req, res) => {
  const { questionText, provider = DEFAULT_PROVIDER } = req.body;
  const file = req.file; // From multer single upload: `upload.single('audio')`

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
 * @desc    Upload content for a new Part 2 Set
 * @route   POST /api/admin/content/part2
 * @access  Private/Admin
 */
const uploadPart2 = async (req, res) => {
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
  if (!files.image || !files.audio) {
    return res
      .status(400)
      .json({ message: "An image file and a single combined audio file are required." });
  }

  try {
    console.log(`[Part 2] Using storage provider: ${provider}`);
    const [imageUrl, audioUrl] = await Promise.all([
      uploadToCDN(provider, files.image[0], "images"),
      uploadToCDN(provider, files.audio[0], "audio"),
    ]);

    const newSet = new Part2_Set({
      imageUrl,
      imageDescription,
      questions: [
        { text: question1, audioUrl: audioUrl }, // All questions point to the same combined audio URL
        { text: question2, audioUrl: audioUrl },
        { text: question3, audioUrl: audioUrl },
      ],
      tags: ["admin-upload"],
    });

    await newSet.save();
    res.status(201).json({ message: "Part 2 content set uploaded successfully!", data: newSet });
  } catch (error) {
    console.error(`[Part 2] Upload Error:`, error);
    res.status(500).json({ message: error.message || "Server error during file upload." });
  }
};

/**
 * @desc    Upload content for a new Part 3 Topic
 * @route   POST /api/admin/content/part3
 * @access  Private/Admin
 */
const uploadPart3 = async (req, res) => {
  // For Part 3, text areas send text with newline characters. We need to split them.
  const { topic, forPoints, againstPoints, provider = DEFAULT_PROVIDER } = req.body;
  const file = req.file; // Optional image file `upload.single('image')`

  if (!topic || !forPoints || !againstPoints) {
    return res.status(400).json({ message: "Topic, FOR points, and AGAINST points are required." });
  }

  try {
    console.log(`[Part 3] Using storage provider: ${provider}`);
    let imageUrl = null;
    if (file) {
      imageUrl = await uploadToCDN(provider, file, "images");
    }

    const newTopic = new Part3_Topic({
      topic,
      // Split the newline-separated strings from the textarea into arrays
      forPoints: forPoints.split("\n").filter((p) => p.trim() !== ""),
      againstPoints: againstPoints.split("\n").filter((p) => p.trim() !== ""),
      imageUrl, // This will be null if no file was uploaded
      tags: ["admin-upload"],
    });

    await newTopic.save();
    res.status(201).json({ message: "Part 3 topic uploaded successfully!", data: newTopic });
  } catch (error) {
    console.error(`[Part 3] Upload Error:`, error);
    res.status(500).json({ message: error.message || "Server error during file upload." });
  }
};

module.exports = {
  uploadPart1_1,
  uploadPart1_2,
  uploadPart2,
  uploadPart3,
};
