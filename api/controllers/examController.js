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
    const prompt = `You are an expert IELTS examiner. Your name is Alex. Begin a new speaking test. Your first line should be to introduce yourself and ask for the user's name. Follow the IELTS Part 1 format.
    
    Respond ONLY with a valid JSON object with the structure: {"examiner_line": "Your full response here", "next_part": 1, "cue_card": null, "is_final_question": false}`;

    const responseText = await generateText(prompt);
    const data = safeJsonParse(responseText);

    if (!data || !data.examiner_line) {
      console.error("AI failed to generate a valid starting question JSON:", responseText);
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
  // --- MODIFICATION: Accept new context from the client ---
  const { part, userInput, transcriptContext, question_count_in_part } = req.body;
  console.log("Received exam step request:", {
    part,
    userInput,
    transcriptContext,
    question_count_in_part,
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
    try {
      const input_ids = await getKokoroInputIds(textToProcess);
      sendSseChunk(res, "input_ids_chunk", {
        sentence: textToProcess,
        input_ids: input_ids,
      });
    } catch (e) {
      console.error("Error getting Kokoro IDs for sentence:", textToProcess, e);
    }
  };

  try {
    // --- PROMPT ENHANCEMENT 1: Better prompt for natural dialogue ---
    // This prompt now focuses ONLY on generating the next line of speech.
    const streamingPrompt = `You are an expert IELTS examiner named Alex, currently in a mock speaking test. Maintain a professional and encouraging tone. The user just said: "${userInput}". 
    Based on the provided conversation context, provide ONLY your next spoken line as a natural, continuous stream of text. Do not add any JSON, formatting, or conversational filler like "Okay." unless it's a natural part of the sentence.
    
    Conversation History:
    ${transcriptContext}`;

    // --- PROMPT ENHANCEMENT 2: Detailed rules for the logic engine ---
    // This prompt is now the 'brain' that controls the exam structure.
    const ieltsRules = `
    IELTS Speaking Test Structure Rules:
    - The test has 3 parts.
    - Part 1: Introduction and interview. Lasts 4-5 minutes. Ask 4-5 questions on 2-3 familiar topics. After enough questions, you MUST transition to Part 2 by providing a cue card.
    - Part 2: Long turn. This part starts with a cue card. The user prepares for 1 minute (handled by the client) and speaks for 1-2 minutes. Your only task after their long turn is to say something brief like "Thank you." and immediately transition to Part 3.
    - Part 3: Discussion. Lasts 4-5 minutes. Ask 5-6 more abstract questions related to the Part 2 topic. This is the final part. After enough questions, end the conversation naturally and set is_final_question to true.
    - IMPORTANT: Do not end the exam prematurely. If a user gives a very short answer, ask a follow-up question to encourage them to speak more, or move to the next planned question. Only set is_final_question to true when Part 3 is complete.
    `;

    const statePrompt = `You are the logic engine for an IELTS exam, following strict rules.
    ${ieltsRules}
    
    Current State:
    - Current Part: ${part}
    - Question Number in this Part: ${question_count_in_part || 0}
    - Full Conversation History:
    ---
    ${transcriptContext}
    User: ${userInput}
    ---
    
    Your Task:
    Based on the rules and the current state, determine the next state of the exam. 
    - If in Part 1 and question_count_in_part is less than 4, next_part should be 1.
    - If in Part 1 and question_count_in_part is 4 or more, next_part should be 2, and you MUST generate a cue_card.
    - If in Part 2, the user has just finished their long turn. next_part must be 3.
    - If in Part 3 and question_count_in_part is less than 5, next_part should be 3.
    - If in Part 3 and question_count_in_part is 5 or more, you can conclude the test by setting is_final_question to true.
    
    Respond ONLY with a valid JSON object: {"next_part": <number>, "cue_card": {"topic": "...", "points": ["...", "..."]} or null, "is_final_question": <boolean>}`;

    // Execute in parallel
    const statePromise = generateText(statePrompt);
    const textStreamPromise = generateTextStream(streamingPrompt);
    const [stateResponseText, textStreamResult] = await Promise.all([
      statePromise,
      textStreamPromise,
    ]);

    console.log("State LLM Raw Response:", stateResponseText);
    finalExamState = safeJsonParse(stateResponseText);
    if (!finalExamState || typeof finalExamState.next_part === "undefined") {
      throw new Error("Failed to get valid exam state from LLM.");
    }

    for await (const chunk of textStreamResult.stream) {
      if (res.writableEnded) {
        console.log("Client disconnected, stopping stream.");
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
    const prompt = `You are an expert IELTS examiner providing a detailed analysis of a speaking test transcript. Analyze the following transcript based on the four IELTS criteria: Fluency and Coherence, Lexical Resource, Grammatical Range and Accuracy, and Pronunciation (based on the text). Provide a band score from 1-9 for each criterion and an overall band score. Also, give specific examples from the transcript to justify your scores and provide actionable feedback for improvement.
    
    Transcript:
    ${formattedTranscript}

    Respond ONLY with a valid JSON object with the structure: 
    {
      "overallBand": <number>,
      "criteria": {
        "fluencyAndCoherence": {"score": <number>, "feedback": "Your detailed feedback..."},
        "lexicalResource": {"score": <number>, "feedback": "Your detailed feedback..."},
        "grammaticalRangeAndAccuracy": {"score": <number>, "feedback": "Your detailed feedback..."},
        "pronunciation": {"score": <number>, "feedback": "Your detailed feedback..."}
      }
    }`;

    const responseText = await generateText(prompt);
    const analysisData = safeJsonParse(responseText);

    if (!analysisData || !analysisData.criteria) {
      console.error("AI failed to generate a valid analysis JSON:", responseText);
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
