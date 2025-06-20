// {PATH_TO_PROJECT}/api/models/multilevelExamResultModel.js

const mongoose = require("mongoose");

const transcriptEntrySchema = new mongoose.Schema({
    speaker: { type: String, enum: ["Examiner", "User"], required: true },
    text: { type: String, required: true },
    part: { type: String, enum: ["1.1", "1.2", "2", "3"] }
}, { _id: false });

const feedbackSchema = new mongoose.Schema({
    part: { type: String, required: true },
    score: { type: Number, required: true },
    feedback: { type: String, required: true },
}, { _id: false });

const multilevelExamResultSchema = new mongoose.Schema({
    userId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: "User",
        required: true,
        index: true,
    },
    totalScore: {
        type: Number,
        required: true,
        min: 0,
        max: 72
    },
    feedbackBreakdown: [feedbackSchema],
    transcript: [transcriptEntrySchema],
    examContent: {
        part1_1: [mongoose.Schema.Types.ObjectId],
        part1_2: mongoose.Schema.Types.ObjectId,
        part2: mongoose.Schema.Types.ObjectId,
        part3: mongoose.Schema.Types.ObjectId
    }
}, {
    timestamps: true,
});

module.exports = mongoose.model("MultilevelExamResult", multilevelExamResultSchema);