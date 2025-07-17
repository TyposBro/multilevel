// {PATH_TO_PROJECT}/src/routes/examRoutes.js

import { Hono } from "hono";
import { protectAndLoadUser } from "../middleware/authMiddleware";
import { checkSubscriptionStatus } from "../middleware/subscriptionMiddleware";
import { generateNewExam, analyzeExam } from "../controllers/examController";

const examRoutes = new Hono();

// Apply the entire middleware chain to all routes in this file
examRoutes.use("*", protectAndLoadUser, checkSubscriptionStatus);

examRoutes.get("/new", generateNewExam);
examRoutes.post("/analyze", analyzeExam);

export default examRoutes;
