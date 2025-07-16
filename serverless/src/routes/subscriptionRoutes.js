// {PATH_TO_PROJECT}/src/routes/subscriptionRoutes.js

import { Hono } from "hono";
import { protectAndLoadUser } from "../middleware/authMiddleware";
// --- 1. IMPORT THE MIDDLEWARE ---
import { checkSubscriptionStatus } from "../middleware/subscriptionMiddleware";
import { verifyAndGrantAccess, startGoldTrial } from "../controllers/subscriptionController";

const subscriptionRoutes = new Hono();

// --- 2. APPLY BOTH MIDDLEWARES TO ALL ROUTES ---
subscriptionRoutes.use("/*", protectAndLoadUser, checkSubscriptionStatus);

subscriptionRoutes.post("/verify-purchase", verifyAndGrantAccess);
subscriptionRoutes.post("/start-trial", startGoldTrial);

export default subscriptionRoutes;
