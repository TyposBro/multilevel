// {PATH_TO_PROJECT}/api/utils/kokoro.js

const axios = require("axios");

const KOKORO_PREPROCESS_URL =
  process.env.KOKORO_PREPROCESS_URL || "http://localhost:8000/preprocess";
const DEFAULT_KOKORO_LANG_CODE = process.env.DEFAULT_KOKORO_LANG_CODE || "b";
const DEFAULT_KOKORO_CONFIG_KEY = process.env.DEFAULT_KOKORO_CONFIG_KEY || "hexgrad/Kokoro-82M";

/**
 * Calls the Kokoro preprocessing service to get input_ids for a given text.
 * @param {string} textToProcess The sentence to preprocess.
 * @returns {Promise<number[]>} An array of input_ids, or an empty array on failure.
 */
async function getKokoroInputIds(textToProcess) {
  if (!textToProcess) {
    return [];
  }
  try {
    console.log(`[Kokoro] Preprocessing text: "${textToProcess.substring(0, 50)}..."`);
    const response = await axios.post(
      KOKORO_PREPROCESS_URL,
      {
        text: textToProcess,
        lang_code: DEFAULT_KOKORO_LANG_CODE,
        config_key: DEFAULT_KOKORO_CONFIG_KEY,
      },
      { responseType: "json", timeout: 7000 }
    );

    const ids = response.data?.results?.[0]?.input_ids?.[0];
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

module.exports = { getKokoroInputIds };
