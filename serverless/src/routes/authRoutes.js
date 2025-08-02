// {PATH_TO_PROJECT}/serverless/src/routes/authRoutes.js
import { Hono } from "hono";
import { protectAndLoadUser } from "../middleware/authMiddleware";
import {
  getUserProfile,
  googleSignIn,
  deleteUserProfile,
  verifyTelegramToken,
  reviewerLogin,
  telegramLoginWeb,
  telegramRedirect,
} from "../controllers/authController";

const authRoutes = new Hono();

// Public routes
authRoutes.post("/google-signin", googleSignIn);
authRoutes.post("/verify-telegram-token", verifyTelegramToken);
authRoutes.post("/reviewer-login", reviewerLogin);
authRoutes.get("/telegram/login-web", telegramLoginWeb);
authRoutes.get("/telegram/redirect", telegramRedirect);
// Group all protected routes under a sub-router
// Use the new single middleware for the profile route
authRoutes.get("/profile", protectAndLoadUser, getUserProfile);
authRoutes.delete("/profile", protectAndLoadUser, deleteUserProfile);

export default authRoutes;
