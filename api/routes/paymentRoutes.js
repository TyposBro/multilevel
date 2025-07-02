// {PATH_TO_PROJECT}/api/routes/paymentRoutes.js

const express = require("express");
const router = express.Router();
const { protect } = require("../middleware/authMiddleware");
const { createPayment, getPaymentStatus } = require("../controllers/payments/paymentController");

// All payment routes should be protected
router.use(protect);

// The client sends the provider in the request body
router.post("/create", createPayment);

// The route now includes the provider for the status check
router.get("/status/:provider/:transactionId", getPaymentStatus);

module.exports = router;
