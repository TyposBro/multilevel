const express = require("express");
const multer = require("multer");

// Import all required middleware and controllers
const { protectAdmin } = require("../middleware/adminMiddleware");
const { loginAdmin } = require("../controllers/adminAuthController");
const {
  uploadPart1_1,
  uploadPart1_2,
  uploadPart2,
  uploadPart3,
} = require("../controllers/adminContentController");

const router = express.Router();

// Configure multer for in-memory storage, which is required by our storage service.
const storage = multer.memoryStorage();
const upload = multer({
  storage: storage,
  limits: { fileSize: 10 * 1024 * 1024 }, // Optional: Set a file size limit (e.g., 10MB)
});

// --- Public Admin Authentication Route ---
// This route is not protected, allowing admins to log in.
router.post("/auth/login", loginAdmin);

// --- Protected Content Management Routes ---
// The `protectAdmin` middleware is applied to all subsequent routes in this file.
// Any request to these endpoints must include a valid admin JWT.
router.use(protectAdmin);

/**
 * @route   POST /api/admin/content/part1.1
 * @desc    Uploads a single question for Part 1.1
 * @access  Private (Admin only)
 * @expects A `multipart/form-data` request with:
 *          - A text field named `questionText`
 *          - A single file field named `audio`
 */
router.post("/content/part1.1", upload.single("audio"), uploadPart1_1);

/**
 * @route   POST /api/admin/content/part1.2
 * @desc    Uploads a complete set for Part 1.2
 * @access  Private (Admin only)
 * @expects A `multipart/form-data` request with multiple fields.
 *          The `upload.fields()` middleware handles these specifically.
 */
router.post(
  "/content/part1.2",
  upload.fields([
    { name: "image1", maxCount: 1 },
    { name: "image2", maxCount: 1 },
    { name: "audio1", maxCount: 1 },
    { name: "audio2", maxCount: 1 },
    { name: "audio3", maxCount: 1 },
  ]),
  uploadPart1_2
);

/**
 * @route   POST /api/admin/content/part2
 * @desc    Uploads a complete set for Part 2
 * @access  Private (Admin only)
 * @expects A `multipart/form-data` request with two named files.
 */
router.post(
  "/content/part2",
  upload.fields([
    { name: "image", maxCount: 1 },
    { name: "audio", maxCount: 1 }, // This is the combined audio for all 3 questions
  ]),
  uploadPart2
);

/**
 * @route   POST /api/admin/content/part3
 * @desc    Uploads a complete topic for Part 3
 * @access  Private (Admin only)
 * @expects A `multipart/form-data` request with:
 *          - Text fields for topic, forPoints, againstPoints
 *          - An optional single file field named `image`
 */
router.post("/content/part3", upload.single("image"), uploadPart3);

module.exports = router;
