// {PATH_TO_PROJECT}/src/controllers/ieltsExamController.js
import { generateText, safeJsonParse } from "../utils/gemini.js";
import { getKokoroInputIds } from "../utils/kokoro.js";

/**
 * @desc    Start a new IELTS mock exam.
 * @route   POST /api/exam/ielts/start
 * @access  Private
 */
export const startExam = async (c) => {
  try {
    const prompt = `You are an IELTS examiner. Begin a new speaking test. Your first line should be to state your name and ask for the user's name. Respond ONLY with a valid JSON object with the structure: {"examiner_line": "Your full response here", "next_part": 1, "cue_card": null, "is_final_question": false}`;

    const responseText = await generateText(c, prompt);
    const data = safeJsonParse(responseText);

    if (!data || !data.examiner_line) {
      return c.json({ message: "AI failed to generate a valid starting question." }, 500);
    }

    data.input_ids = await getKokoroInputIds(c, data.examiner_line);
    return c.json(data);
  } catch (error) {
    console.error("Error starting IELTS exam:", error);
    return c.json({ message: "Server error while starting exam." }, 500);
  }
};

/**
 * @desc    Handle the next step in an IELTS exam.
 * @route   POST /api/exam/ielts/step
 * @access  Private
 */
export const handleExamStep = async (c) => {
  try {
    const { part, userInput, transcriptContext, questionCountInPart } = await c.req.json();

    const prompt = `You are an IELTS examiner and the logic engine for a mock speaking test.
The user is in Part ${part}.
This is question number ${questionCountInPart + 1} for this part.
The user just said: "${userInput}"
The conversation history is:
---
${transcriptContext}
---
Based on the current state and user input, generate your next response.
Your response MUST be a single, valid JSON object with the following structure:
{
  "examiner_line": "Your full spoken response here.",
  "next_part": <number for the next part, e.g., 1, 2, 3>,
  "cue_card": {"topic": "...", "points": ["...", "..."]} or null,
  "is_final_question": <boolean>
}
- If moving to Part 2, provide the cue card. For example, your 'examiner_line' might be "Now, I'm going to give you a topic...", but the cue card JSON object should contain the actual topic, like 'Describe a historical place...'. Otherwise, "cue_card" should be null.
- Decide if this is the final question of the part or the test.
- The "examiner_line" should be natural and appropriate for the context.`;

    const responseText = await generateText(c, prompt);
    const data = safeJsonParse(responseText);

    if (!data || !data.examiner_line) {
      console.error("AI failed to generate a valid step JSON.", responseText);
      return c.json({ message: "AI failed to generate a valid step response." }, 500);
    }

    data.input_ids = await getKokoroInputIds(c, data.examiner_line);
    return c.json(data);
  } catch (error) {
    console.error("Error processing IELTS exam step:", error);
    return c.json({ message: "Server error during exam step." }, 500);
  }
};

/**
 * @desc    Analyze a full IELTS exam transcript and save the result.
 * @route   POST /api/exam/ielts/analyze
 * @access  Private
 */
export const analyzeExam = async (c) => {
  try {
    const { transcript } = await c.req.json();
    const user = c.get("user");

    const formattedTranscript = transcript.map((t) => `${t.speaker}: ${t.text}`).join("\n");

    const prompt = `You are an expert IELTS examiner. Analyze the following speaking test transcript. Provide a detailed evaluation for each of the four criteria (Fluency and Coherence, Lexical Resource, Grammatical Range and Accuracy, Pronunciation). For each criterion, give a band score and constructive feedback.
    
    IMPORTANT: If the user's speech in the transcript is insufficient, nonsensical, or completely empty, you MUST still provide a full analysis. In this case, assign a low band score (e.g., 1.0) for each criterion and provide feedback explaining that the score is low due to a lack of sufficient speech to analyze.
    
    Finally, calculate the overall band score.
    
    CRITICAL: Your entire response must be ONLY a single, valid JSON object using this exact structure, with no extra text or explanations before or after the JSON:
    {"overallBand": <number>, "criteria": [{"criterionName": "Fluency & Coherence", "bandScore": <number>, "feedback": "...", "examples": [{"userQuote": "...", "suggestion": "...", "type": "Fluency"}]}, ...]}`;

    const responseText = await generateText(c, prompt);
    const analysisData = safeJsonParse(responseText);

    if (!analysisData || !analysisData.criteria || !analysisData.overallBand) {
      console.error("AI failed to generate a valid analysis JSON.", responseText);
      return c.json({ message: "AI failed to generate a valid analysis." }, 500);
    }

    // Defensive programming to ensure 'type' field exists in examples
    analysisData.criteria.forEach((criterion) => {
      if (criterion.examples && Array.isArray(criterion.examples)) {
        criterion.examples.forEach((example) => {
          if (!example.type) {
            if (criterion.criterionName.includes("Fluency")) example.type = "Fluency";
            else if (criterion.criterionName.includes("Lexical")) example.type = "Vocabulary";
            else if (criterion.criterionName.includes("Grammar")) example.type = "Grammar";
            else if (criterion.criterionName.includes("Pronunciation"))
              example.type = "Pronunciation";
            else example.type = "General";
          }
        });
      }
    });

    // Construct the full response object to send back to the client
    const resultResponse = {
      _id: crypto.randomUUID(), // Generate a unique ID on the server
      userId: user.id,
      overallBand: analysisData.overallBand,
      criteria: analysisData.criteria,
      transcript,
      createdAt: new Date().toISOString(),
    };

    return c.json(resultResponse, 201);
  } catch (error) {
    console.error("Error analyzing IELTS exam:", error);
    return c.json({ message: "Server error during exam analysis." }, 500);
  }
};
