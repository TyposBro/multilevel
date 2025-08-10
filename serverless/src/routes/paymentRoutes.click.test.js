// serverless/src/routes/paymentRoutes.click.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { db } from "../db/d1-client";
import * as clickService from "../services/providers/clickService";
import PLANS from "../config/plans";

vi.mock("../db/d1-client.js");
// We only want to mock the signature verification, not the entire service.
vi.mock("../services/providers/clickService.js", async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...original,
    verifyWebhookSignature: vi.fn(),
  };
});

describe("Click Webhook Integration Tests", () => {
  const mockUser = { id: "user-click-123", subscription_expiresAt: null };
  const MOCK_ENV = {
    DB: db,
    ENVIRONMENT: "development",
    CLICK_SECRET_KEY_TEST: "a-very-secret-key",
  };

  // This represents the transaction record created in our DB when the user first clicked "Pay".
  const mockDbTransaction = {
    id: "our-internal-tx-id-123",
    userId: mockUser.id,
    planId: "silver_monthly",
    provider: "click",
    amount: PLANS.silver_monthly.prices.uzs, // 1500000 Tiyin
    status: "PENDING",
  };

  // This is the base payload Click would send to our webhook.
  const clickWebhookBasePayload = {
    click_trans_id: 98765,
    service_id: PLANS.silver_monthly.providerIds.click, // "80012"
    merchant_trans_id: mockDbTransaction.id,
    amount: "15000.00", // Note: Click sends amount in UZS as a string
    sign_time: "2025-08-10 12:00:00",
    sign_string: "mocked-valid-signature",
    error: 0,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    // For happy path tests, we assume the signature is always valid.
    clickService.verifyWebhookSignature.mockReturnValue(true);
    // Mock the DB calls that will happen inside the controller.
    db.getPaymentTransaction.mockResolvedValue(mockDbTransaction);
    db.getUserById.mockResolvedValue(mockUser);
    db.updateUserSubscription.mockResolvedValue({});
    db.updatePaymentTransaction.mockResolvedValue({});
  });

  // Test Case 1: Successful PREPARE call (action: "0")
  it("should handle PREPARE (action 0) successfully for a valid transaction", async () => {
    const preparePayload = { ...clickWebhookBasePayload, action: "0" };

    const res = await app.request(
      "/api/payment/click/webhook",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(preparePayload),
      },
      MOCK_ENV
    );

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.error).toBe(0);
    expect(body.error_note).toBe("Success");
    expect(body.merchant_prepare_id).toBe(mockDbTransaction.id);
  });

  // Test Case 2: Successful COMPLETE call (action: "1")
  it("should handle COMPLETE (action 1) successfully and grant subscription", async () => {
    const completePayload = {
      ...clickWebhookBasePayload,
      action: "1",
      merchant_prepare_id: mockDbTransaction.id, // Click adds this for the complete action
    };

    const res = await app.request(
      "/api/payment/click/webhook",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(completePayload),
      },
      MOCK_ENV
    );

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.error).toBe(0);
    expect(body.error_note).toBe("Success");
    expect(body.merchant_confirm_id).toBe(mockDbTransaction.id);

    // Verify the user's subscription was updated in the database
    expect(db.updateUserSubscription).toHaveBeenCalledOnce();
    expect(db.updateUserSubscription).toHaveBeenCalledWith(
      expect.anything(),
      mockUser.id,
      expect.objectContaining({
        tier: "silver",
        expiresAt: expect.any(String), // We can be more specific if needed
      })
    );

    // Verify our internal transaction was marked as completed
    expect(db.updatePaymentTransaction).toHaveBeenCalledOnce();
    expect(db.updatePaymentTransaction).toHaveBeenCalledWith(
      expect.anything(),
      mockDbTransaction.id,
      { status: "COMPLETED", providerTransactionId: completePayload.click_trans_id }
    );
  });

  // --- Sad Path Tests ---

  it("should return a SIGN CHECK FAILED error if signature is invalid", async () => {
    clickService.verifyWebhookSignature.mockReturnValue(false);
    const payload = { ...clickWebhookBasePayload, action: "0" };

    const res = await app.request(
      "/api/payment/click/webhook",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      },
      MOCK_ENV
    );

    const body = await res.json();
    expect(body.error).toBe(-1);
    expect(body.error_note).toBe("SIGN CHECK FAILED!");
  });

  it("should return a User does not exist error if transaction is not in our DB", async () => {
    db.getPaymentTransaction.mockResolvedValue(null);
    const payload = { ...clickWebhookBasePayload, action: "0" };

    const res = await app.request(
      "/api/payment/click/webhook",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      },
      MOCK_ENV
    );

    const body = await res.json();
    expect(body.error).toBe(-5);
    expect(body.error_note).toBe("User does not exist");
  });

  it("should return an Incorrect parameter amount error if amounts do not match", async () => {
    const payload = { ...clickWebhookBasePayload, action: "0", amount: "100.00" }; // Mismatched amount

    const res = await app.request(
      "/api/payment/click/webhook",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      },
      MOCK_ENV
    );

    const body = await res.json();
    expect(body.error).toBe(-2);
    expect(body.error_note).toBe("Incorrect parameter amount");
  });

  it("should return an Already paid error if the transaction is already completed", async () => {
    db.getPaymentTransaction.mockResolvedValue({ ...mockDbTransaction, status: "COMPLETED" });
    const payload = { ...clickWebhookBasePayload, action: "0" };

    const res = await app.request(
      "/api/payment/click/webhook",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      },
      MOCK_ENV
    );

    const body = await res.json();
    expect(body.error).toBe(-4);
    expect(body.error_note).toBe("Already paid");
  });
});
