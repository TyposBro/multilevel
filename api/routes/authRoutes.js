// {PATH_TO_PROJECT}/api/routes/authRoutes.js
const express = require("express");
const {
  registerUser,
  loginUser,
  getUserProfile,
  googleSignIn,
  deleteUserProfile,
} = require("../controllers/authController"); // Adjust path
const { protect } = require("../middleware/authMiddleware"); // Adjust path

const router = express.Router();

// --- Social Sign-In route ---
router.post("/google-signin", googleSignIn);

router.post("/register", registerUser);
router.post("/login", loginUser);

router
  .route("/profile")
  .get(protect, getUserProfile) // GET /api/auth/profile
  .delete(protect, deleteUserProfile); // DELETE /api/auth/profile

module.exports = router;
