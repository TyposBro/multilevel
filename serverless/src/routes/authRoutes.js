// {PATH_TO_PROJECT}/src/routes/authRoutes.js

import { Hono } from "hono";
import { protect } from "../middleware/authMiddleware";
import {
  getUserProfile,
  googleSignIn,
  deleteUserProfile,
  verifyTelegramToken,
  telegramRedirect,
} from "../controllers/authController";

const authRoutes = new Hono();

// --- Public Social Sign-In and Telegram Routes ---
authRoutes.post("/google-signin", googleSignIn);
authRoutes.post("/verify-telegram-token", verifyTelegramToken);
authRoutes.get("/telegram/redirect", telegramRedirect);

// --- Protected Profile Route ---
authRoutes.get("/profile", protect, getUserProfile);
authRoutes.delete("/profile", protect, deleteUserProfile);

export default authRoutes;
