// serverless/src/routes/paymentRoutes.js

import { Hono } from "hono";
import { protectAndLoadUser } from "../middleware/authMiddleware";
import { proxyAuth } from "../middleware/proxyAuthMiddleware"; // <--- IMPORT MIDDLEWARE
import { createPayment, verifyPayment } from "../controllers/payments/paymentController"; // Assuming original createPayment is still needed
import { handlePrepare, handleComplete } from "../controllers/payments/clickProxyController"; // <--- IMPORT NEW CONTROLLER

const paymentRoutes = new Hono();

// --- PUBLIC WEBHOOKS FOR CLICK (now handled by PHP proxy) ---
// It's good practice to leave a note or remove these old routes.
// paymentRoutes.post("/click/webhook", handleСlickWebhook);

// --- NEW SECURE PROXY ENDPOINTS FOR PHP ---
paymentRoutes.post("/click/prepare", proxyAuth, handlePrepare);
paymentRoutes.post("/click/complete", proxyAuth, handleComplete);

// --- PROTECTED ROUTES FOR YOUR APP'S FRONTEND ---
paymentRoutes.use("/create", protectAndLoadUser);
paymentRoutes.use("/verify", protectAndLoadUser);

paymentRoutes.post("/create", createPayment);
paymentRoutes.post("/verify", verifyPayment);

export default paymentRoutes;
