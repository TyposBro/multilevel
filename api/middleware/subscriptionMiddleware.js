// {PATH_TO_PROJECT}/api/middleware/subscriptionMiddleware.js

const User = require("../models/userModel");

// This middleware checks if a user's paid subscription has expired.
// If it has, it reverts them to the 'free' tier.
// It should be placed AFTER the `protect` middleware.
const checkSubscriptionStatus = async (req, res, next) => {
  try {
    const user = await User.findById(req.user.id);
    if (!user) {
      return res.status(401).json({ message: "User not found" });
    }

    // Check for expiration ONLY if they are on a paid tier and have an expiration date.
    if (
      user.subscription.tier !== "free" &&
      user.subscription.expiresAt &&
      user.subscription.expiresAt < new Date()
    ) {
      console.log(`Subscription for user ${user.email} has expired. Reverting to free.`);
      user.subscription.tier = "free";
      user.subscription.expiresAt = null;
      // Also clear the provider ID if it was a recurring subscription that ended.
      user.subscription.providerSubscriptionId = null;
      await user.save();
    }

    // Attach the potentially updated user object to the request for use in subsequent controllers.
    req.user = user;
    next();
  } catch (error) {
    console.error("Error in checkSubscriptionStatus middleware:", error);
    res.status(500).json({ message: "Server error while checking subscription." });
  }
};

module.exports = { checkSubscriptionStatus };
