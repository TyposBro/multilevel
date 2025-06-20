const express = require("express");
const multer = require("multer");
const { protectAdmin } = require("../middleware/adminMiddleware"); // Use new middleware
const { loginAdmin } = require("../controllers/adminAuthController");
const { uploadPart1_2 } = require("../controllers/adminContentController");

const router = express.Router();

// --- Public Admin Routes ---
router.post("/auth/login", loginAdmin);

// --- Protected Admin Content Routes ---
const storage = multer.memoryStorage();
const upload = multer({ storage: storage });

// Apply the protectAdmin middleware to all routes below this point
router.use(protectAdmin);

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

// Example for another part
// router.post("/content/part2", ..., uploadPart2);

module.exports = router;
