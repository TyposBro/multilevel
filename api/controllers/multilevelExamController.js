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

// --- Helper object to define prompts and scoring for each part ---
const partAnalysisConfig = {
  P1_1: {
    maxScore: 12,
    partName: "Part 1.1",
    promptFocus:
      "Detailed feedback for Part 1.1 performance, focusing on fluency, relevance, and clarity for short personal questions.",
  },
  P1_2: {
    maxScore: 22,
    partName: "Part 1.2",
    promptFocus:
      "Detailed feedback for Part 1.2, focusing on description, comparison, and speculative language related to the pictures.",
  },
  P2: {
    maxScore: 18,
    partName: "Part 2",
    promptFocus:
      "Detailed feedback for Part 2, assessing the ability to structure a 2-minute monologue and develop ideas based on the cue card.",
  },
  P3: {
    maxScore: 20,
    partName: "Part 3",
    promptFocus:
      "Detailed feedback for Part 3, evaluating the construction of a balanced argument using the provided for/against points.",
  },
};

/**
 * @desc    Analyze a full or partial multilevel exam transcript and save the result
 * @route   POST /api/exam/multilevel/analyze
 * @access  Private
 */
const analyzeExam = async (req, res) => {
  // Destructure practicePart from the body
  const { transcript, examContentIds, practicePart } = req.body;
  const userId = req.user._id;

  if (!transcript || transcript.length === 0) {
    return res.status(400).json({ message: "Transcript is required for analysis." });
  }

  try {
    const formattedTranscript = transcript.map((t) => `${t.speaker}: ${t.text}`).join("\n");
    let prompt;
    const isSinglePartPractice = !!practicePart && partAnalysisConfig[practicePart];

    if (isSinglePartPractice) {
      // --- PROMPT FOR SINGLE PART PRACTICE ---
      const config = partAnalysisConfig[practicePart];
      prompt = `
You are an expert examiner for a structured, multilevel English speaking test.
The user is practicing a single part of the exam: ${config.partName}. The maximum score for this part is ${config.maxScore}.
Analyze the following speaking test transcript. The user's speech may be minimal or nonsensical; score it accordingly.

TRANSCRIPT:
---
${formattedTranscript}
---

CRITICAL: Your entire response must be ONLY a single, valid JSON object using this exact structure, with no extra text or explanations.

{
  "part": "${config.partName}",
  "score": <number>,
  "feedback": "${config.promptFocus}"
}
`;
    } else {
      // --- PROMPT FOR FULL EXAM (Unchanged) ---
      prompt = `
You are an expert examiner for a structured, multilevel English speaking test. The maximum score is 72.
Analyze the following speaking test transcript. The user's speech may be minimal or nonsensical; score it accordingly.

The exam has 4 parts:
- Part 1.1: 3 personal questions (${partAnalysisConfig.P1_1.maxScore} points total)
- Part 1.2: Picture comparison (${partAnalysisConfig.P1_2.maxScore} points total)
- Part 2: Single picture monologue (${partAnalysisConfig.P2.maxScore} points total)
- Part 3: Argumentative monologue (${partAnalysisConfig.P3.maxScore} points total)

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
      "feedback": "${partAnalysisConfig.P1_1.promptFocus}"
    },
    {
      "part": "Part 1.2",
      "score": <number>,
      "feedback": "${partAnalysisConfig.P1_2.promptFocus}"
    },
    {
      "part": "Part 2",
      "score": <number>,
      "feedback": "${partAnalysisConfig.P2.promptFocus}"
    },
    {
      "part": "Part 3",
      "score": <number>,
      "feedback": "${partAnalysisConfig.P3.promptFocus}"
    }
  ]
}
`;
    }

    const responseText = await generateText(prompt);
    const analysisData = safeJsonParse(responseText);

    // --- Process and Save the Result ---
    let totalScore;
    let feedbackBreakdown;

    if (isSinglePartPractice) {
      // For single part, the AI returns one object. We wrap it in an array.
      if (!analysisData || typeof analysisData.score === "undefined" || !analysisData.feedback) {
        console.error("AI failed to generate a valid single-part analysis JSON.", responseText);
        return res.status(500).json({ message: "AI failed to generate a valid analysis." });
      }
      totalScore = analysisData.score;
      feedbackBreakdown = [analysisData]; // Wrap the single object in an array for the schema
    } else {
      // For full exam, the AI returns the full structure.
      if (!analysisData || !analysisData.totalScore || !analysisData.feedbackBreakdown) {
        console.error("AI failed to generate a valid full-exam analysis JSON.", responseText);
        return res.status(500).json({ message: "AI failed to generate a valid analysis." });
      }
      totalScore = analysisData.totalScore;
      feedbackBreakdown = analysisData.feedbackBreakdown;
    }

    const newExamResult = new MultilevelExamResult({
      userId,
      transcript,
      totalScore,
      feedbackBreakdown,
      examContent: examContentIds,
      // NEW: Store which part was practiced, or mark as "FULL"
      practicedPart: isSinglePartPractice ? practicePart : "FULL",
    });

    const savedResult = await newExamResult.save();
    res.status(201).json({ resultId: savedResult._id });
  } catch (error) {
    console.error("Error during exam analysis:", error);
    res.status(500).json({ message: "Server error during exam analysis." });
  }
};

/**
 * @desc    Get the user's exam history for Multilevel
 * @route   GET /api/exam/multilevel/history
 * @access  Private
 */
const getExamHistory = async (req, res) => {
  try {
    const history = await MultilevelExamResult.find({ userId: req.user._id })
      .select("_id totalScore createdAt practicedPart") // Include practicedPart
      .sort({ createdAt: -1 });

    const historySummaries = history.map((item) => ({
      id: item._id,
      examDate: item.createdAt.getTime(),
      totalScore: item.totalScore,
      // You could potentially use `practicedPart` on the frontend list view
    }));

    res.json({ history: historySummaries });
  } catch (error) {
    res.status(500).json({ message: "Server error fetching history." });
  }
};

/**
 * @desc    Get the details of a single Multilevel exam result
 * @route   GET /api/exam/multilevel/result/:resultId
 * @access  Private
 */
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