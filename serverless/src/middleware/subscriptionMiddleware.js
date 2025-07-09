// {PATH_TO_PROJECT}/src/middleware/subscriptionMiddleware.js

import { db } from "../db/d1-client"; // Using the new DB client stub

/**
 * Hono middleware to check a user's subscription status.
 * It must run AFTER `protect` middleware so that `c.get('user')` is available.
 * If a subscription is expired, it reverts the user to the 'free' tier.
 */
export const checkSubscriptionStatus = async (c, next) => {
  try {
    let user = c.get("user"); // Get user from the context set by `protect` middleware
    if (!user) {
      // This case should ideally be caught by `protect` first.
      return c.json({ message: "User not found in context" }, 401);
    }

    const hasExpired =
      user.subscription.tier !== "free" &&
      user.subscription.expiresAt &&
      new Date(user.subscription.expiresAt) < new Date();

    if (hasExpired) {
      console.log(`Subscription for user ${user._id} has expired. Reverting to free.`);

      // --- DB STUB ---
      // In a real implementation, you would call a DB function to update the user.
      // const updatedUser = await db.updateUserSubscription(c.env.DB, user._id, {
      //   tier: 'free',
      //   expiresAt: null,
      //   providerSubscriptionId: null,
      // });

      // Simulating the update for the stub
      user.subscription.tier = "free";
      user.subscription.expiresAt = null;
      user.subscription.providerSubscriptionId = null;
      const updatedUser = user;
      // ---

      // IMPORTANT: Set the *updated* user object back into the context
      // so that downstream controllers have the latest subscription status.
      c.set("user", updatedUser);
    }

    await next();
  } catch (error) {
    console.error("Error in checkSubscriptionStatus middleware:", error);
    return c.json({ message: "Server error while checking subscription." }, 500);
  }
};
