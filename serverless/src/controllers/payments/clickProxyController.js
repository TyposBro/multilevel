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
    const { merchant_prepare_id, click_trans_id, error: clickError } = data;

    // --- Early Exit for Failed Payments from Click ---
    // If Click reports an error, we just mark our transaction as failed and stop.
    if (clickError && parseInt(clickError) < 0) {
      console.log(
        `COMPLETE: Click reported failure (${clickError}). Marking transaction ${merchant_prepare_id} as FAILED.`
      );
      await db.updatePaymentTransaction(c.env.DB, merchant_prepare_id, {
        status: "FAILED",
        providerTransactionId: click_trans_id.toString(),
      });
      // Acknowledge the cancellation to Click
      return c.json({ error: -9, error_note: "Transaction cancelled" });
    }

    // --- Find Transaction ---
    const transaction = await db.getPaymentTransaction(c.env.DB, merchant_prepare_id);
    if (!transaction) {
      return c.json({ error: -6, error_note: "Transaction does not exist" }, 404);
    }

    // --- Idempotency Check (Most Important) ---
    // If the transaction is anything other than PENDING, it has already been processed or is being processed.
    if (transaction.status !== "PENDING") {
      console.log(
        `COMPLETE: Transaction ${transaction.id} already processed. Status: ${transaction.status}. Ignoring duplicate webhook.`
      );
      // Return a success response so Click stops sending retries.
      return c.json({
        merchant_confirm_id: transaction.id,
        error: 0,
        error_note: "Success (Already Confirmed)",
      });
    }

    // --- THE CRITICAL FIX: ATOMIC STATE CHANGE ---
    // Immediately update the transaction status. This prevents the race condition.
    // We now consider this transaction "locked" for processing.
    await db.updatePaymentTransaction(c.env.DB, transaction.id, {
      status: "COMPLETED", // Mark as completed right away
      providerTransactionId: click_trans_id.toString(),
    });
    console.log(`COMPLETE: Locked and updated transaction ${transaction.id} to COMPLETED.`);

    // --- Now, safely perform the business logic ---
    const plan = PLANS[transaction.planId];
    if (!plan) {
      console.error(
        `FATAL: Plan ${transaction.planId} not found for completed transaction ${transaction.id}.`
      );
      // The payment is complete, but we have a server error. Still return success to Click.
      return c.json(
        { error: -7, error_note: "Failed to perform transaction (Plan not found)" },
        500
      );
    }

    // --- Grant Subscription ---
    const user = await db.getUserById(c.env.DB, transaction.userId);
    if (!user) {
      console.error(
        `FATAL: User ${transaction.userId} not found for completed transaction ${transaction.id}.`
      );
      // User doesn't exist, but payment is done. Return success to Click.
      return c.json(
        { error: -7, error_note: "Failed to perform transaction (User not found)" },
        500
      );
    }

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
    console.log(
      `COMPLETE: Subscription for user ${user.id} updated. Expires: ${newExpiresAt.toISOString()}`
    );

    // --- Final Success Response to Click ---
    return c.json({
      merchant_confirm_id: transaction.id,
      error: 0,
      error_note: "Success",
    });
  } catch (error) {
    console.error("CRITICAL ERROR in handleComplete:", error);
    // Even if our server has an error, we might need to tell Click we received it,
    // but a 500 error will cause them to retry, which is often desired.
    return c.json(
      { error: -7, error_note: "Failed to perform transaction (Internal Server Error)" },
      500
    );
  }
};
