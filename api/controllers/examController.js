// controllers/examController.js
const ExamResult = require("../models/ExamResult");
const { GoogleGenerativeAI } = require("@google/generative-ai");
require("dotenv").config();

// --- Gemini Setup ---
if (!process.env.GEMINI_API_KEY) {
  throw new Error("FATAL ERROR: GEMINI_API_KEY is not set.");
}
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });

// Helper to safely parse JSON from LLM response
const safeJsonParse = (text) => {
  try {
    // LLMs sometimes wrap JSON in ```json ... ```, so we extract it.
    const match = text.match(/```json\n([\s\S]*?)\n```/);
    if (match && match[1]) {
      return JSON.parse(match[1]);
    }
    return JSON.parse(text); // Try parsing directly
  } catch (e) {
    console.error("Failed to parse JSON from LLM:", text, e);
    return null;
  }
};

/**
 * @desc    Start a new mock exam
 * @route   POST /api/exam/start
 * @access  Private
 */
const startExam = async (req, res) => {
  try {
    const prompt = `You are an IELTS examiner. Begin a new speaking test. Your first two lines should be to state your name and ask for the user's name. Respond ONLY with a valid JSON object with the structure: {"examiner_line": "Your full response here", "next_part": 1, "cue_card": null, "is_final_question": false, "input_ids": []}`;

    const result = await model.generateContent(prompt);
    const responseText = await result.response.text();
    const data = safeJsonParse(responseText);

    if (!data) {
      return res.status(500).json({ message: "AI failed to generate a valid starting question." });
    }
    res.status(200).json(data);
  } catch (error) {
    console.error("Error starting exam:", error);
    res.status(500).json({ message: "Server error while starting exam." });
  }
};

/**
 * @desc    Handle the next step in an exam
 * @route   POST /api/exam/step
 * @access  Private
 */
const handleExamStep = async (req, res) => {
  const { part, userInput, transcriptContext } = req.body;

  try {
    const prompt = `
      You are an IELTS examiner in a mock speaking test. The full conversation so far is:
      ---
      ${transcriptContext}
      ---
      The user just said: "${userInput}". We are currently in Part ${part} of the exam. 
      Based on the rules of IELTS, provide your next line.

      - If in Part 1, ask another introductory question. Decide if it's time to move to Part 2. If you decide to move to part 2, your examiner_line should be the standard transition line and you must provide a cue_card.
      - If in Part 2, the user has just finished their 2-minute talk. Ask one brief follow-up question. Your next_part should be 3.
      - If in Part 3, continue the abstract discussion based on the Part 2 topic. Decide if it is the final question of the exam and set is_final_question accordingly.

      Respond ONLY with a valid JSON object with the following structure: 
      {"examiner_line": "Your question here", "next_part": <number>, "cue_card": {"topic": "...", "points": ["...", "..."]} or null, "is_final_question": <boolean>, "input_ids": []}
    `;

    const result = await model.generateContent(prompt);
    const responseText = await result.response.text();
    const data = safeJsonParse(responseText);

    if (!data) {
      return res.status(500).json({ message: "AI failed to generate a valid next step." });
    }
    res.status(200).json(data);
  } catch (error) {
    console.error("Error handling exam step:", error);
    res.status(500).json({ message: "Server error during exam step." });
  }
};

/**
 * @desc    Analyze a full exam transcript and save the result
 * @route   POST /api/exam/analyze
 * @access  Private
 */
const analyzeExam = async (req, res) => {
  const { transcript } = req.body;
  const userId = req.user._id;

  try {
    const formattedTranscript = transcript.map((t) => `${t.speaker}: ${t.text}`).join("\n");
    const prompt = `
      You are an expert IELTS examiner. Analyze the following speaking test transcript and provide a score.
      The user's responses are marked with 'User:'.
      Transcript:
      ---
      ${formattedTranscript}
      ---
      Based on the four official IELTS criteria (Fluency and Coherence, Lexical Resource, Grammatical Range and Accuracy, and Pronunciation - which you must infer from the text), provide a detailed analysis.
      
      Respond ONLY with a valid JSON object with the following structure:
      {"overallBand": <number>, "criteria": [ { "criterionName": "Fluency & Coherence", "bandScore": <number>, "feedback": "...", "examples": [{"user_quote": "...", "suggestion": "...", "type": "Grammar"}] }, ...etc for all 4 criteria... ] }
    `;

    const result = await model.generateContent(prompt);
    const responseText = await result.response.text();
    const analysisData = safeJsonParse(responseText);

    if (!analysisData) {
      return res.status(500).json({ message: "AI failed to generate a valid analysis." });
    }

    // Save the analysis to the database
    const newExamResult = new ExamResult({
      userId,
      transcript,
      overallBand: analysisData.overallBand,
      criteria: analysisData.criteria,
    });
    const savedResult = await newExamResult.save();

    res.status(201).json({ resultId: savedResult._id });
  } catch (error) {
    console.error("Error analyzing exam:", error);
    res.status(500).json({ message: "Server error during exam analysis." });
  }
};

/**
 * @desc    Get a summary list of a user's past exams
 * @route   GET /api/exam/history
 * @access  Private
 */
const getExamHistory = async (req, res) => {
  try {
    const history = await ExamResult.find({ userId: req.user._id })
      .select("_id overallBand createdAt")
      .sort({ createdAt: -1 });

    // Map to match frontend model
    const historySummaries = history.map((item) => ({
      id: item._id,
      examDate: item.createdAt.getTime(),
      overallBand: item.overallBand,
    }));

    res.json({ history: historySummaries });
  } catch (error) {
    console.error("Error fetching exam history:", error);
    res.status(500).json({ message: "Server error fetching history." });
  }
};

/**
 * @desc    Get the full details of a specific exam result
 * @route   GET /api/exam/result/:resultId
 * @access  Private
 */
const getExamResultDetails = async (req, res) => {
  try {
    const result = await ExamResult.findOne({
      _id: req.params.resultId,
      userId: req.user._id, // Crucial security check
    });

    if (!result) {
      return res.status(404).json({ message: "Exam result not found or permission denied." });
    }
    res.json(result);
  } catch (error) {
    console.error("Error fetching exam result details:", error);
    res.status(500).json({ message: "Server error fetching result details." });
  }
};

module.exports = {
  startExam,
  handleExamStep,
  analyzeExam,
  getExamHistory,
  getExamResultDetails,
};
