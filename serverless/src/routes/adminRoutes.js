// {PATH_TO_PROJECT}/src/routes/adminRoutes.js

import { Hono } from "hono";
import { protectAdmin } from "../middleware/adminMiddleware";
import { loginAdmin } from "../controllers/adminAuthController";
import {
  // Create (POST)
  uploadPart1_1,
  uploadPart1_2,
  uploadPart2,
  uploadPart3,
  createWordBankWord,

  // Read (GET)
  listPart1_1,
  getPart1_1,
  listPart1_2,
  getPart1_2,
  listPart2,
  getPart2,
  listPart3,
  getPart3,
  listWordBankWords,
  getWordBankWord,

  // Update (POST for FormData)
  updatePart1_1,
  updatePart1_2,
  updatePart2,
  updatePart3,
  updateWordBankWord,

  // Delete (DELETE)
  deletePart1_1,
  deletePart1_2,
  deletePart2,
  deletePart3,
  deleteWordBankWord,
} from "../controllers/adminContentController";

const adminRoutes = new Hono();

// --- Public Admin Authentication Route ---
adminRoutes.post("/auth/login", loginAdmin);

// --- Protected Content Management Routes ---
// == Part 1.1 ==
adminRoutes.get("/content/part1.1", protectAdmin, listPart1_1);
adminRoutes.post("/content/part1.1", protectAdmin, uploadPart1_1);
adminRoutes.get("/content/part1.1/:id", protectAdmin, getPart1_1);
adminRoutes.post("/content/part1.1/:id", protectAdmin, updatePart1_1);
adminRoutes.delete("/content/part1.1/:id", protectAdmin, deletePart1_1);

// == Part 1.2 ==
adminRoutes.get("/content/part1.2", protectAdmin, listPart1_2);
adminRoutes.post("/content/part1.2", protectAdmin, uploadPart1_2);
adminRoutes.get("/content/part1.2/:id", protectAdmin, getPart1_2);
adminRoutes.post("/content/part1.2/:id", protectAdmin, updatePart1_2); // Note: This is text-only for now
adminRoutes.delete("/content/part1.2/:id", protectAdmin, deletePart1_2);

// == Part 2 ==
adminRoutes.get("/content/part2", protectAdmin, listPart2);
adminRoutes.post("/content/part2", protectAdmin, uploadPart2);
adminRoutes.get("/content/part2/:id", protectAdmin, getPart2);
adminRoutes.post("/content/part2/:id", protectAdmin, updatePart2);
adminRoutes.delete("/content/part2/:id", protectAdmin, deletePart2);

// == Part 3 ==
adminRoutes.get("/content/part3", protectAdmin, listPart3);
adminRoutes.post("/content/part3", protectAdmin, uploadPart3);
adminRoutes.get("/content/part3/:id", protectAdmin, getPart3);
adminRoutes.post("/content/part3/:id", protectAdmin, updatePart3); // Note: This is text-only for now
adminRoutes.delete("/content/part3/:id", protectAdmin, deletePart3);

// == Word Bank ==
adminRoutes.get("/wordbank", protectAdmin, listWordBankWords);
adminRoutes.post("/wordbank", protectAdmin, createWordBankWord);
adminRoutes.get("/wordbank/:id", protectAdmin, getWordBankWord);
adminRoutes.post("/wordbank/:id", protectAdmin, updateWordBankWord);
adminRoutes.delete("/wordbank/:id", protectAdmin, deleteWordBankWord);

export default adminRoutes;
