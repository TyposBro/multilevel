// {PATH_TO_PROJECT}/src/utils/gemini.js
const GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

/**
 * A robust JSON parser that extracts JSON from a string,
 * even if it's wrapped in Markdown code blocks or other text.
 * @param {string} text The raw text response from the LLM.
 * @returns {object|null} The parsed JSON object, or null if no valid JSON is found.
 */
export const safeJsonParse = (text) => {
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
    const parsed = JSON.parse(jsonString);
    console.log("[safeJsonParse] Successfully parsed JSON.");
    return parsed;
  } catch (error) {
    console.error("[safeJsonParse] CRITICAL: Failed to parse extracted JSON string.", {
      error: error.message,
    });
    return null;
  }
};

/**
 * Generates a single, non-streamed response from the Gemini model using its REST API.
 * @param {object} c - The Hono context, for accessing environment variables.
 * @param {string} prompt - The prompt to send to the model.
 * @returns {Promise<string>} The text content of the response.
 */
export async function generateText(c, prompt) {
  const apiKey = c.env.GEMINI_API_KEY;
  if (!apiKey) {
    throw new Error("FATAL ERROR: GEMINI_API_KEY is not set in wrangler.toml secrets.");
  }

  // --- THIS IS THE FIX ---
  // The correct model name is "gemini-2.0-flash".
  const model = "gemini-2.0-flash";
  // --- END OF FIX ---

  const url = `${GEMINI_API_BASE_URL}/${model}:generateContent?key=${apiKey}`;

  console.log("\n----------- PROMPT TO GEMINI -----------");
  console.log(prompt);
  console.log("----------------------------------------\n");

  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ parts: [{ text: prompt }] }],
    }),
  });

  if (!response.ok) {
    const errorBody = await response.text();
    console.error("Gemini API Error:", errorBody);
    throw new Error(`Gemini API request failed with status ${response.status}`);
  }

  const data = await response.json();
  const text = data.candidates[0]?.content?.parts[0]?.text || "";

  console.log("\n---------- RAW RESPONSE FROM GEMINI ----------");
  console.log(text);
  console.log("------------------------------------------\n");

  return text;
}
