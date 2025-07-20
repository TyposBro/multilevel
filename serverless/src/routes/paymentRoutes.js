// serverless/src/routes/paymentRoutes.js

import { Hono } from "hono";
import { protectAndLoadUser } from "../middleware/authMiddleware";
import {
  createPayment,
  verifyPayment,
  handleСlickWebhook,
} from "../controllers/payments/paymentController";

const paymentRoutes = new Hono();

// --- PUBLIC WEBHOOK FOR CLICK ---
// This route must NOT be protected by user auth middleware.
paymentRoutes.post("/click/webhook", handleСlickWebhook);

// All other payment routes should be protected
paymentRoutes.use("/create", protectAndLoadUser);
paymentRoutes.use("/verify", protectAndLoadUser);

// Route to create the payment and get the URL/params
paymentRoutes.post("/create", createPayment);

// Route to verify the payment after user returns from providers
paymentRoutes.post("/verify", verifyPayment);

export default paymentRoutes;
