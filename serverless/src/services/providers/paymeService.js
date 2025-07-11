// {PATH_TO_PROJECT}/src/services/providers/paymeService.js

/**
 * Creates a payment receipt using the Payme Subscribe API.
 * @param {object} c - The Hono context.
 * @param {object} plan - The plan object from your config.
 * @param {string} userId - The ID of the user making the purchase.
 * @returns {Promise<{paymentUrl: string, receiptId: string}>}
 */
export const createTransaction = async (c, plan, userId) => {
  const isProduction = c.env.ENVIRONMENT === "production";
  const PAYME_API_URL = isProduction
    ? "https://checkout.paycom.uz"
    : "https://checkout.test.paycom.uz";
  const basePaymentUrl = isProduction ? "https://checkout.paycom.uz" : "https://test.paycom.uz";

  const requestId = Date.now();
  const fiscalData = {};

  try {
    const response = await fetch(PAYME_API_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        id: requestId,
        method: "receipts.create",
        params: {
          amount: plan.prices.uzs,
          account: { user_id: userId, plan_id: plan.providerIds.payme },
          detail: fiscalData,
        },
      }),
    });

    const data = await response.json();

    // --- START OF FIX ---
    // If there's a Payme-specific error, throw it now.
    if (data.error) {
      throw new Error(`Payme API Error: ${data.error.message}`);
    }

    // On success, return the data.
    const receipt = data.result.receipt;
    const paymentUrl = `${basePaymentUrl}/${receipt._id}`;
    return { paymentUrl, receiptId: receipt._id };
    // --- END OF FIX ---
  } catch (error) {
    // This catch block now only handles network errors or JSON parsing errors.
    // If the error is our specific Payme API error, re-throw it. Otherwise, wrap it.
    if (error.message.startsWith("Payme API Error:")) {
      throw error;
    }
    console.error("Payme Service Error:", error.message);
    throw new Error("Failed to create Payme transaction.");
  }
};

/**
 * Checks the status of a Payme receipt.
 * @param {object} c - The Hono context.
 * @param {string} receiptId - The ID of the receipt to check.
 * @returns {Promise<{state: number, planId?: string}>}
 */
export const checkTransaction = async (c, receiptId) => {
  const isProduction = c.env.ENVIRONMENT === "production";
  const PAYME_API_URL = isProduction
    ? "https://checkout.paycom.uz"
    : "https://checkout.test.paycom.uz";
  const MERCHANT_ID = isProduction ? c.env.PAYME_MERCHANT_ID_LIVE : c.env.PAYME_MERCHANT_ID_TEST;
  const SECRET_KEY = isProduction ? c.env.PAYME_SECRET_KEY_LIVE : c.env.PAYME_SECRET_KEY_TEST;

  const requestId = Date.now();

  try {
    const response = await fetch(PAYME_API_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Auth": `${MERCHANT_ID}:${SECRET_KEY}`,
      },
      body: JSON.stringify({
        id: requestId,
        method: "receipts.check",
        params: { id: receiptId },
      }),
    });

    const data = await response.json();

    // --- START OF FIX ---
    if (data.error) {
      throw new Error(`Payme API Error: ${data.error.message}`);
    }

    const result = data.result;
    const accountInfo = result.receipt.account.find((acc) => acc.name === "plan_id");

    return {
      state: result.state,
      planId: accountInfo ? accountInfo.value : undefined,
    };
    // --- END OF FIX ---
  } catch (error) {
    if (error.message.startsWith("Payme API Error:")) {
      throw error;
    }
    console.error("[Payme Service] Failed to check Payme transaction status:", error.message);
    throw new Error("Failed to check Payme transaction status.");
  }
};
