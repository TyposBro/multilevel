// in: serverless/src/services/paymentService.js

import { db } from "../db/d1-client";
import PLANS from "../config/plans";
import * as paymeService from "./providers/paymeService";
import * as googlePlayService from "./providers/googlePlayService";
import * as clickService from "./providers/clickService";

/**
 * Initiates a payment flow by calling the appropriate provider service.
 * This is typically used for redirect-based payments like Click or Payme.
 */
export const initiatePayment = async (c, provider, planId, userId, transaction) => {
  const plan = PLANS[planId];
  if (!plan) {
    throw new Error("Plan not found");
  }

  switch (provider.toLowerCase()) {
    case "payme":
      return paymeService.createTransaction(c, plan, userId);
    case "click":
      return clickService.createTransactionUrl(c, plan, planId, userId, transaction);
    default:
      throw new Error(`Unsupported payment provider for creation: ${provider}`);
  }
};

/**
 * Verifies a purchase from any provider, records the transaction, and grants entitlements.
 * This is the single source of truth for activating a user's subscription.
 */
export const verifyPurchase = async (c, provider, verificationToken, user, planId) => {
  let verificationResult;
  const trace = {
    scope: "purchase.verify",
    provider: provider.toLowerCase(),
    tokenSuffix: verificationToken.slice(-8),
    userId: user.id,
    planId,
  };
  console.log(JSON.stringify({ ...trace, event: "start" }));

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
      if (paymeResult.state === 4) {
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

  // Step 2: Handle immediate verification failure.
  if (!verificationResult || !verificationResult.success) {
    console.warn(
      JSON.stringify({ ...trace, event: "provider_failed", error: verificationResult?.error })
    );
    return {
      success: false,
      message: verificationResult?.error || "Purchase verification failed.",
    };
  }

  // At this point, the token IS VALID.
  const verifiedPlanId = planId; // For Google, the planId from the client is the source of truth.
  const verifiedPlan = PLANS[verifiedPlanId];
  if (!verifiedPlan) {
    console.error(`FATAL: No plan found for verified planId: ${verifiedPlanId}`);
    return { success: false, message: "Internal server error: Plan not configured." };
  }

  // Step 3: Record the transaction immediately (Idempotent Check).
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
      amount: verifiedPlan.prices.usd,
      status: "COMPLETED",
      providerTransactionId: verificationToken,
    });
    console.log(JSON.stringify({ ...trace, event: "transaction_recorded" }));
  } else {
    console.log(
      JSON.stringify({ ...trace, event: "transaction_exists", existingId: existingTransaction.id })
    );
  }

  // Step 4: Check the CORRECT state field BEFORE granting the entitlement.
  if (provider.toLowerCase() === "google") {
    const purchaseInfo = verificationResult.purchaseInfo;
    const paymentState = purchaseInfo?.paymentState; // <-- THE FIX: Use paymentState

    console.log(`Verifying subscription payment state. Received paymentState: ${paymentState}`);

    // A paymentState of 1 (Payment received) or 2 (Free Trial) is active.
    // A paymentState of 0 (Payment pending) is also acceptable to proceed.
    if (paymentState !== 0 && paymentState !== 1 && paymentState !== 2) {
      console.warn(
        `Purchase for token ${verificationToken.slice(
          -8
        )} is valid but not in an active/pending state (State: ${paymentState}). Entitlement not granted.`
      );
      return { success: false, message: "This subscription is not in an active state." };
    }
  }

  // Step 5: Grant the subscription entitlement.
  let newExpiresAt;
  if (provider.toLowerCase() === "google" && verificationResult.purchaseInfo.expiryTimeMillis) {
    const expiryMs = parseInt(verificationResult.purchaseInfo.expiryTimeMillis, 10);
    newExpiresAt = new Date(expiryMs);
  } else {
    // Fallback for other providers or if expiry is missing
    const now = new Date();
    const startDate =
      user.subscription_expiresAt && new Date(user.subscription_expiresAt) > now
        ? new Date(user.subscription_expiresAt)
        : now;
    newExpiresAt = new Date(startDate);
    newExpiresAt.setDate(newExpiresAt.getDate() + verifiedPlan.durationDays);
  }

  const updatedUser = await db.updateUserSubscription(c.env.DB, user.id, {
    tier: verifiedPlan.tier,
    expiresAt: newExpiresAt.toISOString(),
  });

  console.log(
    JSON.stringify({
      ...trace,
      event: "grant_success",
      tier: verifiedPlan.tier,
      expiresAt: newExpiresAt.toISOString(),
    })
  );

  // Step 6: Return a success message.
  const response = {
    success: true,
    message: `Successfully upgraded to ${verifiedPlan.tier}!`,
    subscription: {
      tier: updatedUser.subscription_tier,
      expiresAt: updatedUser.subscription_expiresAt,
    },
  };
  console.log(JSON.stringify({ ...trace, event: "end", success: true }));
  return response;
};
