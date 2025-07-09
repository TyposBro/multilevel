// {PATH_TO_PROJECT}/src/controllers/payments/paymentController.js

import * as paymentService from "../../services/paymentService";

/**
 * @desc    Create a new payment for a specified provider
 * @route   POST /api/payment/create
 * @access  Private
 */
export const createPayment = async (c) => {
  try {
    const { provider, planId } = await c.req.json();
    const user = c.get("user");

    if (!provider || !planId) {
      return c.json({ message: "Provider and planId are required." }, 400);
    }

    const result = await paymentService.initiatePayment(c, provider, planId, user.id);
    return c.json(result, 201);
  } catch (error) {
    console.error("Error in createPayment controller:", error);
    return c.json({ message: error.message || "Server error while creating payment" }, 500);
  }
};

/**
 * @desc    Verifies a purchase after the user returns from the payment provider.
 * @route   POST /api/payment/verify
 * @access  Private
 */
export const verifyPayment = async (c) => {
  try {
    const { provider, token } = await c.req.json(); // `token` is the receiptId
    const user = c.get("user");

    if (!provider || !token) {
      return c.json({ message: "Provider and transaction token are required." }, 400);
    }

    const result = await paymentService.verifyPurchase(c, provider, token, user);

    if (result.success) {
      return c.json({ message: result.message, subscription: result.subscription });
    } else {
      return c.json({ message: result.message }, 400);
    }
  } catch (error) {
    console.error("Error in verifyPayment controller:", error);
    return c.json({ message: "Server error during payment verification" }, 500);
  }
};
