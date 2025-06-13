// routes/examRoutes.js
const express = require("express");
const {
  startExam,
  handleExamStep,
  analyzeExam,
  getExamHistory,
  getExamResultDetails,
} = require("../controllers/examController");
const { protect } = require("../middleware/authMiddleware");

const router = express.Router();

// Apply protect middleware to all exam routes to ensure user is logged in
router.use(protect);

// Define the routes
router.post("/start", startExam);
router.post("/step", handleExamStep);
router.post("/analyze", analyzeExam);
router.get("/history", getExamHistory);
router.get("/result/:resultId", getExamResultDetails);

module.exports = router;
