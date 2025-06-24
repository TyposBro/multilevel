const mongoose = require("mongoose");

const wordSchema = new mongoose.Schema(
  {
    word: { type: String, required: true, unique: true, index: true },
    cefrLevel: {
      type: String,
      required: true,
      enum: ["A1", "A2", "B1", "B2", "C1", "C2"],
    },
    topic: { type: String, required: true },
    translation: { type: String, required: true },
    example1: { type: String },
    example1Translation: { type: String },
    example2: { type: String },
    example2Translation: { type: String },
  },
  { timestamps: true }
);

const Word = mongoose.model("Word", wordSchema);

module.exports = Word;
