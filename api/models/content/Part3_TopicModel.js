// {PATH_TO_PROJECT}/api/models/content/Part3_TopicModel.js

const mongoose = require("mongoose");

const part3_Schema = new mongoose.Schema({
  topic: { type: String, required: true },
  forPoints: { type: [String], required: true }, // e.g., ["Point A", "Point B", "Point C"]
  againstPoints: { type: [String], required: true },
  tags: [String],
});

module.exports = mongoose.model("Part3Topic", part3_Schema);
