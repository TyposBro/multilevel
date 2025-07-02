const axios = require("axios");

// --- Environment-aware Configuration ---
const isProduction = process.env.NODE_ENV === "production";

const PAYME_API_URL = isProduction
  ? process.env.PAYME_CHECKOUT_URL_LIVE
  : process.env.PAYME_CHECKOUT_URL_TEST;
const MERCHANT_ID = isProduction
  ? process.env.PAYME_MERCHANT_ID_LIVE
  : process.env.PAYME_MERCHANT_ID_TEST;
const SECRET_KEY = isProduction
  ? process.env.PAYME_SECRET_KEY_LIVE
  : process.env.PAYME_SECRET_KEY_TEST;
// -------------------------------------

console.log(`[Payme Service] Running in ${isProduction ? "PRODUCTION" : "TEST"} mode.`);

/**
 * Creates a payment receipt using the Payme Subscribe API.
 * @param {object} plan - The plan object from your config (e.g., PLANS['gold_monthly'])
 * @param {string} userId - The ID of the user making the purchase.
 * @returns {Promise<{paymentUrl: string, receiptId: string}>} An object with the payment URL and the receipt ID.
 */
const createTransaction = async (plan, userId) => {
  const requestId = Date.now();

  // The payment URL is also environment-dependent
  const basePaymentUrl = isProduction ? "https://checkout.paycom.uz" : "https://test.paycom.uz";

  try {
    const response = await axios.post(
      PAYME_API_URL,
      {
        id: requestId,
        method: "receipts.create",
        params: {
          amount: plan.prices.uzs, // Amount in Tiyin
          account: {
            user_id: userId,
            plan_id: plan.providerIds.payme,
          },
          description: `Payment for ${plan.tier} plan`,
        },
      },
      {
        headers: {
          "Content-Type": "application/json",
          "X-Auth": `${MERCHANT_ID}:${SECRET_KEY}`,
        },
      }
    );

    if (response.data.error) {
      throw new Error(`Payme API Error: ${response.data.error.message}`);
    }

    const receipt = response.data.result.receipt;
    const paymentUrl = `${basePaymentUrl}/${receipt._id}`; // Construct the correct URL

    return {
      paymentUrl: paymentUrl,
      receiptId: receipt._id,
    };
  } catch (error) {
    console.error("Payme Service Error:", error.message);
    throw new Error("Failed to create Payme transaction.");
  }
};

// The checkTransaction function uses the same configured variables,
// so it does not need to be changed.
const checkTransaction = async (receiptId) => {
  // ... (rest of the function is the same as before) ...
  const requestId = Date.now();
  try {
    const response = await axios.post(
      PAYME_API_URL,
      {
        id: requestId,
        method: "receipts.check",
        params: {
          id: receiptId,
        },
      },
      {
        headers: {
          "Content-Type": "application/json",
          "X-Auth": `${MERCHANT_ID}:${SECRET_KEY}`,
        },
      }
    );

    if (response.data.error) {
      throw new Error(`Payme API Error: ${response.data.error.message}`);
    }

    return { state: response.data.result.state };
  } catch (error) {
    console.error("Payme Service Check Error:", error.message);
    throw new Error("Failed to check Payme transaction status.");
  }
};

module.exports = {
  createTransaction,
  checkTransaction,
};
