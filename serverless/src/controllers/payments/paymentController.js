// serverless/src/controllers/payments/paymentController.js

import * as paymentService from "../../services/paymentService";
import { db } from "../../db/d1-client";
import { verifyWebhookSignature } from "../../services/providers/clickService";
import PLANS from "../../config/plans";

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

/**
 * @desc    Handles incoming webhooks from Click (Prepare & Complete)
 * @route   POST /api/payment/click/webhook
 * @access  Public (Signature-verified)
 */
export const handleСlickWebhook = async (c) => {
  const data = await c.req.json();
  const { action, error, merchant_trans_id, amount, merchant_prepare_id } = data;

  // 1. Verify the signature
  if (!verifyWebhookSignature(c, data)) {
    return c.json({ error: -1, error_note: "SIGN CHECK FAILED!" });
  }

  // 2. Check for external errors reported by Click
  if (error < 0) {
    // Transaction failed on Click's side, update our internal record
    await db.updatePaymentTransaction(c.env.DB, merchant_trans_id, {
      status: "FAILED",
      providerTransactionId: data.click_trans_id,
    });
    return c.json({ error: -9, error_note: "Transaction cancelled" });
  }

  // 3. Find our internal transaction record
  const transaction = await db.getPaymentTransaction(c.env.DB, merchant_trans_id);
  if (!transaction) {
    return c.json({ error: -5, error_note: "User does not exist" });
  }

  // 4. Validate the amount
  if (Math.abs(parseInt(amount, 10) * 100 - transaction.amount) > 1) {
    return c.json({ error: -2, error_note: "Incorrect parameter amount" });
  }

  const responsePayload = {
    click_trans_id: data.click_trans_id,
    merchant_trans_id: merchant_trans_id,
    merchant_prepare_id: merchant_trans_id, // For 'prepare', this becomes the prepare_id
    error: 0,
    error_note: "Success",
  };

  if (action === "0") {
    // --- PREPARE ---
    if (transaction.status !== "PENDING") {
      return c.json({ error: -4, error_note: "Already paid" });
    }
    return c.json(responsePayload);
  } else if (action === "1") {
    // --- COMPLETE ---
    if (transaction.id !== merchant_prepare_id) {
      return c.json({ error: -6, error_note: "Transaction not found" });
    }
    if (transaction.status === "COMPLETED") {
      return c.json({ error: -4, error_note: "Already paid" });
    }

    // Grant subscription
    const plan = Object.values(PLANS).find((p) => p.providerIds.click === transaction.planId);
    if (!plan) {
      // Critical internal error, but we must respond to Click successfully
      console.error(`Webhook success, but plan not found for ID: ${transaction.planId}`);
      responsePayload.merchant_confirm_id = transaction.id;
      return c.json(responsePayload);
    }
    const user = await db.getUserById(c.env.DB, transaction.userId);
    const now = new Date();
    const startDate =
      user.subscription_expiresAt && new Date(user.subscription_expiresAt) > now
        ? new Date(user.subscription_expiresAt)
        : now;
    const newExpiresAt = new Date(startDate);
    newExpiresAt.setDate(newExpiresAt.getDate() + plan.durationDays);

    await db.updateUserSubscription(c.env.DB, user.id, {
      tier: plan.tier,
      expiresAt: newExpiresAt.toISOString(),
    });

    await db.updatePaymentTransaction(c.env.DB, transaction.id, {
      status: "COMPLETED",
      providerTransactionId: data.click_trans_id,
    });

    responsePayload.merchant_confirm_id = transaction.id;
    return c.json(responsePayload);
  }

  return c.json({ error: -3, error_note: "Action not found" });
};
