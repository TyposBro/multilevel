// {PATH_TO_PROJECT}/api/routes/authRoutes.js
const express = require("express");
const {
  registerUser,
  loginUser,
  getUserProfile,
  googleSignIn,
} = require("../controllers/authController"); // Adjust path
const { protect } = require("../middleware/authMiddleware"); // Adjust path

const router = express.Router();

// --- Social Sign-In route ---
router.post("/google-signin", googleSignIn);

router.post("/register", registerUser);
router.post("/login", loginUser);
router.get("/profile", protect, getUserProfile); // Protect this route

module.exports = router;
