// {PATH_TO_PROJECT}/api/controllers/subscriptionController.js
const User = require("../models/userModel");
const { verifyPurchase } = require("../services/paymentService");

/**
 * @desc    Verify a purchase from any provider and grant entitlements.
 * @route   POST /api/subscriptions/verify-purchase
 * @access  Private
 */
const verifyAndGrantAccess = async (req, res) => {
  // Client sends the provider and the token from the SDK
  const { provider, token, planId } = req.body;
  if (!provider || !token || !planId) {
    return res.status(400).json({ message: "Provider, token, and planId are required." });
  }

  try {
    const user = await User.findById(req.user.id);
    if (!user) {
      return res.status(404).json({ message: "User not found." });
    }

    // We pass the planId within the token for simulation purposes
    const verificationToken = `${token}_${planId}`;

    const result = await verifyPurchase(provider, verificationToken, user);

    if (result.success) {
      res.status(200).json({ message: result.message, subscription: result.subscription });
    } else {
      res.status(400).json({ message: result.message });
    }
  } catch (error) {
    console.error("Error in verifyAndGrantAccess controller:", error);
    res.status(500).json({ message: "Internal server error." });
  }
};

/**
 * @desc    Allow a user to start their one-time Gold free trial.
 * @route   POST /api/subscriptions/start-trial
 * @access  Private
 */
const startGoldTrial = async (req, res) => {
  const user = await User.findById(req.user.id);

  if (user.subscription.tier !== "free") {
    return res.status(400).json({ message: "Trials are only for free users." });
  }

  if (user.subscription.hasUsedGoldTrial) {
    return res.status(400).json({ message: "Free trial has already been used." });
  }

  // In a real app, you might require them to link a card first, even for a trial.
  // For now, we just grant it.

  const oneMonthFromNow = new Date();
  oneMonthFromNow.setMonth(oneMonthFromNow.getMonth() + 1);

  user.subscription.tier = "gold";
  user.subscription.expiresAt = oneMonthFromNow;
  user.subscription.hasUsedGoldTrial = true;

  await user.save();

  res.status(200).json({
    message: "Gold trial started! You have access for 1 month.",
    subscription: user.subscription,
  });
};

module.exports = {
  verifyAndGrantAccess,
  startGoldTrial,
};
