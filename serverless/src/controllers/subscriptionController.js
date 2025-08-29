// in: serverless/src/controllers/subscriptionController.js

import { db } from "../db/d1-client";
import { verifyPurchase } from "../services/paymentService";
import { getSubscriptionDetails } from "../services/providers/googlePlayService.js";

/**
 * @desc    Verify a purchase from any provider, record the transaction, and grant entitlements.
 *          This is the primary endpoint called by the client app after a successful purchase.
 * @route   POST /api/subscriptions/verify-purchase
 * @access  Private
 */
export const verifyAndGrantAccess = async (c) => {
  try {
    const user = c.get("user");
    const { provider, token, planId } = await c.req.json();

    // Logging for traceability
    console.log(
      JSON.stringify({
        scope: "subs.verifyAndGrant",
        event: "request_received",
        provider,
        userId: user.id,
        planId,
        tokenSuffix: token?.slice(-8),
      })
    );

    if (!provider || !token || !planId) {
      return c.json({ message: "Provider, token, and planId are required." }, 400);
    }

    // Delegate the complex logic of verification and entitlement to the paymentService.
    const result = await verifyPurchase(c, provider, token, user, planId);

    if (result.success) {
      console.log(
        JSON.stringify({
          scope: "subs.verifyAndGrant",
          event: "grant_success",
          userId: user.id,
          tier: result.subscription?.tier,
        })
      );
      // Return the new subscription state to the client.
      return c.json({ message: result.message, subscription: result.subscription });
    } else {
      console.warn(
        JSON.stringify({
          scope: "subs.verifyAndGrant",
          event: "grant_failed",
          userId: user.id,
          message: result.message,
        })
      );
      // Pass the failure message from the service to the client.
      return c.json({ message: result.message }, 400);
    }
  } catch (error) {
    console.error("CRITICAL ERROR in verifyAndGrantAccess controller:", error);
    return c.json({ message: "Internal server error during purchase verification." }, 500);
  }
};

/**
 * @desc    Allow a user to start their one-time Gold free trial.
 * @route   POST /api/subscriptions/start-trial
 * @access  Private
 */
export const startGoldTrial = async (c) => {
  try {
    const user = c.get("user");

    if (user.subscription_tier !== "free") {
      return c.json({ message: "Trials are only available for users on the free plan." }, 400);
    }

    if (user.subscription_hasUsedGoldTrial === 1) {
      return c.json({ message: "You have already used your free trial." }, 400);
    }

    const oneMonthFromNow = new Date();
    oneMonthFromNow.setMonth(oneMonthFromNow.getMonth() + 1);

    const updatedUser = await db.updateUserSubscription(c.env.DB, user.id, {
      tier: "gold",
      expiresAt: oneMonthFromNow.toISOString(),
      hasUsedGoldTrial: 1, // Mark the trial as used
    });

    return c.json({
      message: "Gold trial started! You have full access for 1 month.",
      subscription: {
        tier: updatedUser.subscription_tier,
        expiresAt: updatedUser.subscription_expiresAt,
        hasUsedGoldTrial: updatedUser.subscription_hasUsedGoldTrial,
      },
    });
  } catch (error) {
    console.error("Error starting gold trial:", error);
    return c.json({ message: "Server error during trial activation." }, 500);
  }
};

/**
 * @desc    Get the live status of a user's Google Play subscription.
 * @route   GET /api/subscriptions/google-play-status
 * @access  Private
 */
export const getGooglePlaySubscriptionStatus = async (c) => {
  try {
    const user = c.get("user");

    // Find the latest completed Google Play transaction for this user to get the token and planId.
    const transaction = await db.getLatestCompletedGoogleTransaction(c.env.DB, user.id);

    if (!transaction || !transaction.providerTransactionId || !transaction.planId) {
      return c.json({
        message: "No active Google Play subscription transaction found for this user.",
        hasGooglePlaySubscription: false,
      });
    }

    // Get live status from Google Play using the stored token and ID.
    const subscriptionDetails = await getSubscriptionDetails(
      c,
      transaction.providerTransactionId,
      transaction.planId
    );

    if (!subscriptionDetails.success) {
      return c.json(
        {
          message: "Failed to get live subscription status from Google Play.",
          error: subscriptionDetails.error,
          hasGooglePlaySubscription: false,
        },
        500
      );
    }

    return c.json({
      hasGooglePlaySubscription: true,
      // Data from our database
      localSubscription: {
        tier: user.subscription_tier,
        expiresAt: user.subscription_expiresAt,
        planId: transaction.planId,
      },
      // Live data from Google's servers
      googlePlaySubscription: subscriptionDetails.subscription,
      lastTransaction: {
        id: transaction.id,
        createdAt: transaction.createdAt,
      },
    });
  } catch (error) {
    console.error("Error getting Google Play subscription status:", error);
    return c.json({ message: "Internal server error." }, 500);
  }
};
