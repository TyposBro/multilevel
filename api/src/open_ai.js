import OpenAI from "openai";
import * as dotenv from "dotenv";
dotenv.config();

const openai = new OpenAI({
  apiKey: process.env.GEMINI_API_KEY,
  baseURL: "https://generativelanguage.googleapis.com/v1beta/openai/",
});

export async function prompt(str = "Hi.sup") {
  try {
    const response = await openai.chat.completions.create({
      model: "gemini-2.0-flash",
      messages: [
        { role: "system", content: "You are a helpful assistant." },
        {
          role: "user",
          content: str,
        },
      ],
    });

    console.log(response.choices[0].message);
    return response.choices[0].message;
  } catch (error) {
    console.error("Error in prompt:", error);
    return "An error occurred during prompt processing.";
  }
}
