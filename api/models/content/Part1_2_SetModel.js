// {PATH_TO_PROJECT}/api/models/content/Part1_2_SetModel.js

const mongoose = require("mongoose");

const questionSchema = new mongoose.Schema(
  {
    text: { type: String, required: true },
    audioUrl: { type: String, required: true },
  },
  { _id: false }
);

const part1_2_Schema = new mongoose.Schema({
  image1Url: { type: String, required: true },
  image2Url: { type: String, required: true },
  imageDescription: { type: String, required: true }, // For internal reference
  questions: [questionSchema], // Should contain 3 questions
  tags: [String],
});

module.exports = mongoose.model("Part1.2Set", part1_2_Schema);
