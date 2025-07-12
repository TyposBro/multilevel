// {PATH_TO_PROJECT}/src/utils/gemini.js

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
    // Check if the response is already an object (from Workers AI)
    if (typeof text === "object") {
      return text;
    }

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
 * Generates a single, non-streamed response from a model via Cloudflare's AI Gateway.
 * @param {object} c - The Hono context, for accessing environment variables and the AI binding.
 * @param {string} prompt - The prompt to send to the model.
 * @returns {Promise<string>} The text content of the response.
 */
export async function generateText(c, prompt) {
  if (!c.env.AI) {
    throw new Error("FATAL ERROR: AI binding is not configured in wrangler.toml.");
  }

  // Use the official model name for Gemini via Cloudflare's gateway
  // const model = "@cf/google/gemma-3-12b-it";
  // const model = "@cf/mistralai/mistral-small-3.1-24b-instruct";
  const model = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
  // *** THE RECOMMENDED MODEL ***
  // This model is fast, cheap, and excellent at following instructions.
  // const model = "@cf/meta/llama-3.1-8b-instruct-fast";
  console.log("\n----------- PROMPT TO WORKERS AI -----------");
  console.log(prompt);
  console.log("-------------------------------------------\n");

  try {
    const aiResponse = await c.env.AI.run(model, {
      prompt: prompt,
      // Instruct the model to return JSON
      response_format: { type: "json_object" },
      // max_tokens: 8192,
    });

    // The response from Workers AI is already a parsed JSON object
    const responseData = aiResponse.response;

    console.log("\n---------- RAW RESPONSE FROM WORKERS AI ----------");
    console.log(responseData);
    console.log("----------------------------------------------\n");

    // safeJsonParse can now handle both the string and the object
    return responseData;
  } catch (error) {
    console.error("Cloudflare AI Gateway Error:", error);
    throw new Error(`Cloudflare AI request failed: ${error.message}`);
  }
}
