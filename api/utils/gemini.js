const { GoogleGenerativeAI } = require("@google/generative-ai");

if (!process.env.GEMINI_API_KEY) {
  throw new Error("FATAL ERROR: GEMINI_API_KEY is not set.");
}

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
// Using a standard, fast, and up-to-date model.
const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash-latest" });

/**
 * A robust JSON parser that extracts JSON from a string,
 * even if it's wrapped in Markdown code blocks or other text.
 * @param {string} text The raw text response from the LLM.
 * @returns {object|null} The parsed JSON object, or null if no valid JSON is found.
 */
const safeJsonParse = (text) => {
  if (!text) {
    console.error("[safeJsonParse] Received null or empty text.");
    return null;
  }

  try {
    const startIndex = text.indexOf("{");
    const endIndex = text.lastIndexOf("}");

    if (startIndex === -1 || endIndex === -1 || endIndex < startIndex) {
      console.error("[safeJsonParse] Failed to find a valid JSON object within the text.");
      return null;
    }

    const jsonString = text.substring(startIndex, endIndex + 1);

    // [DEBUG LOG] Log the extracted JSON string before parsing
    console.log("[safeJsonParse] Attempting to parse extracted JSON string:");
    console.log(jsonString);

    const parsed = JSON.parse(jsonString);
    console.log("[safeJsonParse] Successfully parsed JSON.");
    return parsed;
  } catch (error) {
    const jsonString = text.substring(text.indexOf("{"), text.lastIndexOf("}") + 1);
    console.error("[safeJsonParse] CRITICAL: Failed to parse extracted JSON string.", {
      error: error.message,
      attemptedString: jsonString,
    });
    return null;
  }
};

/**
 * Generates a single, non-streamed response from the Gemini model.
 * @param {string} prompt The prompt to send to the model.
 * @returns {Promise<string>} The text content of the response.
 */
async function generateText(prompt) {
  // [DEBUG LOG] Log the prompt being sent to Gemini
  console.log("\n----------- PROMPT TO GEMINI -----------");
  console.log(prompt);
  console.log("----------------------------------------\n");

  const result = await model.generateContent(prompt);
  const text = result.response.text();

  // [DEBUG LOG] Log the raw response text from Gemini
  console.log("\n---------- RAW RESPONSE FROM GEMINI ----------");
  console.log(text);
  console.log("------------------------------------------\n");

  return text;
}

/**
 * Generates a streamed text response from the Gemini model.
 * @param {string} prompt The prompt to send to the model.
 * @returns {Promise<import('@google/generative-ai').GenerateContentStreamResult>} The stream result object.
 */
async function generateTextStream(prompt) {
  // Add logging here too if you ever use the streaming function
  console.log("\n----------- STREAMING PROMPT TO GEMINI -----------");
  console.log(prompt);
  console.log("------------------------------------------------\n");

  return model.generateContentStream({
    contents: [{ role: "user", parts: [{ text: prompt }] }],
  });
}

module.exports = {
  generateText,
  generateTextStream,
  safeJsonParse,
};
