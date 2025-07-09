// {PATH_TO_PROJECT}/src/utils/kokoro.js

/**
 * Calls the Kokoro preprocessing service to get input_ids for a given text.
 * @param {object} c - The Hono context, for accessing environment variables.
 * @param {string} textToProcess - The sentence to preprocess.
 * @returns {Promise<number[]>} An array of input_ids, or an empty array on failure.
 */
export async function getKokoroInputIds(c, textToProcess) {
  if (!textToProcess) {
    return [];
  }

  const KOKORO_PREPROCESS_URL = c.env.KOKORO_PREPROCESS_URL || "http://localhost:8000/preprocess";
  const DEFAULT_KOKORO_LANG_CODE = c.env.DEFAULT_KOKORO_LANG_CODE || "b";
  const DEFAULT_KOKORO_CONFIG_KEY = c.env.DEFAULT_KOKORO_CONFIG_KEY || "hexgrad/Kokoro-82M";

  try {
    console.log(`[Kokoro] Preprocessing text: "${textToProcess.substring(0, 50)}..."`);
    const response = await fetch(KOKORO_PREPROCESS_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        text: textToProcess,
        lang_code: DEFAULT_KOKORO_LANG_CODE,
        config_key: DEFAULT_KOKORO_CONFIG_KEY,
      }),
      // Add timeout for fetch if needed (using AbortController)
    });

    if (!response.ok) {
      throw new Error(`Kokoro service responded with status: ${response.status}`);
    }

    const data = await response.json();
    const ids = data?.results?.[0]?.input_ids?.[0];

    if (ids && Array.isArray(ids)) {
      console.log(`[Kokoro] Successfully got ${ids.length} input_ids.`);
      return ids;
    }

    console.warn("[Kokoro] Preprocessor did not return valid input_ids.");
    return [];
  } catch (error) {
    console.error(`[Kokoro] Error calling preprocessor: ${error.message}`);
    return [];
  }
}
