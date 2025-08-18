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

  // Define the return URL for different environments
  let returnUrl;
  if (c.env.ENVIRONMENT === 'production') {
    // For production - redirect to your website/app
    returnUrl = `https://yourdomain.com/payment-success?transaction_id=${transaction.id}`;
  } else {
    // For development/testing - redirect to app or local URL
    returnUrl = `multilevelapp://login?payment_status=success&transaction_id=${transaction.id}`;
  }

  const paymentUrl = new URL(baseUrl);
  paymentUrl.searchParams.append("service_id", serviceIdForPlan);
  paymentUrl.searchParams.append("merchant_id", merchantId); // Use dynamic merchant ID
  paymentUrl.searchParams.append("merchant_user_id", merchantUserId); // Add merchant user ID
  // Click expects the amount in the URL to be in Sums, not Tiyin.
  paymentUrl.searchParams.append("amount", (plan.prices.uzs / 100).toString());
  paymentUrl.searchParams.append("transaction_param", transaction.providerTransactionId); // Use the external ID
  paymentUrl.searchParams.append("return_url", returnUrl);

  console.log("Generated Click payment URL:", paymentUrl.toString());
  console.log("Payment parameters:");
  console.log("- service_id:", serviceIdForPlan);
  console.log("- merchant_id:", merchantId);
  console.log("- merchant_user_id:", merchantUserId);
  console.log("- amount:", (plan.prices.uzs / 100).toString());
  console.log("- transaction_param:", transaction.providerTransactionId);

  return {
    paymentUrl: paymentUrl.toString(),
    receiptId: transaction.id, // Still return the full transaction ID for our internal use
    clickTransactionId: transaction.providerTransactionId, // Also return the Click transaction ID
  };
};

/**
 * Creates a Click invoice for server-to-server payment initiation
 * This is an alternative to the web redirect flow
 * 
 * @param {object} c - The Hono context
 * @param {object} plan - The plan object
 * @param {string} planIdKey - The plan ID key
 * @param {string} userId - The user ID
 * @param {string} phoneNumber - User's phone number
 * @returns {Promise<{invoiceId: string, receiptId: string}>}
 */
export const createInvoice = async (c, plan, planIdKey, userId, phoneNumber) => {
  const isProduction = c.env.ENVIRONMENT === "production";
  const merchantUserId = isProduction
    ? c.env.CLICK_MERCHANT_USER_ID_LIVE
    : c.env.CLICK_MERCHANT_USER_ID_TEST;
  const secretKey = isProduction ? c.env.CLICK_SECRET_KEY_LIVE : c.env.CLICK_SECRET_KEY_TEST;

  const serviceIdForPlan = plan.providerIds.click;
  if (!serviceIdForPlan) {
    throw new Error(`Click service ID is not configured for plan '${planIdKey}' in plans.js`);
  }

  // Create internal transaction record
  const transaction = await db.createPaymentTransaction(c.env.DB, {
    userId,
    planId: planIdKey,
    provider: "click",
    amount: plan.prices.uzs,
  });

  // Prepare authentication
  const timestamp = Math.floor(Date.now() / 1000);
  const digest = createHash("sha1").update(`${timestamp}${secretKey}`).digest("hex");
  const authHeader = `${merchantUserId}:${digest}:${timestamp}`;

  // Create invoice via Click API
  const invoiceData = {
    service_id: parseInt(serviceIdForPlan),
    amount: plan.prices.uzs / 100, // Convert from tiyin to sums
    phone_number: phoneNumber,
    merchant_trans_id: transaction.providerTransactionId
  };

  try {
    const response = await fetch("https://api.click.uz/v2/merchant/invoice/create", {
      method: "POST",
      headers: {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "Auth": authHeader
      },
      body: JSON.stringify(invoiceData)
    });

    const result = await response.json();

    if (result.error_code !== 0) {
      throw new Error(`Click invoice creation failed: ${result.error_note}`);
    }

    console.log(`Created Click invoice: ${result.invoice_id} for transaction: ${transaction.id}`);

    return {
      invoiceId: result.invoice_id,
      receiptId: transaction.id,
      clickTransactionId: transaction.providerTransactionId
    };

  } catch (error) {
    console.error("Failed to create Click invoice:", error);
    throw new Error(`Invoice creation failed: ${error.message}`);
  }
};

/**
 * Check invoice status via Click API
 * 
 * @param {object} c - The Hono context
 * @param {string} invoiceId - The Click invoice ID
 * @returns {Promise<object>} Invoice status
 */
export const checkInvoiceStatus = async (c, invoiceId) => {
  const isProduction = c.env.ENVIRONMENT === "production";
  const merchantUserId = isProduction
    ? c.env.CLICK_MERCHANT_USER_ID_LIVE
    : c.env.CLICK_MERCHANT_USER_ID_TEST;
  const secretKey = isProduction ? c.env.CLICK_SECRET_KEY_LIVE : c.env.CLICK_SECRET_KEY_TEST;

  const timestamp = Math.floor(Date.now() / 1000);
  const digest = createHash("sha1").update(`${timestamp}${secretKey}`).digest("hex");
  const authHeader = `${merchantUserId}:${digest}:${timestamp}`;

  try {
    const response = await fetch(`https://api.click.uz/v2/merchant/invoice/status/${invoiceId}`, {
      method: "GET",
      headers: {
        "Accept": "application/json",
        "Auth": authHeader
      }
    });

    return await response.json();
  } catch (error) {
    console.error("Failed to check invoice status:", error);
    throw new Error(`Invoice status check failed: ${error.message}`);
  }
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

  if (!secretKey) {
    console.error("CRITICAL: Secret key not configured for environment:", isProduction ? "PRODUCTION" : "TEST");
    return false;
  }

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
  console.log("- secret_key:", "[HIDDEN]");
  console.log("- merchant_trans_id:", merchant_trans_id);
  console.log("- prepare_id_part:", `"${prepareIdPart}"`, "(only for action=1)");
  console.log("- amount (formatted for hash):", `"${formattedAmount}"`);
  console.log("- action:", action);
  console.log("- sign_time:", `"${sign_time}"`);

  // Generate the MD5 hash from our constructed source string.
  const generatedSignature = createHash("md5").update(signStringSource).digest("hex");
  console.log("Generated signature:", generatedSignature);
  console.log("Received signature: ", sign_string);

  const signaturesMatch = generatedSignature === sign_string;
  console.log("Signatures match:", signaturesMatch);

  return signaturesMatch;
};
