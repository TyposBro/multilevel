// {PATH_TO_PROJECT}/api/models/content/Part2_SetModel.js

const mongoose = require("mongoose");

const questionSchema = new mongoose.Schema(
  {
    text: { type: String, required: true },
    audioUrl: { type: String, required: true },
  },
  { _id: false }
);

const part2_Schema = new mongoose.Schema({
  imageUrl: { type: String, required: true },
  imageDescription: { type: String, required: true }, // For internal reference
  questions: [questionSchema], // Should contain 3 questions
  tags: [String],
});

module.exports = mongoose.model("Part2Set", part2_Schema);
