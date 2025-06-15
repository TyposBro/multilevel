// {PATH_TO_PROJECT}/api/routes/examRoutes.js
const express = require("express");
const {
  startExam,
  handleExamStepStream, // Import the new function
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
router.post("/step-stream", handleExamStepStream);
router.post("/analyze", analyzeExam);
router.get("/history", getExamHistory);
router.get("/result/:resultId", getExamResultDetails);

module.exports = router;
