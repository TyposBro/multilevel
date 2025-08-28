// serverless/src/services/paymentService.js

import { db } from "../db/d1-client";
import PLANS from "../config/plans";
import * as paymeService from "./providers/paymeService";
import * as googlePlayService from "./providers/googlePlayService";
import * as clickService from "./providers/clickService";

/**
 * Initiates a payment flow by calling the appropriate provider service.
 * This is typically used for redirect-based payments like Click or Payme.
 * @param {object} c - The Hono context.
 * @param {string} provider - The payment provider (e.g., 'click', 'payme').
 * @param {string} planId - The ID of the plan being purchased.
 * @param {string} userId - The ID of the user.
 * @param {object} transaction - The database transaction object.
 * @returns {Promise<object>} The result from the provider service (e.g., a payment URL).
 */
export const initiatePayment = async (c, provider, planId, userId, transaction) => {
  const plan = PLANS[planId];
  if (!plan) {
    throw new Error("Plan not found");
  }

  switch (provider.toLowerCase()) {
    case "payme":
      // Payme logic to create a transaction and get a redirect URL.
      return paymeService.createTransaction(c, plan, userId);
    case "click":
      // Click logic to generate a redirect URL using the provided transaction details.
      return clickService.createTransactionUrl(c, plan, planId, userId, transaction);
    default:
      throw new Error(`Unsupported payment provider for creation: ${provider}`);
  }
};

/**
 * Verifies a purchase from any provider and grants entitlements.
 * This is the single source of truth for activating a user's subscription.
 * @param {object} c - The Hono context.
 * @param {string} provider - The provider name ('google', 'payme', etc.).
 * @param {string} verificationToken - The purchase token (from Google) or receipt ID (from Payme/Click).
 * @param {object} user - The user object from the database.
 * @param {string} planId - The plan ID from the client, required for some providers.
 * @returns {Promise<{success: boolean, message: string, subscription?: object}>}
 */
export const verifyPurchase = async (c, provider, verificationToken, user, planId) => {
  let verificationResult;

  // Step 1: Dispatch to the correct provider-specific verification service.
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

    // Note: Click verification happens via webhook, not direct user verification.

    default:
      return { success: false, message: "Invalid payment provider." };
  }

  // Step 2: Handle provider-level verification failures.
  if (!verificationResult || !verificationResult.success) {
    return {
      success: false,
      message: verificationResult?.error || "Purchase verification failed.",
    };
  }

  const verifiedPlanId = verificationResult.planId;
  const verifiedPlan = PLANS[verifiedPlanId];
  if (!verifiedPlan) {
    console.error(`FATAL: No plan found for verified planId: ${verifiedPlanId}`);
    return { success: false, message: "Internal server error: Plan not configured." };
  }

  // Step 3: Grant the subscription entitlement to the user.
  const now = new Date();
  // If the user already has an active subscription, extend it. Otherwise, start from now.
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

  // Step 4: Record the successful transaction for history and RTDN linking.
  if (provider.toLowerCase() === "google") {
    // Check if a transaction for this token already exists to avoid duplicates.
    const existingTransaction = await db.getPaymentTransactionByProviderId(
      c.env.DB,
      "google",
      verificationToken
    );
    if (!existingTransaction) {
      await db.createPaymentTransaction(c.env.DB, {
        userId: user.id,
        planId: verifiedPlanId,
        provider: "google",
        amount: verifiedPlan.prices.usd, // Storing price in cents for consistency
        status: "COMPLETED",
        // THIS IS THE CRITICAL LINK: Store the purchaseToken from Google.
        providerTransactionId: verificationToken,
      });
      console.log(`Recorded new Google Play transaction for user ${user.id}`);
    } else {
      console.log(
        `Transaction for token ...${verificationToken.slice(
          -12
        )} already exists. Skipping record creation.`
      );
    }
  }

  console.log(
    `User ${user.id} successfully upgraded to ${
      verifiedPlan.tier
    } until ${newExpiresAt.toISOString()}`
  );

  // Step 5: Return a success message and the new subscription details to the client.
  return {
    success: true,
    message: `Successfully upgraded to ${verifiedPlan.tier}!`,
    subscription: {
      tier: updatedUser.subscription_tier,
      expiresAt: updatedUser.subscription_expiresAt,
    },
  };
};
