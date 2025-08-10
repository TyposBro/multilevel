// serverless/src/services/providers/clickService.js

import { db } from "../../db/d1-client";
import PLANS from "../../config/plans";
import { createHmac } from "node:crypto";

/**
 * Creates a Click payment URL for a web-based checkout flow.
 *
 * @param {object} c - The Hono context.
 * @param {object} plan - The plan object from config.
 * @param {string} planIdKey - The key for the plan in the PLANS object.
 * @param {string} userId - The ID of the user.
 * @returns {Promise<{paymentUrl: string, receiptId: string}>} The URL for redirection and our internal transaction ID.
 */
export const createTransactionUrl = async (c, plan, planIdKey, userId) => {
  const isProduction = c.env.ENVIRONMENT === "production";
  const merchantId = isProduction ? c.env.CLICK_MERCHANT_ID_LIVE : c.env.CLICK_MERCHANT_ID_TEST;
  const merchantUserId = isProduction
    ? c.env.CLICK_MERCHANT_USER_ID_LIVE
    : c.env.CLICK_MERCHANT_USER_ID_TEST;

  // Use the correct base URL for Click's web payments
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

  // 1. Create a transaction record in our database to track this payment attempt.
  const transaction = await db.createPaymentTransaction(c.env.DB, {
    userId,
    planId: planIdKey,
    provider: "click",
    amount: plan.prices.uzs, // Click uses Tiyin on the backend, but amount in URL is in UZS
  });

  // 2. Construct the URL with query parameters.
  const paymentUrl = new URL(baseUrl);
  paymentUrl.searchParams.append("service_id", serviceIdForPlan);
  paymentUrl.searchParams.append("merchant_id", merchantId);
  paymentUrl.searchParams.append("merchant_user_id", merchantUserId);
  paymentUrl.searchParams.append("amount", (plan.prices.uzs / 100).toString()); // Amount in UZS for URL
  paymentUrl.searchParams.append("transaction_param", transaction.id); // Our internal ID

  return {
    paymentUrl: paymentUrl.toString(),
    receiptId: transaction.id,
  };
};

/**
 * Verifies the signature from a Click webhook request.
 * Logic adapted from the click-llc-click-integration-django/click/utils.py example.
 *
 * @param {object} c - The Hono context.
 * @param {object} data - The POST data from the Click webhook.
 * @returns {boolean} - True if the signature is valid.
 */
export const verifyWebhookSignature = (c, data) => {
  const isProduction = c.env.ENVIRONMENT === "production";
  const secretKey = isProduction ? c.env.CLICK_SECRET_KEY_LIVE : c.env.CLICK_SECRET_KEY_TEST;

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

  // Note: merchant_prepare_id is only present for the 'complete' action (action=1)
  const signStringSource = `${click_trans_id}${service_id}${secretKey}${merchant_trans_id}${
    action === "1" ? merchant_prepare_id : ""
  }${amount}${action}${sign_time}`;

  const generatedSignature = createHmac("md5", secretKey).update(signStringSource).digest("hex");

  return generatedSignature === sign_string;
};
