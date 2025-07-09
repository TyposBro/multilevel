// {PATH_TO_PROJECT}/src/routes/ieltsExamRoutes.js

import { Hono } from "hono";
import { protect } from "../middleware/authMiddleware";
import {
  startExam,
  handleExamStep,
  analyzeExam,
  getExamHistory,
  getExamResultDetails,
} from "../controllers/ieltsExamController";

const ieltsExamRoutes = new Hono();

// Apply `protect` middleware to all routes in this file
ieltsExamRoutes.use("/*", protect);

// Define the routes
ieltsExamRoutes.post("/start", startExam);
ieltsExamRoutes.post("/step", handleExamStep);
ieltsExamRoutes.post("/analyze", analyzeExam);
ieltsExamRoutes.get("/history", getExamHistory);
ieltsExamRoutes.get("/result/:resultId", getExamResultDetails);

export default ieltsExamRoutes;
