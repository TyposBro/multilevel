// serverless/src/services/providers/clickService.js

import { db } from "../../db/d1-client";
import PLANS from "../../config/plans";
import { createHmac } from "node:crypto";

/**
 * Prepares the initial data needed by the Android SDK to start a Click payment.
 * This does NOT create a transaction with Click directly.
 *
 * @param {object} c - The Hono context.
 * @param {object} plan - The plan object from config.
 * @param {string} userId - The ID of the user.
 * @returns {Promise<object>} The parameters needed for the Click Android SDK.
 */
export const prepareTransactionForMobile = async (c, plan, userId) => {
  const isProduction = c.env.ENVIRONMENT === "production";
  const merchantId = isProduction ? c.env.CLICK_MERCHANT_ID_LIVE : c.env.CLICK_MERCHANT_ID_TEST;
  const serviceId = isProduction ? c.env.CLICK_SERVICE_ID_LIVE : c.env.CLICK_SERVICE_ID_TEST;
  const merchantUserId = isProduction
    ? c.env.CLICK_MERCHANT_USER_ID_LIVE
    : c.env.CLICK_MERCHANT_USER_ID_TEST;

  // Create a record in our database to track this payment attempt.
  // The `transactionParam` is our internal ID for this transaction.
  const transaction = await db.createPaymentTransaction(c.env.DB, {
    userId,
    planId: plan.providerIds.click, // Using the specific planId for Click
    provider: "click",
    amount: plan.prices.uzs, // Click works with Tiyin
  });

  return {
    merchantId: parseInt(merchantId, 10),
    serviceId: parseInt(serviceId, 10),
    merchantUserId: parseInt(merchantUserId, 10),
    amount: plan.prices.uzs / 100, // The SDK expects the amount in UZS, not tiyin
    transactionParam: transaction.id, // This is our internal order ID
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
