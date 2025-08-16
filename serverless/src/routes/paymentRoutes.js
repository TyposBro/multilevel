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
// Add this to your paymentRoutes.js

// Test endpoint to verify webhook is accessible
paymentRoutes.get("/click/webhook", (c) => {
  console.log("=== WEBHOOK TEST ENDPOINT HIT ===");
  console.log("Method:", c.req.method);
  console.log("URL:", c.req.url);
  console.log("Headers:", Object.fromEntries(c.req.headers.entries()));
  console.log("Query params:", c.req.query);

  return c.json({
    status: "success",
    message: "Webhook endpoint is accessible",
    timestamp: new Date().toISOString(),
    environment: c.env.ENVIRONMENT || "not-set",
  });
});

paymentRoutes.post("/click/webhook", handleСlickWebhook);

// All other payment routes should be protected
paymentRoutes.use("/create", protectAndLoadUser);
paymentRoutes.use("/verify", protectAndLoadUser);

// Route to create the payment and get the URL/params
paymentRoutes.post("/create", createPayment);

// Route to verify the payment after user returns from providers
paymentRoutes.post("/verify", verifyPayment);

export default paymentRoutes;
