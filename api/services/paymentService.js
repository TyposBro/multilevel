// {PATH_TO_PROJECT}/api/services/paymentService.js
const User = require("../models/userModel");
const PLANS = require("../config/plans");
// We will create stubs for provider-specific verification logic
// In a real app, these would contain axios calls to the provider APIs

const verifyGooglePurchase = async (purchaseToken) => {
  console.log(`[Google] Verifying purchase token: ${purchaseToken}`);
  // TODO: Add actual Google Play Developer API verification logic.
  // This involves using the `googleapis` library.

  // For now, simulate a successful verification.
  if (purchaseToken.startsWith("fake_google_token_")) {
    const planKey = purchaseToken.replace("fake_google_token_", "");
    if (PLANS[planKey]) {
      return { success: true, planId: planKey };
    }
  }
  return { success: false, error: "Invalid Google purchase token." };
};

const verifyPaymeTransaction = async (transactionId) => {
  console.log(`[Payme] Verifying transaction: ${transactionId}`);
  // TODO: Implement Payme's `receipts.check` logic here.
  // This is where you would call the Payme API to confirm the transaction status.
  // For now, simulate success.
  if (transactionId.startsWith("fake_payme_trans_")) {
    const planKey = transactionId.replace("fake_payme_trans_", "");
    if (PLANS[planKey]) {
      return { success: true, planId: planKey, state: 4 /* Paid */ };
    }
  }
  return { success: false, error: "Invalid Payme transaction." };
};

const verifyClickTransaction = async (transactionId) => {
  console.log(`[Click] Verifying transaction: ${transactionId}`);
  // TODO: Implement Click's verification API logic here.
  if (transactionId.startsWith("fake_click_trans_")) {
    const planKey = transactionId.replace("fake_click_trans_", "");
    if (PLANS[planKey]) {
      return { success: true, planId: planKey, state: 0 /* Success */ };
    }
  }
  return { success: false, error: "Invalid Click transaction." };
};

/**
 * Main verification function that dispatches to the correct provider.
 * @param {string} provider - 'google', 'payme', or 'click'.
 * @param {string} verificationToken - The token/ID from the client-side purchase.
 * @param {object} user - The Mongoose user object.
 * @returns {Promise<{success: boolean, message: string, subscription?: object}>}
 */
const verifyPurchase = async (provider, verificationToken, user) => {
  let verificationResult;

  switch (provider.toLowerCase()) {
    case "google":
      verificationResult = await verifyGooglePurchase(verificationToken);
      break;
    case "payme":
      verificationResult = await verifyPaymeTransaction(verificationToken);
      break;
    case "click":
      verificationResult = await verifyClickTransaction(verificationToken);
      break;
    default:
      return { success: false, message: "Invalid payment provider." };
  }

  if (!verificationResult.success) {
    return { success: false, message: verificationResult.error || "Purchase verification failed." };
  }

  // --- Grant Entitlements ---
  const plan = PLANS[verificationResult.planId];
  if (!plan) {
    console.error(`FATAL: No plan found for verified planId: ${verificationResult.planId}`);
    return { success: false, message: "Internal server error: Plan not configured." };
  }

  // Calculate new expiration date
  const now = new Date();
  // If the user already has an active subscription, extend it. Otherwise, start from now.
  const startDate =
    user.subscription.expiresAt && user.subscription.expiresAt > now
      ? user.subscription.expiresAt
      : now;

  const newExpiresAt = new Date(
    new Date(startDate).setDate(startDate.getDate() + plan.durationDays)
  );

  // Update user's subscription in the database
  user.subscription.tier = plan.tier;
  user.subscription.expiresAt = newExpiresAt;
  // For recurring subscriptions, you would also save the provider's subscription ID
  // user.subscription.providerSubscriptionId = verificationResult.providerSubId;

  await user.save();

  console.log(
    `User ${user._id} successfully upgraded to ${plan.tier} until ${newExpiresAt.toISOString()}`
  );

  return {
    success: true,
    message: `Successfully upgraded to ${plan.tier}!`,
    subscription: user.subscription, // Return the updated subscription object
  };
};

module.exports = { verifyPurchase };
