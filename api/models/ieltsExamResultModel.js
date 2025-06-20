// {PATH_TO_PROJECT}/api/models/ieltsExamResultModel.js
const mongoose = require("mongoose");

const transcriptEntrySchema = new mongoose.Schema(
  {
    speaker: {
      type: String,
      enum: ["Examiner", "User"],
      required: true,
    },
    text: {
      type: String,
      required: true,
    },
  },
  { _id: false }
);

const feedbackExampleSchema = new mongoose.Schema(
  {
    userQuote: { type: String, required: true },
    suggestion: { type: String, required: true },
    type: { type: String, required: true }, // e.g., "Grammar", "Vocabulary"
  },
  { _id: false }
);

const scoreCriterionSchema = new mongoose.Schema(
  {
    criterionName: { type: String, required: true }, // e.g., "Fluency & Coherence"
    bandScore: { type: Number, required: true },
    feedback: { type: String, required: true },
    examples: [feedbackExampleSchema],
  },
  { _id: false }
);

const examResultSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    overallBand: {
      type: Number,
      required: true,
    },
    criteria: [scoreCriterionSchema],
    transcript: [transcriptEntrySchema],
  },
  {
    timestamps: true, // Adds createdAt and updatedAt automatically
  }
);

module.exports = mongoose.model("IeltsExamResult", examResultSchema);
