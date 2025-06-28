// {PATH_TO_PROJECT}/api/routes/authRoutes.js
const express = require("express");
const {
  getUserProfile,
  googleSignIn,
  deleteUserProfile,
  verifyTelegramToken,
  telegramRedirect,
} = require("../controllers/authController"); // Adjust path
const { protect } = require("../middleware/authMiddleware"); // Adjust path

const router = express.Router();

// --- Social Sign-In route ---
router.post("/google-signin", googleSignIn);

router.post("/verify-telegram-token", verifyTelegramToken);
router.get("/telegram/redirect", telegramRedirect);

router
  .route("/profile")
  .get(protect, getUserProfile) // GET /api/auth/profile
  .delete(protect, deleteUserProfile); // DELETE /api/auth/profile

module.exports = router;
