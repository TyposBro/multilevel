// {PATH_TO_PROJECT}/api/services/paymentService.js

const PLANS = require("../config/plans");
const User = require("../models/userModel");

// --- Provider-Specific Implementations (Stubs for now) ---

/**
 * Placeholder for Google Play Billing verification.
 * In a real scenario, this would use the `googleapis` library to verify a purchase token.
 * @returns {Promise<{success: boolean, planId: string, error?: string}>}
 */
const verifyGooglePurchase = async (purchaseToken) => {
  console.log(`[Google] Verifying purchase token: ${purchaseToken}`);
  // TODO: Add actual Google Play Developer API verification logic here.
  // You would call the API to confirm the token is valid and get the product/subscription ID.

  // For now, we simulate a successful verification for a one-time gold purchase.
  if (purchaseToken === "fake_google_token_gold_one_time") {
    return { success: true, planId: "gold_one_time_month" };
  }
  return { success: false, error: "Invalid Google purchase token." };
};

/**
 * Placeholder for Payme transaction verification.
 * This would involve calling the Payme Merchant API to check a transaction's status.
 * @returns {Promise<{success: boolean, planId: string, error?: string}>}
 */
const verifyPaymePurchase = async (transactionId) => {
  console.log(`[Payme] Verifying transaction ID: ${transactionId}`);
  // TODO: Add actual Payme API verification logic.

  // Simulate success
  if (transactionId === "fake_payme_trans_id_gold_one_time") {
    return { success: true, planId: "gold_one_time_month" };
  }
  return { success: false, error: "Invalid Payme transaction." };
};

/**
 * Placeholder for Click transaction verification.
 * @returns {Promise<{success: boolean, planId: string, error?: string}>}
 */
const verifyClickPurchase = async (transactionId) => {
  console.log(`[Click] Verifying transaction ID: ${transactionId}`);
  // TODO: Add actual Click API verification logic.

  // Simulate success
  if (transactionId === "fake_click_trans_id_gold_one_time") {
    return { success: true, planId: "gold_one_time_month" };
  }
  return { success: false, error: "Invalid Click transaction." };
};

/**
 * Placeholder for Paynet transaction verification.
 * @returns {Promise<{success: boolean, planId: string, error?: string}>}
 */
const verifyPaynetPurchase = async (transactionId) => {
  console.log(`[Paynet] Verifying transaction ID: ${transactionId}`);
  // TODO: Add actual Paynet API verification logic.

  // Simulate success
  if (transactionId === "fake_paynet_trans_id_gold_one_time") {
    return { success: true, planId: "gold_one_time_month" };
  }
  return { success: false, error: "Invalid Paynet transaction." };
};

// --- The Main Handler ---

/**
 * The main function to process a purchase verification request from any provider.
 * @param {string} provider - 'google', 'payme', 'paynet', or 'click'.
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
      verificationResult = await verifyPaymePurchase(verificationToken);
      break;
    case "click":
      verificationResult = await verifyClickPurchase(verificationToken);

    case "paynet":
      verificationResult = await verifyPaynetPurchase(verificationToken);
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
  const newExpiresAt = new Date(startDate.setDate(startDate.getDate() + plan.durationDays));

  // Update user's subscription
  user.subscription.tier = plan.tier;
  user.subscription.expiresAt = newExpiresAt;

  await user.save();

  console.log(
    `User ${user.email} successfully upgraded to ${plan.tier} until ${newExpiresAt.toISOString()}`
  );

  return {
    success: true,
    message: `Successfully upgraded to ${plan.tier}!`,
    subscription: user.subscription, // Return the updated subscription object to the client
  };
};

module.exports = { verifyPurchase };
