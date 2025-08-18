// Enhanced payment service with Click invoice support

import { db } from "../db/d1-client";
import PLANS from "../config/plans";
import * as paymeService from "./providers/paymeService";
import * as googlePlayService from "./providers/googlePlayService";
import * as clickService from "./providers/clickService";

export const initiatePayment = async (c, provider, planId, userId) => {
  const plan = PLANS[planId];
  if (!plan) {
    throw new Error("Plan not found");
  }

  switch (provider.toLowerCase()) {
    case "payme":
      return paymeService.createTransaction(c, plan, userId);
    case "click":
      // Use the web redirect flow for Click
      return clickService.createTransactionUrl(c, plan, planId, userId);
    default:
      throw new Error(`Unsupported payment provider for creation: ${provider}`);
  }
};

/**
 * Creates a Click invoice for SMS-based payment
 * @param {object} c - The Hono context
 * @param {string} planId - The plan ID
 * @param {string} userId - The user ID
 * @param {string} phoneNumber - User's phone number
 * @returns {Promise<object>} Invoice creation result
 */
export const createClickInvoice = async (c, planId, userId, phoneNumber) => {
  const plan = PLANS[planId];
  if (!plan) {
    throw new Error("Plan not found");
  }

  if (!plan.providerIds?.click) {
    throw new Error("Click service ID not configured for this plan");
  }

  return clickService.createInvoice(c, plan, planId, userId, phoneNumber);
};

/**
 * Main verification function that dispatches to the correct provider service.
 * After successful verification, it updates the user's subscription in the database.
 * @param {object} c - The Hono context.
 * @param {string} provider - The provider name ('google', 'payme', 'click', etc.).
 * @param {string} verificationToken - The purchase token (from Google) or receipt ID (from Payme/Click).
 * @param {object} user - The user object from the database.
 * @param {string} planId - The plan ID from the client, required for some providers.
 * @returns {Promise<{success: boolean, message: string, subscription?: object}>}
 */
export const verifyPurchase = async (c, provider, verificationToken, user, planId) => {
  let verificationResult;

  switch (provider.toLowerCase()) {
    case "google":
      if (!planId) {
        return { success: false, message: "planId is required for Google Play verification." };
      }
      verificationResult = await googlePlayService.verifyGooglePurchase(
        c,
        verificationToken,
        planId
      );
      break;

    case "payme":
      const paymeResult = await paymeService.checkTransaction(c, verificationToken);
      if (!paymeResult) {
        verificationResult = {
          success: false,
          error: "Purchase verification failed with provider.",
        };
        break;
      }
      // Adapt Payme's state to our generic format
      if (paymeResult.state === 4) {
        // State 4 is a successful transaction in Payme
        verificationResult = { ...paymeResult, success: true };
      } else {
        verificationResult = {
          ...paymeResult,
          success: false,
          error: `Invalid Payme transaction state: ${paymeResult.state}`,
        };
      }
      break;

    case "click":
      // For Click, verify the transaction status in our database
      const clickResult = await verifyClickTransaction(c, verificationToken, user);
      verificationResult = clickResult;
      break;

    default:
      return { success: false, message: "Invalid payment provider." };
  }

  // Handle provider-level verification failures first
  if (!verificationResult.success) {
    return { success: false, message: verificationResult.error || "Purchase verification failed." };
  }

  const verifiedPlan = PLANS[verificationResult.planId];
  if (!verifiedPlan) {
    console.error(`FATAL: No plan found for verified planId: ${verificationResult.planId}`);
    return { success: false, message: "Internal server error: Plan not configured." };
  }

  // --- Success Path: Grant subscription ---
  const now = new Date();
  const startDate =
    user.subscription_expiresAt && new Date(user.subscription_expiresAt) > now
      ? new Date(user.subscription_expiresAt)
      : now;

  const newExpiresAt = new Date(startDate);
  newExpiresAt.setDate(newExpiresAt.getDate() + verifiedPlan.durationDays);

  const updatedUser = await db.updateUserSubscription(c.env.DB, user.id, {
    tier: verifiedPlan.tier,
    expiresAt: newExpiresAt.toISOString(),
  });

  console.log(
    `User ${user.id} successfully upgraded to ${
      verifiedPlan.tier
    } until ${newExpiresAt.toISOString()}`
  );

  return {
    success: true,
    message: `Successfully upgraded to ${verifiedPlan.tier}!`,
    subscription: {
      tier: updatedUser.subscription_tier,
      expiresAt: updatedUser.subscription_expiresAt,
    },
  };
};

/**
 * Verifies a Click transaction by checking our internal database
 * @param {object} c - The Hono context
 * @param {string} transactionId - Our internal transaction ID
 * @param {object} user - The user object
 * @returns {Promise<{success: boolean, planId?: string, error?: string}>}
 */
const verifyClickTransaction = async (c, transactionId, user) => {
  try {
    // Get the transaction from our database
    const transaction = await db.getPaymentTransaction(c.env.DB, transactionId);

    if (!transaction) {
      return { success: false, error: "Transaction not found" };
    }

    // Verify the transaction belongs to the user
    if (transaction.userId !== user.id) {
      return { success: false, error: "Unauthorized transaction access" };
    }

    // Check if the transaction is completed
    if (transaction.status !== "COMPLETED") {
      return {
        success: false,
        error: `Transaction not completed. Current status: ${transaction.status}`,
      };
    }

    // Return success with the plan ID
    return {
      success: true,
      planId: transaction.planId,
      transactionId: transaction.id,
      providerTransactionId: transaction.providerTransactionId,
    };
  } catch (error) {
    console.error("Error verifying Click transaction:", error);
    return { success: false, error: "Database error during verification" };
  }
};

/**
 * Check the status of a payment transaction
 * @param {object} c - The Hono context
 * @param {string} transactionId - The internal transaction ID
 * @param {string} userId - The user ID (for authorization)
 * @returns {Promise<object>} Transaction status
 */
export const checkTransactionStatus = async (c, transactionId, userId) => {
  try {
    const transaction = await db.getPaymentTransaction(c.env.DB, transactionId);

    if (!transaction) {
      throw new Error("Transaction not found");
    }

    if (transaction.userId !== userId) {
      throw new Error("Unauthorized");
    }

    let externalStatus = null;

    // For Click invoices, check external status
    if (transaction.provider === "click" && transaction.externalId) {
      try {
        externalStatus = await clickService.checkInvoiceStatus(c, transaction.externalId);
      } catch (error) {
        console.error("Failed to check Click invoice status:", error);
        // Don't fail the whole request if external check fails
      }
    }

    return {
      transactionId: transaction.id,
      status: transaction.status,
      provider: transaction.provider,
      planId: transaction.planId,
      amount: transaction.amount,
      createdAt: transaction.createdAt,
      completedAt: transaction.completedAt,
      providerTransactionId: transaction.providerTransactionId,
      externalId: transaction.externalId,
      externalStatus: externalStatus,
    };
  } catch (error) {
    console.error("Error checking transaction status:", error);
    throw error;
  }
};
