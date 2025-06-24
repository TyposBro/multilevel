// {PATH_TO_PROJECT}/api/models/ieltsExamController

const ExamResult = require("../models/ieltsExamResultModel.js");
const { generateText, safeJsonParse } = require("../utils/gemini.js");
const { getKokoroInputIds } = require("../utils/kokoro.js");

/**
 * @desc    Start a new mock exam
 * @route   POST /api/exam/start
 * @access  Private
 */
const startExam = async (req, res) => {
  try {
    const prompt = `You are an IELTS examiner. Begin a new speaking test. Your first line should be to state your name and ask for the user's name. Respond ONLY with a valid JSON object with the structure: {"examiner_line": "Your full response here", "next_part": 1, "cue_card": null, "is_final_question": false}`;

    const responseText = await generateText(prompt);
    const data = safeJsonParse(responseText);

    if (!data || !data.examiner_line) {
      return res.status(500).json({ message: "AI failed to generate a valid starting question." });
    }

    data.input_ids = await getKokoroInputIds(data.examiner_line);
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
  const { part, userInput, transcriptContext, questionCountInPart } = req.body;

  // [DEBUG LOG] Log the full incoming request body for /step
  console.log("--- handleExamStep: INCOMING REQUEST ---");
  console.log(JSON.stringify(req.body, null, 2)); // Pretty-print the JSON
  console.log("------------------------------------");

  try {
    const prompt = `You are an IELTS examiner and the logic engine for a mock speaking test.
The user is in Part ${part}.
This is question number ${questionCountInPart + 1} for this part.
The user just said: "${userInput}"
The conversation history is:
---
${transcriptContext}
---
Based on the current state and user input, generate your next response.
Your response MUST be a single, valid JSON object with the following structure:
{
  "examiner_line": "Your full spoken response here.",
  "next_part": <number for the next part, e.g., 1, 2, 3>,
  "cue_card": {"topic": "...", "points": ["...", "..."]} or null,
  "is_final_question": <boolean>
}
- If moving to Part 2, provide the cue card. For example, your 'examiner_line' might be "Now, I'm going to give you a topic...", but the cue card JSON object should contain the actual topic, like 'Describe a historical place...'. Otherwise, "cue_card" should be null.
- Decide if this is the final question of the part or the test.
- The "examiner_line" should be natural and appropriate for the context.
`;

    const responseText = await generateText(prompt);
    const data = safeJsonParse(responseText);

    if (!data || !data.examiner_line) {
      console.error("AI failed to generate a valid step JSON.", responseText);
      return res.status(500).json({ message: "AI failed to generate a valid step response." });
    }

    // Generate audio IDs for the complete response at once
    data.input_ids = await getKokoroInputIds(data.examiner_line);

    // [DEBUG LOG] Log the final data being sent back to the client
    console.log("--- handleExamStep: OUTGOING RESPONSE ---");
    // Don't log the full input_ids array as it's very long
    console.log(JSON.stringify({ ...data, input_ids_length: data.input_ids?.length }, null, 2));
    console.log("-------------------------------------");

    res.status(200).json(data);
  } catch (error) {
    console.error("Error processing exam step:", error);
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

  // [DEBUG LOG] Log the incoming transcript for /analyze
  console.log("--- analyzeExam: INCOMING REQUEST ---");
  console.log(JSON.stringify(req.body, null, 2));
  console.log("------------------------------------");

  try {
    const formattedTranscript = transcript.map((t) => `${t.speaker}: ${t.text}`).join("\n");

    const prompt = `You are an expert IELTS examiner. Analyze the following speaking test transcript. Provide a detailed evaluation for each of the four criteria (Fluency and Coherence, Lexical Resource, Grammatical Range and Accuracy, Pronunciation). For each criterion, give a band score and constructive feedback.
    
    IMPORTANT: If the user's speech in the transcript is insufficient, nonsensical, or completely empty, you MUST still provide a full analysis. In this case, assign a low band score (e.g., 1.0) for each criterion and provide feedback explaining that the score is low due to a lack of sufficient speech to analyze.
    
    Finally, calculate the overall band score.
    
    CRITICAL: Your entire response must be ONLY a single, valid JSON object using this exact structure, with no extra text or explanations before or after the JSON:
    {"overallBand": <number>, "criteria": [{"criterionName": "Fluency & Coherence", "bandScore": <number>, "feedback": "...", "examples": [{"userQuote": "...", "suggestion": "...", "type": "Fluency"}]}, ...]}`;

    const responseText = await generateText(prompt);
    const analysisData = safeJsonParse(responseText);

    if (!analysisData || !analysisData.criteria || !analysisData.overallBand) {
      console.error("AI failed to generate a valid analysis JSON.", responseText);
      return res.status(500).json({ message: "AI failed to generate a valid analysis." });
    }

    // Defensive programming to ensure 'type' field exists
    analysisData.criteria.forEach((criterion) => {
      if (criterion.examples && Array.isArray(criterion.examples)) {
        criterion.examples.forEach((example) => {
          if (!example.type) {
            if (criterion.criterionName.includes("Fluency")) example.type = "Fluency";
            else if (criterion.criterionName.includes("Lexical")) example.type = "Vocabulary";
            else if (criterion.criterionName.includes("Grammar")) example.type = "Grammar";
            else if (criterion.criterionName.includes("Pronunciation"))
              example.type = "Pronunciation";
            else example.type = "General";
          }
        });
      }
    });

    const newExamResult = new ExamResult({
      userId,
      transcript,
      overallBand: analysisData.overallBand,
      criteria: analysisData.criteria,
    });

    const savedResult = await newExamResult.save();

    // [DEBUG LOG] Log the successful result ID before sending
    console.log("--- analyzeExam: SUCCESS ---");
    console.log(`Successfully saved and sending resultId: ${savedResult._id}`);
    console.log("--------------------------");

    res.status(201).json({ resultId: savedResult._id });
  } catch (error) {
    // [DEBUG LOG] Enhanced error logging
    console.error("--- analyzeExam: CATCH BLOCK ERROR ---");
    console.error(error);
    console.error("------------------------------------");
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
      userId: req.user._id,
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
