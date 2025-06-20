// {PATH_TO_PROJECT}/api/controllers/multilevelExamController.js

const MultilevelExamResult = require("../models/multilevelExamResultModel.js");
const { generateText, safeJsonParse } = require("../utils/gemini.js");

// Import content models
const Part1_1_Question = require("../models/content/Part1_1_QuestionModel");
const Part1_2_Set = require("../models/content/Part1_2_SetModel");
const Part2_Set = require("../models/content/Part2_SetModel");
const Part3_Topic = require("../models/content/Part3_TopicModel");

/**
 * @desc    Generate a new complete multilevel exam
 * @route   GET /api/exam/multilevel/new
 * @access  Private
 */
const generateNewExam = async (req, res) => {
  try {
    const p1_1_Promise = Part1_1_Question.aggregate([{ $sample: { size: 3 } }]);
    const p1_2_Promise = Part1_2_Set.aggregate([{ $sample: { size: 1 } }]);
    const p2_Promise = Part2_Set.aggregate([{ $sample: { size: 1 } }]);
    const p3_Promise = Part3_Topic.aggregate([{ $sample: { size: 1 } }]);
    const [part1_1, part1_2, part2, part3] = await Promise.all([
      p1_1_Promise,
      p1_2_Promise,
      p2_Promise,
      p3_Promise,
    ]);
    if (part1_1.length < 3 || !part1_2[0] || !part2[0] || !part3[0]) {
      return res
        .status(500)
        .json({ message: "Could not assemble a full exam. Not enough content in DB." });
    }
    res.status(200).json({ part1_1, part1_2: part1_2[0], part2: part2[0], part3: part3[0] });
  } catch (error) {
    console.error("Error generating new multilevel exam:", error);
    res.status(500).json({ message: "Server error while generating exam." });
  }
};

/**
 * @desc    Analyze a full multilevel exam transcript and save the result
 * @route   POST /api/exam/analyze
 * @access  Private
 */
const analyzeExam = async (req, res) => {
  const { transcript, examContentIds } = req.body; // Frontend sends full transcript and content IDs
  const userId = req.user._id;

  if (!transcript || transcript.length === 0) {
    return res.status(400).json({ message: "Transcript is required for analysis." });
  }

  try {
    const formattedTranscript = transcript.map((t) => `${t.speaker}: ${t.text}`).join("\n");

    const prompt = `
You are an expert examiner for a structured, multilevel English speaking test. The maximum score is 72.
Analyze the following speaking test transcript. The user's speech may be minimal or nonsensical; score it accordingly.

The exam has 4 parts:
- Part 1.1: 3 personal questions (12 points total)
- Part 1.2: Picture comparison (22 points total)
- Part 2: Single picture monologue (18 points total)
- Part 3: Argumentative monologue (20 points total)

Based on the transcript, provide a score and constructive feedback for each part. Calculate the final total score (out of 72).

TRANSCRIPT:
---
${formattedTranscript}
---

CRITICAL: Your entire response must be ONLY a single, valid JSON object using this exact structure, with no extra text or explanations.

{
  "totalScore": <number>,
  "feedbackBreakdown": [
    {
      "part": "Part 1.1",
      "score": <number>,
      "feedback": "Detailed feedback for Part 1.1 performance, focusing on fluency, relevance, and clarity for short questions."
    },
    {
      "part": "Part 1.2",
      "score": <number>,
      "feedback": "Detailed feedback for Part 1.2, focusing on description, comparison, and speculative language."
    },
    {
      "part": "Part 2",
      "score": <number>,
      "feedback": "Detailed feedback for Part 2, assessing the ability to structure a 2-minute monologue and develop ideas."
    },
    {
      "part": "Part 3",
      "score": <number>,
      "feedback": "Detailed feedback for Part 3, evaluating the construction of a balanced argument using the provided points."
    }
  ]
}
`;

    const responseText = await generateText(prompt);
    const analysisData = safeJsonParse(responseText);

    if (!analysisData || !analysisData.totalScore || !analysisData.feedbackBreakdown) {
      console.error("AI failed to generate a valid analysis JSON.", responseText);
      return res.status(500).json({ message: "AI failed to generate a valid analysis." });
    }

    const newExamResult = new ExamResult({
      userId,
      transcript,
      totalScore: analysisData.totalScore,
      feedbackBreakdown: analysisData.feedbackBreakdown,
      examContent: examContentIds, // Save the IDs of the content used
    });

    const savedResult = await newExamResult.save();
    res.status(201).json({ resultId: savedResult._id });
  } catch (error) {
    console.error("Error during exam analysis:", error);
    res.status(500).json({ message: "Server error during exam analysis." });
  }
};

// Add getExamHistory and getExamResultDetails, but for the Multilevel model
const getExamHistory = async (req, res) => {
  try {
    const history = await MultilevelExamResult.find({ userId: req.user._id })
      .select("_id totalScore createdAt")
      .sort({ createdAt: -1 });
    const historySummaries = history.map((item) => ({
      id: item._id,
      examDate: item.createdAt.getTime(),
      totalScore: item.totalScore,
    }));
    res.json({ history: historySummaries });
  } catch (error) {
    res.status(500).json({ message: "Server error fetching history." });
  }
};

const getExamResultDetails = async (req, res) => {
  try {
    const result = await MultilevelExamResult.findOne({
      _id: req.params.resultId,
      userId: req.user._id,
    });
    if (!result) return res.status(404).json({ message: "Result not found." });
    res.json(result);
  } catch (error) {
    res.status(500).json({ message: "Server error fetching result details." });
  }
};

module.exports = {
  generateNewExam,
  analyzeExam,
  getExamHistory,
  getExamResultDetails,
};
