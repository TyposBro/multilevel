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
    // --- FISCALIZATION DATA ---
    // You need to get these codes for your specific service.
    // You can find them on my.soliq.uz or ask Payme support.
    const fiscalData = {
      receipt_type: 0, // 0 for Sale (Продажа)
      items: [
        {
          // A user-friendly title for the receipt
          title: `Subscription: ${plan.tier} Tier (30 days)`,
          // The price PER UNIT in Tiyin (must match the total amount if count is 1)
          price: plan.prices.uzs,
          // The quantity of the item being sold
          count: 1,
          // Your Identification Code of Products and Services (ИКПУ)
          // This is VERY IMPORTANT. You get this from the tax authorities.
          code: "07991001001000001", // EXAMPLE CODE - YOU MUST GET YOUR OWN
          // The code for the packaging type (Код упаковки).
          // For digital services, this might be a standard code. Ask Payme support.
          package_code: "123456", // EXAMPLE CODE - YOU MUST GET YOUR OWN
          // VAT percentage for this specific product/service.
          // If you are VAT-exempt, this is 0. Otherwise, it's typically 12 or 15.
          vat_percent: 0,
        },
      ],
    };
    // -------------------------

    const response = await axios.post(
      PAYME_API_URL,
      {
        id: requestId,
        method: "receipts.create",
        params: {
          amount: plan.prices.uzs,
          account: {
            user_id: userId,
            plan_id: plan.providerIds.payme,
          },
          // ADD THE DETAIL OBJECT TO THE PARAMS
          detail: fiscalData,
        },
      },
      {
        headers: {
          /* ... */
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

/**
 * Checks the status of a Payme receipt by calling the Payme API.
 * @param {string} receiptId - The ID of the receipt to check (e.g., '62da73b0803aced907a52b46').
 * @returns {Promise<{state: number, planId?: string}>} The state of the transaction and optionally the planId from the receipt.
 */
const checkTransaction = async (receiptId) => {
  const requestId = Date.now();
  try {
    console.log(`[Payme Service] Checking status for receipt ID: ${receiptId}`);
    console.log(`[Payme Service] Using X-Auth Header: ${MERCHANT_ID}:${SECRET_KEY}`);
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
      console.error("[Payme Service] receipts.check API error:", response.data.error);
      throw new Error(`Payme API Error: ${response.data.error.message}`);
    }

    const result = response.data.result;
    console.log("[Payme Service] receipts.check successful response:", result);

    // The `account` field contains the data you originally sent in `receipts.create`.
    // We can extract the planId from it.
    const accountInfo = result.receipt.account.find((acc) => acc.name === "plan_id");

    return {
      state: result.state, // e.g., 4 for paid, 50 for cancelled
      planId: accountInfo ? accountInfo.value : undefined,
    };
  } catch (error) {
    // Log the detailed error from axios if available
    const errorMsg = error.response ? JSON.stringify(error.response.data) : error.message;
    console.error("[Payme Service] Failed to check Payme transaction status:", errorMsg);
    throw new Error("Failed to check Payme transaction status.");
  }
};

module.exports = {
  createTransaction,
  checkTransaction,
};
