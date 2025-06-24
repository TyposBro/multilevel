// {PATH_TO_PROJECT}/api/routes/multilevelExamRoutes.js

const express = require("express");
const {
  generateNewExam,
  analyzeExam,
  getExamHistory,
  getExamResultDetails,
} = require("../controllers/multilevelExamController");
const { protect } = require("../middleware/authMiddleware");

const router = express.Router();

router.use(protect);

router.get("/new", generateNewExam);
router.post("/analyze", analyzeExam);
router.get("/history", getExamHistory);
router.get("/result/:resultId", getExamResultDetails);

module.exports = router;
