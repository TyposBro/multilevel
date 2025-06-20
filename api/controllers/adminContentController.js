// {PATH_TO_PROJECT}/api/controllers/adminContentController.js

const Part1_2_Set = require("../models/content/Part1_2_SetModel");

// --- Placeholder for your CDN upload logic ---
// You will replace this with your actual Supabase/GCS SDK logic
const uploadToCDN = async (file) => {
  // Example for Supabase:
  // const { data, error } = await supabase.storage
  //   .from('exam-assets')
  //   .upload(`path/to/${Date.now()}-${file.originalname}`, file.buffer, {
  //     contentType: file.mimetype,
  //   });
  // if (error) throw error;
  // return supabase.storage.from('exam-assets').getPublicUrl(data.path).data.publicUrl;

  // For now, we'll return a placeholder URL
  console.log(`Uploading ${file.originalname} to CDN...`);
  return `https://your-cdn.com/uploads/${Date.now()}-${file.originalname}`;
};

/**
 * @desc    Upload content for a new Part 1.2 set
 * @route   POST /api/admin/content/part1.2
 * @access  Private/Admin
 */
const uploadPart1_2 = async (req, res) => {
  const { question1, question2, question3, imageDescription } = req.body;
  const files = req.files;

  // --- Validation ---
  if (!question1 || !question2 || !question3 || !imageDescription) {
    return res.status(400).json({ message: "All text fields are required." });
  }
  if (!files.image1 || !files.image2 || !files.audio1 || !files.audio2 || !files.audio3) {
    return res.status(400).json({ message: "All 5 files are required." });
  }

  try {
    // --- Upload all files to CDN in parallel ---
    const [image1Url, image2Url, audio1Url, audio2Url, audio3Url] = await Promise.all([
      uploadToCDN(files.image1[0]),
      uploadToCDN(files.image2[0]),
      uploadToCDN(files.audio1[0]),
      uploadToCDN(files.audio2[0]),
      uploadToCDN(files.audio3[0]),
    ]);

    // --- Create new document in MongoDB ---
    const newSet = new Part1_2_Set({
      image1Url,
      image2Url,
      imageDescription,
      questions: [
        { text: question1, audioUrl: audio1Url },
        { text: question2, audioUrl: audio2Url },
        { text: question3, audioUrl: audio3Url },
      ],
      tags: ["admin-upload"], // Optional tag
    });

    await newSet.save();

    res.status(201).json({ message: "Part 1.2 content uploaded successfully!", data: newSet });
  } catch (error) {
    console.error("Error uploading Part 1.2 content:", error);
    res.status(500).json({ message: "Server error during file upload." });
  }
};

module.exports = {
  uploadPart1_2,
};
