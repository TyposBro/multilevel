// {PATH_TO_PROJECT}/src/routes/multilevelExamRoutes.js

import { Hono } from "hono";
import { protect } from "../middleware/authMiddleware";
import { checkSubscriptionStatus } from "../middleware/subscriptionMiddleware";
import {
  generateNewExam,
  analyzeExam,
  getExamHistory,
  getExamResultDetails,
} from "../controllers/multilevelExamController";

const multilevelExamRoutes = new Hono();

// This order is important:
// 1. `protect` ensures we have a user from a valid token.
// 2. `checkSubscriptionStatus` uses that user to update their tier if it expired.
// Middleware is applied in the order it is listed.
multilevelExamRoutes.use("/*", protect, checkSubscriptionStatus);

multilevelExamRoutes.get("/new", generateNewExam);
multilevelExamRoutes.post("/analyze", analyzeExam);
multilevelExamRoutes.get("/history", getExamHistory);
multilevelExamRoutes.get("/result/:resultId", getExamResultDetails);

export default multilevelExamRoutes;
