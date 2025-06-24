// {PATH_TO_PROJECT}/api/models/content/Part1_1_QuestionModel.js

const mongoose = require("mongoose");

const part1_1_Schema = new mongoose.Schema({
  questionText: { type: String, required: true },
  audioUrl: { type: String, required: true }, // URL to audio file on CDN
  tags: [String],
});

module.exports = mongoose.model("Part1.1Question", part1_1_Schema);
