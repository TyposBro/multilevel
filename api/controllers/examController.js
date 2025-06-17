// {PATH_TO_PROJECT}/api/controllers/examController.js

const ExamResult = require("../models/ExamResultModel.js");
const { generateText, generateTextStream, safeJsonParse } = require("../utils/gemini.js");
const { getKokoroInputIds } = require("../utils/kokoro.js");
const { sendSseChunk, sentenceTerminators } = require("../utils/sse");

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
 * @desc    Handle the next step in an exam VIA STREAMING
 * @route   POST /api/exam/step-stream
 * @access  Private
 */
const handleExamStepStream = async (req, res) => {
  const { part, userInput, transcriptContext, questionCountInPart } = req.body;
  console.log("Received exam step request:", {
    part,
    userInput,
    questionCountInPart,
    transcriptContext: transcriptContext.slice(-100),
  });

  // --- SSE Setup ---
  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("Connection", "keep-alive");
  res.write(": SSE connection initiated\n\n");

  let fullModelResponseText = "";
  let sentenceBuffer = "";
  let finalExamState = {};

  const processAndStreamSentence = async (textToProcess) => {
    if (res.writableEnded || !textToProcess) return;
    const input_ids = await getKokoroInputIds(textToProcess);
    sendSseChunk(res, "input_ids_chunk", {
      sentence: textToProcess,
      input_ids: input_ids,
    });
  };

  try {
    const streamingPrompt = `You are an IELTS examiner in a mock speaking test. The user just said: "${userInput}". Based on the conversation context, provide ONLY your next spoken line as a natural, continuous stream of text. Do not add any JSON or extra formatting.`;
    const statePrompt = `You are the logic engine for an IELTS exam. The user is in Part ${part}. This is question number ${
      questionCountInPart + 1
    } for this part. The conversation so far is:\n---\n${transcriptContext}\nUser: ${userInput}\n---\nBased on this, determine the next state of the exam. Your decision should consider the current part and question count to decide if it's time to move to the next part. Respond ONLY with a valid JSON object: {"next_part": <number>, "cue_card": {"topic": "...", "points": ["...", "..."]} or null, "is_final_question": <boolean>}`;

    // Execute in parallel
    const statePromise = generateText(statePrompt);
    const textStreamPromise = generateTextStream(streamingPrompt);
    const [stateResponseText, textStreamResult] = await Promise.all([
      statePromise,
      textStreamPromise,
    ]);

    finalExamState = safeJsonParse(stateResponseText);
    if (!finalExamState) throw new Error("Failed to get valid exam state from LLM.");

    for await (const chunk of textStreamResult.stream) {
      if (res.writableEnded) {
        console.log("Client disconnected");
        break;
      }
      const textChunk = chunk.candidates?.[0]?.content?.parts?.[0]?.text;
      if (textChunk) {
        fullModelResponseText += textChunk;
        sendSseChunk(res, "text_chunk", { text: textChunk });

        sentenceBuffer += textChunk;
        let match;
        while ((match = sentenceTerminators.exec(sentenceBuffer)) !== null) {
          if (res.writableEnded) break;
          const sentence = sentenceBuffer.substring(0, match.index + 1).trim();
          sentenceBuffer = sentenceBuffer.substring(match.index + 1);
          if (sentence) await processAndStreamSentence(sentence);
        }
      }
    }

    if (!res.writableEnded && sentenceBuffer.trim()) {
      await processAndStreamSentence(sentenceBuffer.trim());
    }

    if (!res.writableEnded) {
      const endData = { ...finalExamState, full_text: fullModelResponseText };
      sendSseChunk(res, "stream_end", endData);
    }
  } catch (error) {
    console.error("Error during exam step stream:", error);
    if (!res.writableEnded) {
      sendSseChunk(res, "error", { message: "Stream processing error", details: error.message });
    }
  } finally {
    if (!res.writableEnded) {
      res.end();
    }
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
    const prompt = `You are an expert IELTS examiner. Analyze the following speaking test transcript. Provide a detailed evaluation for each of the four criteria (Fluency and Coherence, Lexical Resource, Grammatical Range and Accuracy, Pronunciation). For each criterion, give a band score (e.g., 6.5) and constructive feedback with specific examples from the user's speech. Finally, calculate the overall band score. Respond ONLY with a valid JSON object: {"overallBand": <number>, "criteria": [{"criterionName": "Fluency & Coherence", "bandScore": <number>, "feedback": "...", "examples": [{"userQuote": "...", "suggestion": "..."}]}, ...]}`;

    const responseText = await generateText(prompt);
    const analysisData = safeJsonParse(responseText);

    if (!analysisData) {
      return res.status(500).json({ message: "AI failed to generate a valid analysis." });
    }

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
  handleExamStepStream,
  analyzeExam,
  getExamHistory,
  getExamResultDetails,
};
