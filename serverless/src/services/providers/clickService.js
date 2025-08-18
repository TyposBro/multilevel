// serverless/src/services/providers/clickService.js

import { db } from "../../db/d1-client";
import PLANS from "../../config/plans";
import { createHash } from "node:crypto";

/**
 * Creates a Click payment URL for a web-based checkout flow.
 * This function is called when a user initiates a payment from the front-end.
 *
 * @param {object} c - The Hono context, used to access environment variables.
 * @param {object} plan - The plan object from the configuration file.
 * @param {string} planIdKey - The key for the plan in the PLANS object (e.g., "premium_1_month").
 * @param {string} userId - The ID of the user initiating the payment.
 * @returns {Promise<{paymentUrl: string, receiptId: string}>} An object containing the URL for redirection and the internal transaction ID.
 */
export const createTransactionUrl = async (c, plan, planIdKey, userId) => {
  const isProduction = c.env.ENVIRONMENT === "production";
  const merchantId = isProduction ? c.env.CLICK_MERCHANT_ID_LIVE : c.env.CLICK_MERCHANT_ID_TEST;
  const merchantUserId = isProduction
    ? c.env.CLICK_MERCHANT_USER_ID_LIVE
    : c.env.CLICK_MERCHANT_USER_ID_TEST;

  const baseUrl = "https://my.click.uz/services/pay";

  if (!merchantId || !merchantUserId) {
    throw new Error(
      "Click Merchant ID or Merchant User ID is not configured for the current environment."
    );
  }

  const serviceIdForPlan = plan.providerIds.click;
  if (!serviceIdForPlan) {
    throw new Error(`Click service ID is not configured for plan '${planIdKey}' in plans.js`);
  }

  // Create a record of this transaction attempt in our database.
  const transaction = await db.createPaymentTransaction(c.env.DB, {
    userId,
    planId: planIdKey,
    provider: "click",
    amount: plan.prices.uzs, // Store amount in tiyin
  });

  console.log(
    `Created transaction: ${transaction.id} with Click ID: ${transaction.providerTransactionId}`
  );

  // Define the deep link to redirect the user back to your mobile app after payment.
  // This helps the app confirm which transaction was completed.
  const returnUrl = `multilevelapp://login?payment_status=success&transaction_id=${transaction.id}`;

  const paymentUrl = new URL(baseUrl);
  paymentUrl.searchParams.append("service_id", serviceIdForPlan);
  paymentUrl.searchParams.append("merchant_id", 44439);
  // paymentUrl.searchParams.append("merchant_id", merchantUserId);
  // Click expects the amount in the URL to be in Sums, not Tiyin.
  paymentUrl.searchParams.append("amount", (plan.prices.uzs / 100).toString());
  paymentUrl.searchParams.append("transaction_param", transaction.providerTransactionId); // Use the external ID
  paymentUrl.searchParams.append("return_url", returnUrl);

  console.log("Generated Click payment URL:", paymentUrl.toString());
  console.log("Payment parameters:");
  console.log("- service_id:", serviceIdForPlan);
  // console.log("- merchant_id:", 49952866);
  console.log("- merchant_id:", 44439);
  console.log("- amount:", (plan.prices.uzs / 100).toString());
  console.log("- transaction_param:", transaction.providerTransactionId);

  return {
    paymentUrl: paymentUrl.toString(),
    receiptId: transaction.id, // Still return the full transaction ID for our internal use
    clickTransactionId: transaction.providerTransactionId, // Also return the Click transaction ID
  };
};

/**
 * Verifies the signature from a Click webhook request.
 * This is the critical function for handling server-to-server communication from Click.
 *
 * @param {object} c - The Hono context, used to access environment variables.
 * @param {object} data - The POST data from the Click webhook.
 * @returns {boolean} - True if the signature is valid, false otherwise.
 */
export const verifyWebhookSignature = (c, data) => {
  console.log("=== SIGNATURE VERIFICATION DEBUG ===");

  const isProduction = c.env.ENVIRONMENT === "production";
  console.log("Environment:", isProduction ? "PRODUCTION" : "TEST");

  const secretKey = isProduction ? c.env.CLICK_SECRET_KEY_LIVE : c.env.CLICK_SECRET_KEY_TEST;
  console.log("Secret key available:", !!secretKey);
  console.log("Secret key length:", secretKey ? secretKey.length : 0);
  console.log(
    "Secret key first 4 chars:",
    secretKey ? secretKey.substring(0, 4) + "..." : "NOT SET"
  );

  const {
    click_trans_id,
    service_id,
    merchant_trans_id,
    merchant_prepare_id,
    amount,
    action,
    sign_time,
    sign_string,
  } = data;

  console.log("Signature verification parameters:");
  console.log("- click_trans_id:", click_trans_id);
  console.log("- service_id:", service_id);
  console.log("- merchant_trans_id:", merchant_trans_id);
  console.log("- merchant_prepare_id:", merchant_prepare_id);
  console.log("- amount (raw):", amount);
  console.log("- action:", action);
  console.log("- sign_time:", sign_time);
  console.log("- sign_string (received):", sign_string);

  // --- BUG FIX ---
  // Click generates its signature using the 'amount' formatted as a string with two decimal places.
  // We must replicate this exactly. e.g., if amount is 10, it must become "10.00".
  const formattedAmount = Number(amount).toFixed(2);

  // The 'merchant_prepare_id' is only included in the signature string for the 'Complete' action (action=1).
  const prepareIdPart = action == "1" ? merchant_prepare_id : "";

  // Construct the source string for the MD5 hash in the exact order specified by Click's documentation.
  const signStringSource = `${click_trans_id}${service_id}${secretKey}${merchant_trans_id}${prepareIdPart}${formattedAmount}${action}${sign_time}`;

  console.log("Sign string source components:");
  console.log("- click_trans_id:", click_trans_id);
  console.log("- service_id:", service_id);
  console.log("- secret_key:", secretKey ? "[HIDDEN]" : "NOT SET");
  console.log("- merchant_trans_id:", merchant_trans_id);
  console.log("- prepare_id_part:", `"${prepareIdPart}"`, "(only for action=1)");
  console.log("- amount (formatted for hash):", `"${formattedAmount}"`);
  console.log("- action:", action);
  console.log("- sign_time:", `"${sign_time}"`);
  console.log("Full sign string source:", signStringSource.replace(secretKey, "[SECRET]"));

  // Generate the MD5 hash from our constructed source string.
  const generatedSignature = createHash("md5").update(signStringSource).digest("hex");
  console.log("Generated signature:", generatedSignature);
  console.log("Received signature: ", sign_string);

  const signaturesMatch = generatedSignature === sign_string;
  console.log("Signatures match:", signaturesMatch);

  return signaturesMatch;
};
