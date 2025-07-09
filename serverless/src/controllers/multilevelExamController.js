// {PATH_TO_PROJECT}/src/controllers/multilevelExamController.js

import { db } from "../db/d1-client.js";
import { generateText, safeJsonParse } from "../utils/gemini.js";
import OFFERINGS from "../config/offerings";
import { partAnalysisConfig } from "../config/partAnalysis";

// Helper function to check if daily usage counters need to be reset.
// It returns an object with the current count and last reset date.
const resetDailyUsageIfNeeded = (count, lastReset) => {
  if (!lastReset) {
    return { count: 0, lastReset: new Date().toISOString() };
  }

  const now = new Date();
  const lastResetDate = new Date(lastReset);

  // Compare dates in UTC to avoid timezone issues
  if (
    lastResetDate.getUTCFullYear() !== now.getUTCFullYear() ||
    lastResetDate.getUTCMonth() !== now.getUTCMonth() ||
    lastResetDate.getUTCDate() !== now.getUTCDate()
  ) {
    return { count: 0, lastReset: now.toISOString() };
  }

  return { count: count || 0, lastReset };
};

/**
 * @desc    Generate a new complete multilevel exam from content in the database.
 * @route   GET /api/exam/multilevel/new
 * @access  Private
 */
export const generateNewExam = async (c) => {
  try {
    const p1_1_Promise = db.getRandomContent(c.env.DB, "content_part1_1", 3);
    const p1_2_Promise = db.getRandomContent(c.env.DB, "content_part1_2", 1);
    const p2_Promise = db.getRandomContent(c.env.DB, "content_part2", 1);
    const p3_Promise = db.getRandomContent(c.env.DB, "content_part3", 1);

    const [part1_1, part1_2_arr, part2_arr, part3_arr] = await Promise.all([
      p1_1_Promise,
      p1_2_Promise,
      p2_Promise,
      p3_Promise,
    ]);

    if (
      part1_1.length < 3 ||
      part1_2_arr.length < 1 ||
      part2_arr.length < 1 ||
      part3_arr.length < 1
    ) {
      return c.json({ message: "Could not assemble a full exam. Not enough content in DB." }, 500);
    }

    // Return the exam structure with single objects instead of arrays for single-item parts.
    return c.json({
      part1_1,
      part1_2: part1_2_arr[0],
      part2: part2_arr[0],
      part3: part3_arr[0],
    });
  } catch (error) {
    console.error("Error generating new multilevel exam:", error);
    return c.json({ message: "Server error while generating exam." }, 500);
  }
};

/**
 * @desc    Analyze a full or partial multilevel exam transcript and save the result.
 * @route   POST /api/exam/multilevel/analyze
 * @access  Private
 */
export const analyzeExam = async (c) => {
  try {
    const user = c.get("user"); // Attached by `protect` and `checkSubscriptionStatus` middleware
    const { transcript, examContentIds, practicePart } = await c.req.json();
    const isSinglePartPractice = !!practicePart;

    if (!transcript || transcript.length === 0) {
      return c.json({ message: "Transcript is required for analysis." }, 400);
    }

    // --- TIER & USAGE CHECK LOGIC ---
    const tier = user.subscription_tier;
    const limits = OFFERINGS[tier];

    if (tier === "free") {
      const fullExamsUsage = resetDailyUsageIfNeeded(
        user.dailyUsage_fullExams_count,
        user.dailyUsage_fullExams_lastReset
      );
      const partPracticesUsage = resetDailyUsageIfNeeded(
        user.dailyUsage_partPractices_count,
        user.dailyUsage_partPractices_lastReset
      );

      if (isSinglePartPractice) {
        if (partPracticesUsage.count >= limits.dailyPartPractices) {
          return c.json(
            {
              message: `You have used all ${limits.dailyPartPractices} of your free part practices for today. Upgrade for unlimited access.`,
            },
            403
          );
        }
        partPracticesUsage.count += 1;
      } else {
        // Full Exam
        if (fullExamsUsage.count >= limits.dailyFullExams) {
          return c.json(
            {
              message: `You have used your ${limits.dailyFullExams} free full mock exam for today. Upgrade for more.`,
            },
            403
          );
        }
        fullExamsUsage.count += 1;
      }

      // Update the usage counters in the database
      await db.updateUserUsage(c.env.DB, user.id, {
        fullExams: fullExamsUsage,
        partPractices: partPracticesUsage,
      });
    }

    const formattedTranscript = transcript.map((t) => `${t.speaker}: ${t.text}`).join("\n");
    let prompt;

    if (isSinglePartPractice && partAnalysisConfig[practicePart]) {
      const config = partAnalysisConfig[practicePart];
      prompt = `
        You are an expert examiner for a structured, multilevel English speaking test.
        The user is practicing a single part of the exam: ${config.partName}. The maximum score for this part is ${config.maxScore}.
        Analyze the following speaking test transcript. The user's speech may be minimal or nonsensical; score it accordingly.
        TRANSCRIPT:\n---\n${formattedTranscript}\n---\n
        CRITICAL: Your entire response must be ONLY a single, valid JSON object using this exact structure, with no extra text or explanations.
        { "part": "${config.partName}", "score": <number>, "feedback": "${config.promptFocus}" }`;
    } else {
      prompt = `
        You are an expert examiner for a structured, multilevel English speaking test. The maximum score is 72.
        Analyze the following speaking test transcript. The user's speech may be minimal or nonsensical; score it accordingly.
        The exam has 4 parts:
        - Part 1.1: 3 personal questions (${partAnalysisConfig.P1_1.maxScore} points total)
        - Part 1.2: Picture comparison (${partAnalysisConfig.P1_2.maxScore} points total)
        - Part 2: Single picture monologue (${partAnalysisConfig.P2.maxScore} points total)
        - Part 3: Argumentative monologue (${partAnalysisConfig.P3.maxScore} points total)
        Based on the transcript, provide a score and constructive feedback for each part. Calculate the final total score (out of 72).
        TRANSCRIPT:\n---\n${formattedTranscript}\n---\n
        CRITICAL: Your entire response must be ONLY a single, valid JSON object using this exact structure, with no extra text or explanations.
        { "totalScore": <number>, "feedbackBreakdown": [
          { "part": "Part 1.1", "score": <number>, "feedback": "${partAnalysisConfig.P1_1.promptFocus}" },
          { "part": "Part 1.2", "score": <number>, "feedback": "${partAnalysisConfig.P1_2.promptFocus}" },
          { "part": "Part 2", "score": <number>, "feedback": "${partAnalysisConfig.P2.promptFocus}" },
          { "part": "Part 3", "score": <number>, "feedback": "${partAnalysisConfig.P3.promptFocus}" }
        ]}`;
    }

    const responseText = await generateText(c, prompt);
    const analysisData = safeJsonParse(responseText);

    let totalScore;
    let feedbackBreakdown;

    if (isSinglePartPractice) {
      if (!analysisData || typeof analysisData.score === "undefined" || !analysisData.feedback) {
        throw new Error("AI failed to generate a valid single-part analysis JSON.");
      }
      totalScore = analysisData.score;
      feedbackBreakdown = [analysisData];
    } else {
      if (
        !analysisData ||
        typeof analysisData.totalScore === "undefined" ||
        !analysisData.feedbackBreakdown
      ) {
        throw new Error("AI failed to generate a valid full-exam analysis JSON.");
      }
      totalScore = analysisData.totalScore;
      feedbackBreakdown = analysisData.feedbackBreakdown;
    }

    const savedResult = await db.createMultilevelExamResult(c.env.DB, {
      userId: user.id,
      transcript,
      totalScore,
      feedbackBreakdown,
      examContent: examContentIds,
      practicedPart: isSinglePartPractice ? practicePart : "FULL",
    });

    return c.json({ resultId: savedResult.id }, 201);
  } catch (error) {
    console.error("Error during multilevel exam analysis:", error);
    return c.json({ message: error.message || "Server error during exam analysis." }, 500);
  }
};

/**
 * @desc    Get the user's exam history for Multilevel, filtered by subscription tier.
 * @route   GET /api/exam/multilevel/history
 * @access  Private
 */
export const getExamHistory = async (c) => {
  try {
    const user = c.get("user");
    const tier = user.subscription_tier;
    const limits = OFFERINGS[tier];

    let retentionStartDateISO = null;
    if (limits.historyRetentionDays !== Infinity) {
      const now = new Date();
      const retentionStartDate = new Date(now.setDate(now.getDate() - limits.historyRetentionDays));
      retentionStartDateISO = retentionStartDate.toISOString();
    }

    const history = await db.getMultilevelExamHistory(c.env.DB, user.id, retentionStartDateISO);

    const historySummaries = history.map((item) => ({
      id: item.id,
      examDate: new Date(item.createdAt).getTime(),
      totalScore: item.totalScore,
      practicePart: item.practicedPart,
    }));

    return c.json({ history: historySummaries });
  } catch (error) {
    console.error("Error fetching multilevel history:", error);
    return c.json({ message: "Server error fetching history." }, 500);
  }
};

/**
 * @desc    Get the details of a single Multilevel exam result.
 * @route   GET /api/exam/multilevel/result/:resultId
 * @access  Private
 */
export const getExamResultDetails = async (c) => {
  try {
    const resultId = c.req.param("resultId");
    const user = c.get("user");
    const result = await db.getMultilevelExamResultDetails(c.env.DB, resultId, user.id);

    if (!result) {
      return c.json({ message: "Result not found or permission denied." }, 404);
    }
    // The db client already parses the JSON fields, so it's ready to be sent.
    return c.json(result);
  } catch (error) {
    console.error("Error fetching multilevel result details:", error);
    return c.json({ message: "Server error fetching result details." }, 500);
  }
};
