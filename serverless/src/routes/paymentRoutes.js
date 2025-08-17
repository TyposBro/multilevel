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
  console.log("=== WEBHOOK GET ENDPOINT HIT ===");
  console.log("Method:", c.req.method);
  console.log("URL:", c.req.url);
  console.log("Timestamp:", new Date().toISOString());

  return c.json({
    status: "success",
    message: "Webhook endpoint is accessible",
    timestamp: new Date().toISOString(),
    environment: c.env.ENVIRONMENT || "not-set"
  });
});

// Simple POST endpoint that accepts anything
paymentRoutes.post("/click/webhook-simple", async (c) => {
  console.log("=== SIMPLE WEBHOOK POST ===");
  console.log("Method:", c.req.method);
  console.log("URL:", c.req.url);
  console.log("Timestamp:", new Date().toISOString());
  
  let body = '';
  try {
    body = await c.req.text();
    console.log("Raw body:", body);
  } catch (e) {
    console.log("Could not read body:", e.message);
  }

  // Always return success for testing
  return c.json({
    error: 0,
    error_note: "Success",
    click_trans_id: 12345,
    merchant_trans_id: "test",
    merchant_prepare_id: "test"
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
