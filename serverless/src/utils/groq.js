// {PATH_TO_PROJECT}/src/utils/groq.js

const GROQ_API_BASE_URL = "https://api.groq.com/openai/v1";

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
 * Generates a single, non-streamed response from the Groq model using its REST API.
 * @param {object} c - The Hono context, for accessing environment variables.
 * @param {Array<object>} messages - The messages to send to the model (in OpenAI chat format).
 * @returns {Promise<string>} The text content of the response.
 */
export async function generateText(c, messages) {
  const apiKey = c.env.GROQ_API_KEY;
  if (!apiKey) {
    throw new Error("FATAL ERROR: GROQ_API_KEY is not set in wrangler.toml secrets.");
  }

  const model = "meta-llama/llama-4-maverick-17b-128e-instruct"; // The Groq model you want to use
  const url = `${GROQ_API_BASE_URL}/chat/completions`;

  console.log("\n----------- PROMPT TO GROQ -----------");
  console.log(JSON.stringify(messages, null, 2));
  console.log("----------------------------------------\n");

  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      messages: messages,
      model: model,
      temperature: 1,
      max_tokens: 8192, // Max tokens for the Groq model
      top_p: 1,
      stream: false,
      response_format: {
        type: "json_object",
      },
      stop: null,
    }),
  });

  if (!response.ok) {
    const errorBody = await response.text();
    console.error("Groq API Error:", errorBody);
    throw new Error(`Groq API request failed with status ${response.status}`);
  }

  const data = await response.json();
  const text = data.choices[0]?.message?.content || "";

  console.log("\n---------- RAW RESPONSE FROM GROQ ----------");
  console.log(text);
  console.log("------------------------------------------\n");

  return text;
}
