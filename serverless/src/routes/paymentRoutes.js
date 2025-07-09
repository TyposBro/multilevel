// {PATH_TO_PROJECT}/src/routes/paymentRoutes.js

import { Hono } from "hono";
import { protectAndLoadUser } from "../middleware/authMiddleware";
import { createPayment, verifyPayment } from "../controllers/payments/paymentController";

const paymentRoutes = new Hono();

// All payment routes should be protected
paymentRoutes.use("/*", protectAndLoadUser);

// Route to create the payment and get the URL
paymentRoutes.post("/create", createPayment);

// Route to verify the payment after user returns from Payme/Click
paymentRoutes.post("/verify", verifyPayment);

export default paymentRoutes;
