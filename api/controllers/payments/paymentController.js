const paymentService = require("../../services/paymentService");
const User = require("../../models/userModel");

/**
 * @desc    Create a new payment for a specified provider and get a payment URL
 * @route   POST /api/payment/create
 * @access  Private
 */
const createPayment = async (req, res) => {
  const { provider, planId } = req.body;
  const userId = req.user.id;

  if (!provider || !planId) {
    return res.status(400).json({ message: "Provider and planId are required." });
  }

  try {
    // This call should now work correctly because `initiatePayment` is exported.
    const result = await paymentService.initiatePayment(provider, planId, userId);

    // TODO: Save the result.receiptId to your database with a 'pending' status.
    // This is important for verification later.

    res.status(201).json(result);
  } catch (error) {
    // Send a more informative error message back to the client
    console.error("Error in createPayment controller:", error);
    res.status(500).json({ message: error.message || "Server error while creating payment" });
  }
};

/**
 * @desc    Verifies a purchase after the user returns from the payment provider.
 *          This is a more accurate name for the flow than "getStatus".
 * @route   POST /api/payment/verify
 * @access  Private
 */
const verifyPayment = async (req, res) => {
  const { provider, token } = req.body; // `token` here is the receiptId from Payme

  if (!provider || !token) {
    return res.status(400).json({ message: "Provider and transaction token are required." });
  }

  try {
    const user = await User.findById(req.user.id);
    if (!user) {
      return res.status(404).json({ message: "User not found." });
    }

    // Use the generic verifyPurchase function
    const result = await paymentService.verifyPurchase(provider, token, user);

    if (result.success) {
      res.status(200).json({ message: result.message, subscription: result.subscription });
    } else {
      res.status(400).json({ message: result.message });
    }
  } catch (error) {
    console.error("Error in verifyPayment controller:", error);
    res.status(500).json({ message: error.message || "Server error during payment verification" });
  }
};

module.exports = {
  createPayment,
  verifyPayment,
};
