// {PATH_TO_PROJECT}/src/controllers/subscriptionController.js

import { db } from "../db/d1-client";
import { verifyPurchase } from "../services/paymentService";
import { 
  verifyGooglePurchase, 
  verifyGoogleProductPurchase, 
  getSubscriptionDetails 
} from "../services/providers/googlePlayService.js";

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

/**
 * @desc    Verify Google Play subscription purchase directly.
 * @route   POST /api/subscriptions/verify-google-play
 * @access  Private
 */
export const verifyGooglePlaySubscription = async (c) => {
  try {
    const user = c.get("user");
    const { purchaseToken, subscriptionId } = await c.req.json();

    if (!purchaseToken || !subscriptionId) {
      return c.json({ 
        message: "Purchase token and subscription ID are required." 
      }, 400);
    }

    console.log(`Verifying Google Play subscription for user ${user.id}: ${subscriptionId}`);

    // Verify the purchase with Google Play
    const verificationResult = await verifyGooglePurchase(c, purchaseToken, subscriptionId);
    
    if (!verificationResult.success) {
      return c.json({ 
        message: "Purchase verification failed", 
        error: verificationResult.error,
        purchaseInfo: verificationResult.purchaseInfo 
      }, 400);
    }

    // Grant access using the payment service
    const grantResult = await verifyPurchase(c, 'google_play', purchaseToken, user, subscriptionId);

    if (grantResult.success) {
      return c.json({ 
        message: "Google Play subscription verified and activated",
        subscription: grantResult.subscription,
        purchaseInfo: verificationResult.purchaseInfo
      });
    } else {
      return c.json({ 
        message: grantResult.message 
      }, 400);
    }

  } catch (error) {
    console.error("Error verifying Google Play subscription:", error);
    return c.json({ message: "Internal server error during verification." }, 500);
  }
};

/**
 * @desc    Verify Google Play product purchase (one-time purchases).
 * @route   POST /api/subscriptions/verify-google-play-product
 * @access  Private
 */
export const verifyGooglePlayProduct = async (c) => {
  try {
    const user = c.get("user");
    const { purchaseToken, productId } = await c.req.json();

    if (!purchaseToken || !productId) {
      return c.json({ 
        message: "Purchase token and product ID are required." 
      }, 400);
    }

    console.log(`Verifying Google Play product for user ${user.id}: ${productId}`);

    // Verify the product purchase with Google Play
    const verificationResult = await verifyGoogleProductPurchase(c, purchaseToken, productId);
    
    if (!verificationResult.success) {
      return c.json({ 
        message: "Product verification failed", 
        error: verificationResult.error 
      }, 400);
    }

    // For products, you might want different logic than subscriptions
    // For example, granting permanent features or credits
    const grantResult = await verifyPurchase(c, 'google_play', purchaseToken, user, productId);

    if (grantResult.success) {
      return c.json({ 
        message: "Google Play product verified and activated",
        purchase: grantResult.subscription // For products, this might contain different data
      });
    } else {
      return c.json({ 
        message: grantResult.message 
      }, 400);
    }

  } catch (error) {
    console.error("Error verifying Google Play product:", error);
    return c.json({ message: "Internal server error during verification." }, 500);
  }
};

/**
 * @desc    Get Google Play subscription status and details.
 * @route   GET /api/subscriptions/google-play-status
 * @access  Private
 */
export const getGooglePlaySubscriptionStatus = async (c) => {
  try {
    const user = c.get("user");
    
    if (!user.payment_provider === 'google_play' || !user.payment_reference_id) {
      return c.json({ 
        message: "No active Google Play subscription found.",
        hasGooglePlaySubscription: false
      });
    }

    // Get the subscription details from our database
    const db = c.env.DB;
    const transaction = await db.prepare(`
      SELECT * FROM payment_transactions 
      WHERE user_id = ? AND payment_provider = 'google_play' 
      AND status = 'completed'
      ORDER BY created_at DESC LIMIT 1
    `).bind(user.id).first();

    if (!transaction) {
      return c.json({ 
        message: "No Google Play transaction found.",
        hasGooglePlaySubscription: false
      });
    }

    // Get live status from Google Play
    const subscriptionDetails = await getSubscriptionDetails(
      c, 
      transaction.reference_id, 
      transaction.plan_id
    );

    if (!subscriptionDetails.success) {
      return c.json({ 
        message: "Failed to get subscription status from Google Play.",
        error: subscriptionDetails.error,
        hasGooglePlaySubscription: false
      }, 500);
    }

    return c.json({
      hasGooglePlaySubscription: true,
      localSubscription: {
        tier: user.subscription_tier,
        expiresAt: user.subscription_expiresAt,
        planId: transaction.plan_id
      },
      googlePlaySubscription: subscriptionDetails.subscription,
      lastTransaction: {
        id: transaction.id,
        createdAt: transaction.created_at,
        amount: transaction.amount,
        currency: transaction.currency
      }
    });

  } catch (error) {
    console.error("Error getting Google Play subscription status:", error);
    return c.json({ message: "Internal server error." }, 500);
  }
};
