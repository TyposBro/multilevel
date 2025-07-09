// {PATH_TO_PROJECT}/src/routes/subscriptionRoutes.js

import { Hono } from "hono";
import { protectAndLoadUser } from "../middleware/authMiddleware";
import { verifyAndGrantAccess, startGoldTrial } from "../controllers/subscriptionController";

const subscriptionRoutes = new Hono();

// All subscription routes should be protected
subscriptionRoutes.use("/*", protectAndLoadUser);

subscriptionRoutes.post("/verify-purchase", verifyAndGrantAccess);
subscriptionRoutes.post("/start-trial", startGoldTrial);

export default subscriptionRoutes;
