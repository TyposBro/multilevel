// {PATH_TO_PROJECT}/api/services/paymentService.js
const User = require("../models/userModel");
const PLANS = require("../config/plans");
const paymeService = require("./providers/paymeService");

/**
 * Initiates a payment process by dispatching to the correct provider service.
 * THIS IS THE FUNCTION YOUR CONTROLLER IS LOOKING FOR.
 * @param {string} provider - The name of the payment provider (e.g., 'payme').
 * @param {string} planId - The ID of the plan to purchase (e.g., 'gold_monthly').
 * @param {string} userId - The ID of the user.
 * @returns {Promise<object>} The result from the provider service (e.g., { paymentUrl, receiptId }).
 */
const initiatePayment = async (provider, planId, userId) => {
  const plan = PLANS[planId];
  if (!plan) {
    throw new Error("Plan not found");
  }

  switch (provider.toLowerCase()) {
    case "payme":
      // Delegate the creation task to the payme-specific service
      return paymeService.createTransaction(plan, userId);

    // case 'click':
    //     return clickService.createTransaction(plan, userId);

    default:
      throw new Error(`Unsupported payment provider for creation: ${provider}`);
  }
};

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
  // Instead of simulating, we call the actual service function.
  const result = await paymeService.checkTransaction(transactionId);

  // The transaction state for a successful payment in Payme is 4.
  if (result && result.state === 4) {
    return { success: true, planId: result.planId };
  } else {
    // Determine a more specific error message
    const errorMessage = result
      ? `Invalid Payme transaction state: ${result.state}`
      : "Payme transaction not found.";
    return { success: false, error: errorMessage };
  }
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

module.exports = { verifyPurchase, initiatePayment };
