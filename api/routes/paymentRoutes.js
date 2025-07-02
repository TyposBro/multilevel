const express = require("express");
const router = express.Router();
const { protect } = require("../middleware/authMiddleware");
const { createPayment, verifyPayment } = require("../controllers/payments/paymentController");

// All payment routes should be protected
router.use(protect);

// Route to create the payment and get the URL
router.post("/create", createPayment);

// Route to verify the payment after user returns from Payme/Click
router.post("/verify", verifyPayment);

module.exports = router;
