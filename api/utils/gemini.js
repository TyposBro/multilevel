// {PATH_TO_PROJECT}/api/utils/gemini.js

const { GoogleGenerativeAI } = require("@google/generative-ai");

if (!process.env.GEMINI_API_KEY) {
  throw new Error("FATAL ERROR: GEMINI_API_KEY is not set.");
}

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash-lite" });

/**
 * Helper to safely parse JSON from an LLM response,
 * including stripping markdown code blocks.
 * @param {string} text The text response from the LLM.
 * @returns {object|null} The parsed JSON object or null if parsing fails.
 */
function safeJsonParse(text) {
  try {
    const match = text.match(/```json\n([\s\S]*?)\n```/);
    if (match && match[1]) {
      return JSON.parse(match[1]);
    }
    return JSON.parse(text);
  } catch (e) {
    console.error("Failed to parse JSON from LLM:", text, e);
    return null;
  }
}

/**
 * Generates a single, non-streamed response from the Gemini model.
 * @param {string} prompt The prompt to send to the model.
 * @returns {Promise<string>} The text content of the response.
 */
async function generateText(prompt) {
  const result = await model.generateContent(prompt);
  return result.response.text();
}

/**
 * Generates a streamed text response from the Gemini model.
 * @param {string} prompt The prompt to send to the model.
 * @returns {Promise<import('@google/generative-ai').GenerateContentStreamResult>} The stream result object.
 */
async function generateTextStream(prompt) {
  return model.generateContentStream({
    contents: [{ role: "user", parts: [{ text: prompt }] }],
  });
}

module.exports = {
  generateText,
  generateTextStream,
  safeJsonParse,
};
