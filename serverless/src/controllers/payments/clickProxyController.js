// serverless/src/controllers/payments/clickProxyController.js

import { db } from "../../db/d1-client";
import PLANS from "../../config/plans";

/**
 * Handles the 'Prepare' step of a Click.uz transaction, forwarded from the PHP proxy.
 *
 * This function validates that a transaction is legitimate and ready for payment.
 * 1. Finds the transaction in the DB using the user-facing `shortId`.
 * 2. Checks that the transaction status is 'PENDING'.
 * 3. Verifies that the payment amount from Click matches the amount in the DB.
 * 4. Checks for other duplicate pending transactions for the same user/plan.
 * 5. Returns the internal transaction UUID (`id`) to Click as the `merchant_prepare_id`.
 *
 * @param {object} c - The Hono context.
 * @returns {Response} A JSON response formatted for the Click.uz API.
 */
export const handlePrepare = async (c) => {
  try {
    const data = await c.req.json();
    // 'merchant_trans_id' from Click now contains the short, user-friendly ID.
    const { merchant_trans_id: shortId, amount } = data;

    console.log(`PREPARE: Received request for shortId: ${shortId}`);

    // --- Validation Step 1: Find the transaction by its shortId ---
    const transaction = await db.getTransactionByShortId(c.env.DB, shortId);

    if (!transaction) {
      console.log(`PREPARE FAIL: Transaction with shortId ${shortId} not found.`);
      // Error -5: User/Transaction does not exist
      return c.json({ error: -5, error_note: "Transaction does not exist" }, 404);
    }

    console.log(`PREPARE: Found transaction with internal ID: ${transaction.id}`);

    // --- Validation Step 2: Check transaction status ---
    // The transaction must be pending to be prepared for payment.
    if (transaction.status !== "PENDING") {
      console.log(
        `PREPARE FAIL: Transaction ${transaction.id} is not PENDING. Status: ${transaction.status}.`
      );
      // Error -4: Already paid or processed
      return c.json({ error: -4, error_note: "Already paid or processed" }, 409);
    }

    // --- Validation Step 3: Check for other pending transactions (User Rule) ---
    // Ensure the user doesn't have multiple pending payments for the same item.
    const pendingStmt = c.env.DB.prepare(
      `SELECT id FROM payment_transactions WHERE userId = ? AND planId = ? AND status = 'PENDING' AND id != ?`
    ).bind(transaction.userId, transaction.planId, transaction.id);
    const hasOtherPending = await pendingStmt.first();

    if (hasOtherPending) {
      console.log(
        `PREPARE FAIL: User ${transaction.userId} has another pending transaction (${hasOtherPending.id}) for plan ${transaction.planId}.`
      );
      // Error -9: Transaction cancelled (a good generic code for business rule failure)
      return c.json(
        { error: -9, error_note: "Request cancelled. Another pending transaction exists." },
        409
      );
    }

    // --- Validation Step 4: Verify the amount ---
    // Click sends the amount in UZS (e.g., "1000.00"). The DB stores it in Tiyin (e.g., 100000).
    const amountInTiyin = Math.round(parseFloat(amount) * 100);
    if (transaction.amount !== amountInTiyin) {
      console.log(
        `PREPARE FAIL: Amount mismatch for transaction ${transaction.id}. DB amount (Tiyin): ${transaction.amount}, Received amount (Tiyin): ${amountInTiyin}.`
      );
      // Error -2: Incorrect parameter amount
      return c.json({ error: -2, error_note: "Incorrect parameter amount" }, 400);
    }

    // --- Success ---
    // If all checks pass, respond with success.
    // The `merchant_prepare_id` MUST be the internal, unique UUID (`id`) of the transaction.
    // This ID is crucial for the 'complete' step.
    console.log(`PREPARE SUCCESS: Transaction ${transaction.id} is ready for payment.`);
    return c.json({
      merchant_prepare_id: transaction.id,
      error: 0,
      error_note: "Success",
    });
  } catch (error) {
    console.error("CRITICAL ERROR in handlePrepare:", error);
    // Error -8: Generic system error on our side
    return c.json({ error: -8, error_note: "Internal server error" }, 500);
  }
};

export const handleComplete = async (c) => {
  try {
    const data = await c.req.json();
    const { merchant_prepare_id, click_trans_id } = data;

    const transaction = await db.getPaymentTransaction(c.env.DB, merchant_prepare_id);
    if (!transaction) {
      return c.json({ error: -6, error_note: "Transaction does not exist" }, 404);
    }

    if (transaction.status === "COMPLETED") {
      return c.json({ error: -4, error_note: "Already paid" }, 409);
    }

    const plan = PLANS[transaction.planId];
    if (!plan) {
      console.error(`FATAL: Plan ${transaction.planId} not found for completed transaction.`);
      return c.json({ error: -7, error_note: "Failed to perform transaction" }, 500);
    }

    // --- Grant Subscription ---
    const user = await db.getUserById(c.env.DB, transaction.userId);
    const now = new Date();
    const startDate =
      user.subscription_expiresAt && new Date(user.subscription_expiresAt) > now
        ? new Date(user.subscription_expiresAt)
        : now;

    const newExpiresAt = new Date(startDate);
    newExpiresAt.setDate(newExpiresAt.getDate() + plan.durationDays);

    await db.updateUserSubscription(c.env.DB, user.id, {
      tier: plan.tier,
      expiresAt: newExpiresAt.toISOString(),
    });

    // --- Update Transaction Record ---
    await db.updatePaymentTransaction(c.env.DB, transaction.id, {
      status: "COMPLETED",
      providerTransactionId: click_trans_id.toString(),
    });

    return c.json({
      merchant_confirm_id: transaction.id,
      error: 0,
      error_note: "Success",
    });
  } catch (error) {
    console.error("Error in handleComplete:", error);
    return c.json({ error: -7, error_note: "Failed to perform transaction" }, 500);
  }
};
