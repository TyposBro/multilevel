// {PATH_TO_PROJECT}/src/utils/gemini.js

// CHANGED: Updated the base URL to point to the OpenRouter API.
const OPENROUTER_API_BASE_URL = "https://openrouter.ai/api/v1";

/**
 * A robust JSON parser that extracts JSON from a string,
 * even if it's wrapped in Markdown code blocks or other text.
 * NOTE: No changes are needed for this function. It's a general utility.
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
 * Generates a single, non-streamed response from a model via OpenRouter.
 * @param {object} c - The Hono context, for accessing environment variables.
 * @param {string} prompt - The prompt to send to the model.
 * @returns {Promise<string>} The text content of the response.
 */
export async function generateText(c, prompt) {
  // CHANGED: Use OPEN_ROUTER_API from your environment secrets.
  // REMINDER: You must set this secret in your wrangler.toml or Cloudflare dashboard.
  const apiKey = c.env.OPEN_ROUTER_API;
  if (!apiKey) {
    throw new Error("FATAL ERROR: OPEN_ROUTER_API is not set in wrangler.toml secrets.");
  }

  // CHANGED: Set the model to the specific OpenRouter model ID.
  const model = "google/gemini-2.5-flash-lite-preview-06-17";
  const url = `${OPENROUTER_API_BASE_URL}/chat/completions`;

  console.log("\n----------- PROMPT TO OPENROUTER -----------");
  console.log(prompt);
  console.log("------------------------------------------\n");

  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      // CHANGED: Authentication is now done via the Authorization header.
      Authorization: `Bearer ${apiKey}`,
      // Optional, but recommended by OpenRouter for identifying your app.
      // Replace with your actual site and app name.
      // "HTTP-Referer": "https://YOUR_SITE_URL",
      // "X-Title": "YOUR_APP_NAME",
    },
    // CHANGED: The request body now uses the standard OpenAI chat completions format.
    body: JSON.stringify({
      model: model,
      messages: [{ role: "user", content: prompt }],
    }),
  });

  if (!response.ok) {
    const errorBody = await response.text();
    console.error("OpenRouter API Error:", errorBody);
    throw new Error(`OpenRouter API request failed with status ${response.status}`);
  }

  const data = await response.json();
  // CHANGED: The response structure is different. The text is in `choices[0].message.content`.
  const text = data.choices[0]?.message?.content || "";

  console.log("\n---------- RAW RESPONSE FROM OPENROUTER ----------");
  console.log(text);
  console.log("----------------------------------------------\n");

  return text;
}
