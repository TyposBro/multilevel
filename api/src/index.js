import * as dotenv from "dotenv";
dotenv.config();

import express from "express";
import { prompt } from "./open_ai.js";
import { v4 as uuidv4 } from "uuid"; // For generating unique user IDs

const app = express();
const port = process.env.PORT || 3000;

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// In-memory storage for chat histories (replace with a database in production)
const chatHistories = new Map();

app.get("/", async (req, res) => {
  try {
    let userId = req.headers["user-id"]; // Get user ID from headers

    if (!userId) {
      // If no user ID, generate a new one
      userId = uuidv4();
      res.setHeader("user-id", userId); // Send the ID back to the user
    }

    let inputData;

    if (req.body && Object.keys(req.body).length > 0) {
      inputData = req.body;
    } else if (req.query.prompt) {
      inputData = req.query.prompt;
    } else {
      return res.status(400).send("No valid prompt provided.");
    }

    // Get the chat history for the user
    const history = chatHistories.get(userId) || [];

    // Add the user's message to the history
    const userMessage = {
      role: "user",
      content: typeof inputData === "object" ? JSON.stringify(inputData) : inputData,
    };
    history.push(userMessage);

    // Get the response from the OpenAI API with the chat history
    const response = await prompt(history);

    // Add the API's response to the history
    const apiMessage = { role: "assistant", content: response };
    history.push(apiMessage);

    // Update the chat history
    chatHistories.set(userId, history);

    res.send(response);
  } catch (error) {
    console.error("Error processing prompt:", error);
    res.status(500).send("An error occurred.");
  }
});

app.listen(port, () => {
  console.log(`Example app listening on port ${port}`);
});
