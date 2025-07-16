// {PATH_TO_PROJECT}/src/routes/ieltsExamRoutes.js

import { Hono } from "hono";
import { protectAndLoadUser } from "../middleware/authMiddleware";
import { startExam, handleExamStep, analyzeExam } from "../controllers/ieltsExamController";

const ieltsExamRoutes = new Hono();

// Apply `protect` middleware to all routes in this file
ieltsExamRoutes.use("/*", protectAndLoadUser);

// Define the routes
ieltsExamRoutes.post("/start", startExam);
ieltsExamRoutes.post("/step", handleExamStep);
ieltsExamRoutes.post("/analyze", analyzeExam);

export default ieltsExamRoutes;
