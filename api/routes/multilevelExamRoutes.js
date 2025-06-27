// {PATH_TO_PROJECT}/api/routes/multilevelExamRoutes.js

const express = require("express");
const {
  generateNewExam,
  analyzeExam,
  getExamHistory,
  getExamResultDetails,
} = require("../controllers/multilevelExamController");
const { protect } = require("../middleware/authMiddleware");
const { checkSubscriptionStatus } = require("../middleware/subscriptionMiddleware"); // Import the new middleware

const router = express.Router();

// This order is important:
// 1. `protect` ensures we have a user from a valid token.
// 2. `checkSubscriptionStatus` uses that user to update their tier if it expired.
router.use(protect);
router.use(checkSubscriptionStatus);

router.get("/new", generateNewExam);
router.post("/analyze", analyzeExam); // This controller will now have the latest user status
router.get("/history", getExamHistory); // This controller is now filtered by tier
router.get("/result/:resultId", getExamResultDetails);

module.exports = router;
