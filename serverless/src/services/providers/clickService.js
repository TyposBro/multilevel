// serverless/src/services/providers/clickService.js

import { db } from "../../db/d1-client";
import PLANS from "../../config/plans";
import { createHash } from "node:crypto";

/**
 * Creates a Click payment URL for a web-based checkout flow.
 *
 * IMPORTANT: This function no longer creates a database record. It expects a transaction
 * object to be passed in, which was either newly created or fetched by the controller.
 * Its only job is to generate the correct payment URL.
 *
 * @param {object} c - The Hono context, used to access environment variables.
 * @param {object} plan - The plan object from the configuration file.
 * @param {string} planIdKey - The key for the plan in the PLANS object (e.g., "silver_monthly").
 * @param {string} userId - The ID of the user initiating the payment.
 * @param {object} transaction - The database transaction object (either new or an existing pending one).
 * @returns {Promise<{paymentUrl: string, receiptId: string, clickTransactionId: string}>} An object containing the URL for redirection and the internal transaction ID.
 */
export const createTransactionUrl = async (c, plan, planIdKey, userId, transaction) => {
  const baseUrl = "https://my.click.uz/services/pay";

  const serviceIdForPlan = plan.providerIds.click;
  if (!serviceIdForPlan) {
    throw new Error(`Click service ID is not configured for plan '${planIdKey}' in plans.js`);
  }

  const transactionToUse = transaction;
  console.log(
    `Using transaction: ${transactionToUse.id} with shortId: ${transactionToUse.shortId}`
  );

  const returnUrl = `https://api.milliytechnology.org/payment_success`;
  const paymentUrl = new URL(baseUrl);

  paymentUrl.searchParams.append("service_id", serviceIdForPlan);
  paymentUrl.searchParams.append("merchant_id", "44439");
  paymentUrl.searchParams.append("merchant_user_id", "61733");

  const amountInUzs = (plan.prices.uzs / 100).toFixed(2);
  paymentUrl.searchParams.append("amount", amountInUzs);

  // --- THE CRITICAL CHANGE ---
  // We now use the short, user-friendly ID for the payment page.
  paymentUrl.searchParams.append("transaction_param", transactionToUse.shortId);

  paymentUrl.searchParams.append("return_url", returnUrl);

  console.log("Generated Click payment URL:", paymentUrl.toString());

  return {
    paymentUrl: paymentUrl.toString(),
    receiptId: transactionToUse.id,
    clickTransactionId: transactionToUse.providerTransactionId,
  };
};

/**
 * Verifies the signature from a Click webhook request.
 * This function remains unchanged as its logic is correct.
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

  // ... (the rest of this function is unchanged) ...

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

  const formattedAmount = Number(amount).toFixed(2);
  const prepareIdPart = action == "1" ? merchant_prepare_id : "";
  const signStringSource = `${click_trans_id}${service_id}${secretKey}${merchant_trans_id}${prepareIdPart}${formattedAmount}${action}${sign_time}`;
  const generatedSignature = createHash("md5").update(signStringSource).digest("hex");

  console.log("Generated signature:", generatedSignature);
  console.log("Received signature: ", sign_string);

  return generatedSignature === sign_string;
};
