// {PATH_TO_PROJECT}/api/controllers/providers/paymentController.js
const paymentService = require("../../services/paymentService");

/**
 * @desc    Create a new payment for a specified provider
 * @route   POST /api/payment/create
 * @access  Private
 */
const createPayment = async (req, res) => {
  // The client now specifies which provider to use.
  const { provider, planId } = req.body;
  const userId = req.user.id;

  if (!provider || !planId) {
    return res.status(400).json({ message: "Provider and planId are required." });
  }

  try {
    const result = await paymentService.initiatePayment(provider, planId, userId);

    // TODO: Save the result.receiptId to your database with a 'pending' status.
    // This is important for verification later.

    res.status(201).json(result);
  } catch (error) {
    res.status(500).json({ message: error.message || "Server error while creating payment" });
  }
};

/**
 * @desc    Check the status of a payment
 * @route   GET /api/payment/status/:provider/:transactionId
 * @access  Private
 */
const getPaymentStatus = async (req, res) => {
  const { provider, transactionId } = req.params;

  try {
    const result = await paymentService.checkPaymentStatus(provider, transactionId);

    if (provider === "payme" && result.state === 4) {
      // TODO: Logic to confirm the order in your database and grant access.
      // Example:
      // const order = await Order.findOneAndUpdate({ receiptId: transactionId }, { status: 'completed' });
      // await grantSubscription(order.userId, order.planId);
    }

    res.status(200).json(result);
  } catch (error) {
    res.status(500).json({ message: error.message || "Server error while checking status" });
  }
};

module.exports = {
  createPayment,
  getPaymentStatus,
};
