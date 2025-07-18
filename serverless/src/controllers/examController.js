// {PATH_TO_PROJECT}/src/controllers/examController.js

import { db } from "../db/d1-client.js";
import { generateText, safeJsonParse } from "../utils/gemini.js";
import OFFERINGS from "../config/offerings.js";
import { partAnalysisConfig } from "../config/partAnalysis.js";
import {
  generateSimpleAnalysisPrompt,
  generateDetailedAnalysisPrompt,
} from "../prompt/examAnalysisPrompt.js";

// Helper function to check if daily usage counters need to be reset.
const resetDailyUsageIfNeeded = (count, lastReset) => {
  if (!lastReset) {
    return { count: 0, lastReset: new Date().toISOString() };
  }

  const now = new Date();
  const lastResetDate = new Date(lastReset);

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
 * @desc    Analyze a full or partial multilevel exam transcript (SIMPLE analysis).
 * @route   POST /api/exam/multilevel/analyze
 * @access  Private
 */
export const analyzeExam = async (c) => {
  try {
    const user = c.get("user");
    // Note: The original controller had 'examContent' which was unused.
    // Kept 'practicePart' as it's the key differentiator.
    const { transcript, practicePart } = await c.req.json();
    const isSinglePartPractice = !!practicePart && practicePart !== "FULL";

    if (!transcript || transcript.length === 0) {
      return c.json({ message: "Transcript is required for analysis." }, 400);
    }

    const tier = user.subscription_tier;
    const limits = OFFERINGS[tier];
    if (c.env.ENVIRONMENT !== "development" && tier === "free") {
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
              message: `You have used all ${limits.dailyPartPractices} of your free part practices today.`,
            },
            403
          );
        }
        partPracticesUsage.count += 1;
      } else {
        if (fullExamsUsage.count >= limits.dailyFullExams) {
          return c.json(
            { message: `You have used your ${limits.dailyFullExams} free full mock exam today.` },
            403
          );
        }
        fullExamsUsage.count += 1;
      }
      await db.updateUserUsage(c.env.DB, user.id, {
        fullExams: fullExamsUsage,
        partPractices: partPracticesUsage,
      });
    }

    const formattedTranscript = transcript.map((t) => `${t.speaker}: ${t.text}`).join("\n");
    const prompt = generateSimpleAnalysisPrompt(
      formattedTranscript,
      partAnalysisConfig,
      isSinglePartPractice,
      practicePart
    );
    const responseText = await generateText(c, prompt);
    const analysisData = safeJsonParse(responseText);

    let totalScore, feedbackBreakdown;
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

    const resultResponse = {
      _id: crypto.randomUUID(),
      userId: user.id,
      totalScore,
      feedbackBreakdown,
      transcript,
      createdAt: new Date().toISOString(),
    };
    return c.json(resultResponse, 201);
  } catch (error) {
    console.error("Error during simple multilevel exam analysis:", error);
    return c.json({ message: error.message || "Server error during exam analysis." }, 500);
  }
};

/**
 * @desc    Analyze exam transcript with DETAILED, criteria-based, and LOCALIZED feedback.
 * @route   POST /api/exam/multilevel/v2/analyze
 * @access  Private
 */
export const analyzeExamV2 = async (c) => {
  try {
    const user = c.get("user");
    // --- MODIFIED: Get language from the request body ---
    const { transcript, practicePart, language } = await c.req.json();
    const isSinglePartPractice = !!practicePart && practicePart !== "FULL";

    if (!transcript || transcript.length === 0) {
      return c.json({ message: "Transcript is required for analysis." }, 400);
    }

    const tier = user.subscription_tier;
    const limits = OFFERINGS[tier];
    if (c.env.ENVIRONMENT !== "development" && tier === "free") {
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
      await db.updateUserUsage(c.env.DB, user.id, {
        fullExams: fullExamsUsage,
        partPractices: partPracticesUsage,
      });
    }

    // --- MODIFIED: Use the language from the request, or default to 'en' ---
    const targetLanguage = language || "en";
    const formattedTranscript = transcript.map((t) => `${t.speaker}: ${t.text}`).join("\n");
    const prompt = generateDetailedAnalysisPrompt(
      formattedTranscript,
      partAnalysisConfig,
      isSinglePartPractice,
      practicePart,
      targetLanguage
    );
    const responseText = await generateText(c, prompt);
    const analysisData = safeJsonParse(responseText);

    if (!analysisData) {
      throw new Error("AI response was not valid JSON.");
    }
    let finalAnalysisData;
    if (isSinglePartPractice) {
      if (
        typeof analysisData.score === "undefined" ||
        !analysisData.detailedBreakdown?.fluencyAndCoherence
      ) {
        throw new Error("AI failed to generate a valid detailed single-part analysis JSON.");
      }
      finalAnalysisData = {
        totalScore: analysisData.score,
        feedbackBreakdown: [
          {
            part: analysisData.part,
            score: analysisData.score,
            overallFeedback: analysisData.overallFeedback,
            detailedBreakdown: analysisData.detailedBreakdown,
          },
        ],
      };
    } else {
      if (
        typeof analysisData.totalScore === "undefined" ||
        !analysisData.feedbackBreakdown?.[0]?.detailedBreakdown?.fluencyAndCoherence
      ) {
        throw new Error("AI failed to generate a valid detailed full-exam analysis JSON.");
      }
      finalAnalysisData = analysisData;
    }

    const resultResponse = {
      _id: crypto.randomUUID(),
      userId: user.id,
      totalScore: finalAnalysisData.totalScore,
      feedbackBreakdown: finalAnalysisData.feedbackBreakdown,
      transcript,
      createdAt: new Date().toISOString(),
      language: targetLanguage,
    };

    // --- REMOVED: No database saving of the analysis result ---

    return c.json(resultResponse, 201);
  } catch (error) {
    console.error("Error during detailed multilevel exam analysis:", error);
    return c.json({ message: error.message || "Server error during detailed exam analysis." }, 500);
  }
};
