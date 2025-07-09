// {PATH_TO_PROJECT}/src/services/paymentService.js

import { db } from "../db/d1-client";
import PLANS from "../config/plans";
import * as paymeService from "./providers/paymeService";
// import * as clickService from './providers/clickService'; // When you implement it

/**
 * Initiates a payment process by dispatching to the correct provider service.
 * @param {object} c - The Hono context.
 * @param {string} provider - The name of the payment provider.
 * @param {string} planId - The ID of the plan to purchase.
 * @param {string} userId - The ID of the user.
 * @returns {Promise<object>}
 */
export const initiatePayment = async (c, provider, planId, userId) => {
  const plan = PLANS[planId];
  if (!plan) {
    throw new Error("Plan not found");
  }

  switch (provider.toLowerCase()) {
    case "payme":
      // Pass the context 'c' to the provider service
      return paymeService.createTransaction(c, plan, userId);
    default:
      throw new Error(`Unsupported payment provider for creation: ${provider}`);
  }
};

/**
 * Main verification function that dispatches to the correct provider.
 * @param {object} c - The Hono context.
 * @param {string} provider - 'google', 'payme', or 'click'.
 * @param {string} verificationToken - The token/ID from the client-side purchase.
 * @param {object} user - The user object from the database.
 * @returns {Promise<{success: boolean, message: string, subscription?: object}>}
 */
export const verifyPurchase = async (c, provider, verificationToken, user) => {
  let verificationResult;

  switch (provider.toLowerCase()) {
    // case 'google':
    //   verificationResult = await verifyGooglePurchase(c, verificationToken);
    //   break;
    case "payme":
      verificationResult = await paymeService.checkTransaction(c, verificationToken);
      // Adapt Payme's state to the generic verificationResult format
      if (verificationResult && verificationResult.state === 4) {
        verificationResult.success = true;
      } else {
        verificationResult.success = false;
        verificationResult.error = `Invalid Payme transaction state: ${verificationResult.state}`;
      }
      break;
    // case 'click':
    //   verificationResult = await verifyClickTransaction(c, verificationToken);
    //   break;
    default:
      return { success: false, message: "Invalid payment provider." };
  }

  if (!verificationResult.success) {
    return { success: false, message: verificationResult.error || "Purchase verification failed." };
  }

  const plan = PLANS[verificationResult.planId];
  if (!plan) {
    console.error(`FATAL: No plan found for verified planId: ${verificationResult.planId}`);
    return { success: false, message: "Internal server error: Plan not configured." };
  }

  const now = new Date();
  const startDate =
    user.subscription_expiresAt && new Date(user.subscription_expiresAt) > now
      ? new Date(user.subscription_expiresAt)
      : now;

  const newExpiresAt = new Date(startDate);
  newExpiresAt.setDate(newExpiresAt.getDate() + plan.durationDays);

  // Update user's subscription using the D1 client
  const updatedUser = await db.updateUserSubscription(c.env.DB, user.id, {
    tier: plan.tier,
    expiresAt: newExpiresAt.toISOString(),
    // providerSubscriptionId: verificationResult.providerSubId, // For recurring subs
  });

  console.log(
    `User ${user.id} successfully upgraded to ${plan.tier} until ${newExpiresAt.toISOString()}`
  );

  return {
    success: true,
    message: `Successfully upgraded to ${plan.tier}!`,
    subscription: {
      // Construct the response object
      tier: updatedUser.subscription_tier,
      expiresAt: updatedUser.subscription_expiresAt,
    },
  };
};
