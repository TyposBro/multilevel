// {PATH_TO_PROJECT}/src/services/paymentService.js

import { db } from "../db/d1-client";
import PLANS from "../config/plans";
import * as paymeService from "./providers/paymeService";
import * as googlePlayService from "./providers/googlePlayService";
// import * as clickService from './providers/clickService'; // Example for another provider

/**
 * Initiates a payment process for web-based providers by dispatching to the correct service.
 * @param {object} c - The Hono context.
 * @param {string} provider - The name of the payment provider (e.g., 'payme', 'click').
 * @param {string} planId - The ID of the plan to purchase.
 * @param {string} userId - The ID of the user making the purchase.
 * @returns {Promise<object>} An object containing the paymentUrl and a receiptId.
 */
export const initiatePayment = async (c, provider, planId, userId) => {
  const plan = PLANS[planId];
  if (!plan) {
    throw new Error("Plan not found");
  }

  // This flow is for providers that require redirecting to a web checkout.
  switch (provider.toLowerCase()) {
    case "payme":
      return paymeService.createTransaction(c, plan, userId);
    // case 'click':
    //   return clickService.createTransaction(c, plan, userId);
    default:
      throw new Error(`Unsupported payment provider for creation: ${provider}`);
  }
};

/**
 * Main verification function that dispatches to the correct provider service.
 * After successful verification, it updates the user's subscription in the database.
 * @param {object} c - The Hono context.
 * @param {string} provider - The provider name ('google', 'payme', etc.).
 * @param {string} verificationToken - The purchase token (from Google) or receipt ID (from Payme).
 * @param {object} user - The user object from the database.
 * @returns {Promise<{success: boolean, message: string, subscription?: object}>}
 */
export const verifyPurchase = async (c, provider, verificationToken, user) => {
  let verificationResult;
  // The planId is required for Google Play verification and is sent from the client.
  const { planId } = await c.req.json();

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

    default:
      return { success: false, message: "Invalid payment provider." };
  }

  // Handle provider-level verification failures first
  if (!verificationResult.success) {
    return { success: false, message: verificationResult.error || "Purchase verification failed." };
  }

  // Now handle our internal logic
  const verifiedPlan = PLANS[verificationResult.planId];
  if (!verifiedPlan) {
    console.error(`FATAL: No plan found for verified planId: ${verificationResult.planId}`);
    return { success: false, message: "Internal server error: Plan not configured." };
  }

  // --- Success Path: Grant subscription ---
  const now = new Date();
  // If the user has an active subscription, extend it. Otherwise, start from now.
  const startDate =
    user.subscription_expiresAt && new Date(user.subscription_expiresAt) > now
      ? new Date(user.subscription_expiresAt)
      : now;

  const newExpiresAt = new Date(startDate);
  newExpiresAt.setDate(newExpiresAt.getDate() + verifiedPlan.durationDays);

  const updatedUser = await db.updateUserSubscription(c.env.DB, user.id, {
    tier: verifiedPlan.tier,
    expiresAt: newExpiresAt.toISOString(),
    // providerSubscriptionId can be stored here if needed for provider-specific logic later
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
