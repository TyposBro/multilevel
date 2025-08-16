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
  // Log all incoming webhook data
  console.log("=== CLICK WEBHOOK RECEIVED ===");
  console.log("Method:", c.req.method);
  console.log("URL:", c.req.url);
  // console.log("Headers:", Object.fromEntries(c.req.headers.entries()));

  let data;
  try {
    data = await c.req.json();
    console.log("Raw webhook data:", JSON.stringify(data, null, 2));
  } catch (error) {
    console.error("Failed to parse webhook JSON:", error);
    return c.json({ error: -8, error_note: "Error in request from click" });
  }

  const {
    action,
    error,
    merchant_trans_id,
    amount,
    merchant_prepare_id,
    service_id,
    click_trans_id,
    sign_string,
    sign_time,
  } = data;

  console.log("Parsed webhook parameters:");
  console.log("- action:", action);
  console.log("- error:", error);
  console.log("- merchant_trans_id:", merchant_trans_id);
  console.log("- amount:", amount);
  console.log("- merchant_prepare_id:", merchant_prepare_id);
  console.log("- service_id:", service_id);
  console.log("- click_trans_id:", click_trans_id);
  console.log("- sign_string:", sign_string);
  console.log("- sign_time:", sign_time);

  // 1. Verify the signature
  console.log("=== SIGNATURE VERIFICATION ===");
  const signatureValid = verifyWebhookSignature(c, data);
  console.log("Signature verification result:", signatureValid);

  if (!signatureValid) {
    console.log("SIGNATURE VERIFICATION FAILED!");
    return c.json({ error: -1, error_note: "SIGN CHECK FAILED!" });
  }
  console.log("Signature verification PASSED");

  // 2. Check for external errors reported by Click
  console.log("=== ERROR CHECK ===");
  if (error < 0) {
    console.log(`Click reported error: ${error}`);
    // Transaction failed on Click's side, update our internal record
    try {
      await db.updatePaymentTransaction(c.env.DB, merchant_trans_id, {
        status: "FAILED",
        providerTransactionId: click_trans_id,
      });
      console.log("Updated transaction status to FAILED");
    } catch (dbError) {
      console.error("Failed to update transaction status:", dbError);
    }
    return c.json({ error: -9, error_note: "Transaction cancelled" });
  }
  console.log("No external errors from Click");

  // 3. Find our internal transaction record
  console.log("=== TRANSACTION LOOKUP ===");
  console.log("Looking up transaction with ID:", merchant_trans_id);

  let transaction;
  try {
    transaction = await db.getPaymentTransaction(c.env.DB, merchant_trans_id);
    console.log(
      "Transaction found:",
      transaction ? JSON.stringify(transaction, null, 2) : "NOT FOUND"
    );
  } catch (dbError) {
    console.error("Database error during transaction lookup:", dbError);
    return c.json({ error: -8, error_note: "Error in request from click" });
  }

  if (!transaction) {
    console.log("Transaction not found in database");
    return c.json({ error: -5, error_note: "User does not exist" });
  }

  // 4. Look up the plan using the service_id from the webhook
  console.log("=== PLAN LOOKUP ===");
  console.log("Looking up plan for service_id:", service_id);
  console.log("Available plans:", Object.keys(PLANS));

  const plan = Object.values(PLANS).find((p) => {
    console.log(
      `Checking plan with click service ID: ${p.providerIds?.click} against ${service_id}`
    );
    return p.providerIds?.click === service_id.toString();
  });

  console.log("Plan found:", plan ? JSON.stringify(plan, null, 2) : "NOT FOUND");

  if (!plan) {
    console.error(`CRITICAL: Plan not found for service_id: ${service_id}`);
    console.log("All available plans and their Click service IDs:");
    Object.entries(PLANS).forEach(([key, planData]) => {
      console.log(`- ${key}: ${planData.providerIds?.click || "NO CLICK ID"}`);
    });
    return c.json({ error: -3, error_note: "Action not found" });
  }

  // 5. Validate the amount
  console.log("=== AMOUNT VALIDATION ===");
  const webhookAmountInTiyin = parseInt(amount, 10) * 100;
  const transactionAmountInTiyin = transaction.amount;
  const amountDifference = Math.abs(webhookAmountInTiyin - transactionAmountInTiyin);

  console.log("Webhook amount (tiyin):", webhookAmountInTiyin);
  console.log("Transaction amount (tiyin):", transactionAmountInTiyin);
  console.log("Amount difference:", amountDifference);

  if (amountDifference > 1) {
    console.log("AMOUNT VALIDATION FAILED!");
    return c.json({ error: -2, error_note: "Incorrect parameter amount" });
  }
  console.log("Amount validation PASSED");

  const responsePayload = {
    click_trans_id: click_trans_id,
    merchant_trans_id: merchant_trans_id,
    merchant_prepare_id: merchant_trans_id,
    error: 0,
    error_note: "Success",
  };

  if (action === 0) {
    console.log("=== PREPARE ACTION ===");
    console.log("Current transaction status:", transaction.status);

    if (transaction.status !== "PENDING") {
      console.log("Transaction already processed, status:", transaction.status);
      return c.json({ error: -4, error_note: "Already paid" });
    }

    // Additional validation for prepare
    console.log("Loading user for validation...");
    let user;
    try {
      user = await db.getUserById(c.env.DB, transaction.userId);
      console.log("User found:", user ? `ID: ${user.id}` : "NOT FOUND");
    } catch (dbError) {
      console.error("Database error during user lookup:", dbError);
      return c.json({ error: -8, error_note: "Error in request from click" });
    }

    if (!user) {
      console.log("User not found in database");
      return c.json({ error: -5, error_note: "User does not exist" });
    }

    console.log("PREPARE successful, sending response:", JSON.stringify(responsePayload, null, 2));
    return c.json(responsePayload);
  } else if (action === 1) {
    console.log("=== COMPLETE ACTION ===");
    console.log("merchant_prepare_id from webhook:", merchant_prepare_id);
    console.log("transaction.id from database:", transaction.id);

    if (transaction.id !== merchant_prepare_id) {
      console.log("PREPARE ID MISMATCH!");
      return c.json({ error: -6, error_note: "Transaction does not exist" });
    }

    if (transaction.status === "COMPLETED") {
      console.log("Transaction already completed");
      return c.json({ error: -4, error_note: "Already paid" });
    }

    console.log("Processing successful payment...");

    // Process the successful payment
    let user;
    try {
      user = await db.getUserById(c.env.DB, transaction.userId);
      console.log("User for subscription update:", user ? `ID: ${user.id}` : "NOT FOUND");
    } catch (dbError) {
      console.error("Database error during user lookup for completion:", dbError);
      // Still return success to Click, but log the error
      responsePayload.merchant_confirm_id = transaction.id;
      return c.json(responsePayload);
    }

    if (user) {
      const now = new Date();
      const startDate =
        user.subscription_expiresAt && new Date(user.subscription_expiresAt) > now
          ? new Date(user.subscription_expiresAt)
          : now;
      const newExpiresAt = new Date(startDate);
      newExpiresAt.setDate(newExpiresAt.getDate() + plan.durationDays);

      console.log("Updating user subscription:");
      console.log("- Current expires at:", user.subscription_expiresAt);
      console.log("- New expires at:", newExpiresAt.toISOString());
      console.log("- New tier:", plan.tier);

      try {
        await db.updateUserSubscription(c.env.DB, user.id, {
          tier: plan.tier,
          expiresAt: newExpiresAt.toISOString(),
        });
        console.log("User subscription updated successfully");
      } catch (dbError) {
        console.error("Failed to update user subscription:", dbError);
      }
    }

    try {
      await db.updatePaymentTransaction(c.env.DB, transaction.id, {
        status: "COMPLETED",
        providerTransactionId: click_trans_id,
      });
      console.log("Transaction updated to COMPLETED");
    } catch (dbError) {
      console.error("Failed to update transaction status:", dbError);
    }

    responsePayload.merchant_confirm_id = transaction.id;
    console.log("COMPLETE successful, sending response:", JSON.stringify(responsePayload, null, 2));
    return c.json(responsePayload);
  }

  console.log("=== UNKNOWN ACTION ===");
  console.log("Received unknown action:", action);
  return c.json({ error: -3, error_note: "Action not found" });
};
