// in: serverless/src/routes/subscriptionRoutes.js

import { Hono } from "hono";
import { protectAndLoadUser } from "../middleware/authMiddleware";
import { checkSubscriptionStatus } from "../middleware/subscriptionMiddleware";
import {
  verifyAndGrantAccess,
  startGoldTrial,
  getGooglePlaySubscriptionStatus, // This one is still useful for checking status
} from "../controllers/subscriptionController";

const subscriptionRoutes = new Hono();

// Apply the middleware chain to all routes in this file.
// This ensures every request is authenticated and the user's subscription status is fresh.
subscriptionRoutes.use("/*", protectAndLoadUser, checkSubscriptionStatus);

// --- Primary Endpoints ---

// This is now the ONLY endpoint your app needs to call to verify ANY purchase.
subscriptionRoutes.post("/verify-purchase", verifyAndGrantAccess);

// Endpoint for the free trial feature.
subscriptionRoutes.post("/start-trial", startGoldTrial);

// Endpoint for the app to get the live status of a user's subscription from Google.
subscriptionRoutes.get("/google-play-status", getGooglePlaySubscriptionStatus);

export default subscriptionRoutes;
