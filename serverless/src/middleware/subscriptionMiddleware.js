// {PATH_TO_PROJECT}/src/middleware/subscriptionMiddleware.js

import { db } from "../db/d1-client";

/**
 * Hono middleware to check a user's subscription status.
 * It must run AFTER `protect` middleware so that `c.get('user')` is available.
 * If a paid subscription is expired, it reverts the user to the 'free' tier in the database.
 */
export const checkSubscriptionStatus = async (c, next) => {
  try {
    const user = c.get("user"); // Get user from the context set by `protect` middleware
    if (!user) {
      // This case should ideally be caught by `protect` first, but it's good to be safe.
      return c.json({ message: "User not found in context" }, 401);
    }

    // --- START OF FIX ---
    // Access properties from the D1 schema (e.g., subscription_tier, not subscription.tier)
    const hasExpired =
      user.subscription_tier !== "free" &&
      user.subscription_expiresAt &&
      new Date(user.subscription_expiresAt) < new Date();

    if (hasExpired) {
      console.log(`Subscription for user ${user.id} has expired. Reverting to free.`);

      // Call the actual database function to update the user's subscription
      const updatedUser = await db.updateUserSubscription(c.env.DB, user.id, {
        tier: "free",
        expiresAt: null, // Pass null to clear the date
        providerSubscriptionId: null, // Pass null to clear the provider ID
        hasUsedGoldTrial: user.subscription_hasUsedGoldTrial, // Preserve the trial status
      });

      // IMPORTANT: Set the *updated* user object back into the context
      // so that downstream controllers have the latest subscription status.
      c.set("user", updatedUser);
    }
    // --- END OF FIX ---

    // Pass control to the next handler in the chain (e.g., the final controller).
    await next();
  } catch (error) {
    console.error("Error in checkSubscriptionStatus middleware:", error);
    return c.json({ message: "Server error while checking subscription." }, 500);
  }
};
