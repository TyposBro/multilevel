// {PATH_TO_PROJECT}/src/routes/multilevelExamRoutes.js

import { Hono } from "hono";
import { protectAndLoadUser } from "../middleware/authMiddleware";
import { checkSubscriptionStatus } from "../middleware/subscriptionMiddleware";
import {
  generateNewExam,
  analyzeExam,
  getExamHistory,
  getExamResultDetails,
} from "../controllers/multilevelExamController";

const multilevelExamRoutes = new Hono();

// Apply the entire middleware chain to all routes in this file
multilevelExamRoutes.use("*", protectAndLoadUser, checkSubscriptionStatus);

multilevelExamRoutes.get("/new", generateNewExam);
multilevelExamRoutes.post("/analyze", analyzeExam);
multilevelExamRoutes.get("/history", getExamHistory);
multilevelExamRoutes.get("/result/:resultId", getExamResultDetails);

export default multilevelExamRoutes;
