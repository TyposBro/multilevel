// {PATH_TO_PROJECT}/src/controllers/subscriptionController.js

import { db } from "../db/d1-client";
import { verifyPurchase } from "../services/paymentService";

/**
 * @desc    Verify a purchase from any provider and grant entitlements.
 * @route   POST /api/subscriptions/verify-purchase
 * @access  Private
 */
export const verifyAndGrantAccess = async (c) => {
  try {
    const user = c.get("user");
    const { provider, token, planId } = await c.req.json();

    if (!provider || !token || !planId) {
      return c.json({ message: "Provider, token, and planId are required." }, 400);
    }

    // Pass planId as a direct argument to the service function.
    const result = await verifyPurchase(c, provider, token, user, planId);

    if (result.success) {
      return c.json({ message: result.message, subscription: result.subscription });
    } else {
      return c.json({ message: result.message }, 400);
    }
  } catch (error) {
    console.error("Error in verifyAndGrantAccess controller:", error);
    return c.json({ message: "Internal server error." }, 500);
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
      return c.json({ message: "Trials are only for free users." }, 400);
    }

    if (user.subscription_hasUsedGoldTrial === 1) {
      return c.json({ message: "Free trial has already been used." }, 400);
    }

    const oneMonthFromNow = new Date();
    oneMonthFromNow.setMonth(oneMonthFromNow.getMonth() + 1);

    const updatedUser = await db.updateUserSubscription(c.env.DB, user.id, {
      tier: "gold",
      expiresAt: oneMonthFromNow.toISOString(),
      hasUsedGoldTrial: 1,
    });

    return c.json({
      message: "Gold trial started! You have access for 1 month.",
      subscription: {
        tier: updatedUser.subscription_tier,
        expiresAt: updatedUser.subscription_expiresAt,
      },
    });
  } catch (error) {
    console.error("Error starting gold trial:", error);
    return c.json({ message: "Server error during trial activation." }, 500);
  }
};
