import * as dotenv from "dotenv";
dotenv.config();

import express from "express";
import { prompt } from "./open_ai.js";
import { v4 as uuidv4 } from "uuid";
import textToSpeech from "@google-cloud/text-to-speech";
import fs from "fs/promises"; // Use fs.promises for async file operations

const app = express();
const port = process.env.PORT || 3000;

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

const chatHistories = new Map();
const ttsClient = new textToSpeech.TextToSpeechClient();

app.get("/", async (req, res) => {
  try {
    let userId = req.headers["user-id"];

    if (!userId) {
      userId = uuidv4();
      res.setHeader("user-id", userId);
    }

    let inputData;

    if (req.body && Object.keys(req.body).length > 0) {
      inputData = req.body;
    } else if (req.query.prompt) {
      inputData = req.query.prompt;
    } else {
      return res.status(400).send("No valid prompt provided.");
    }

    const history = chatHistories.get(userId) || [];
    const userMessage = { role: "user", content: typeof inputData === 'object' ? JSON.stringify(inputData): inputData };
    history.push(userMessage);

    const apiResponse = await prompt(history);
    const apiMessage = { role: "assistant", content: apiResponse };
    history.push(apiMessage);
    chatHistories.set(userId, history);

    // Text-to-Speech
    const ttsRequest = {
      input: { text: apiResponse },
      voice: { languageCode: "en-US", ssmlGender: "NEUTRAL" },
      audioConfig: { audioEncoding: "MP3" },
    };

    const [ttsResponse] = await ttsClient.synthesizeSpeech(ttsRequest);
    const audioContent = ttsResponse.audioContent;

    // Send audio as response
    res.setHeader('Content-Type', 'audio/mpeg');
    res.send(audioContent);

  } catch (error) {
    console.error("Error processing prompt:", error);
    res.status(500).send("An error occurred.");
  }
});

app.listen(port, () => {
  console.log(`Example app listening on port ${port}`);
});