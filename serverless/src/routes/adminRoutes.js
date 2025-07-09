// {PATH_TO_PROJECT}/src/routes/adminRoutes.js

import { Hono } from "hono";
import { protectAdmin } from "../middleware/adminMiddleware";

// Import all required controllers
import { loginAdmin } from "../controllers/adminAuthController";
import {
  uploadPart1_1,
  uploadPart1_2,
  uploadPart2,
  uploadPart3,
  uploadWordBankWord,
} from "../controllers/adminContentController";

const adminRoutes = new Hono();

// --- Public Admin Authentication Route ---
adminRoutes.post("/auth/login", loginAdmin);

// --- Protected Content Management Routes ---
// The `protectAdmin` middleware is applied to each subsequent route individually.

/**
 * @route   POST /api/admin/content/part1.1
 * @desc    Uploads a single question for Part 1.1
 * @access  Private (Admin only)
 */
adminRoutes.post("/content/part1.1", protectAdmin, uploadPart1_1);

/**
 * @route   POST /api/admin/content/part1.2
 * @desc    Uploads a complete set for Part 1.2
 * @access  Private (Admin only)
 */
adminRoutes.post("/content/part1.2", protectAdmin, uploadPart1_2);

/**
 * @route   POST /api/admin/content/part2
 * @desc    Uploads a complete set for Part 2
 * @access  Private (Admin only)
 */
adminRoutes.post("/content/part2", protectAdmin, uploadPart2);

/**
 * @route   POST /api/admin/content/part3
 * @desc    Uploads a complete topic for Part 3
 * @access  Private (Admin only)
 */
adminRoutes.post("/content/part3", protectAdmin, uploadPart3);

/**
 * @route   POST /api/admin/wordbank/add
 * @desc    Uploads a new word for the Word Bank
 * @access  Private (Admin only)
 */
adminRoutes.post("/wordbank/add", protectAdmin, uploadWordBankWord);

export default adminRoutes;
