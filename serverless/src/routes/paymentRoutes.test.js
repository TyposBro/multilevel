// serverless/src/routes/paymentRoutes.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";
import * as paymentService from "../services/paymentService";

// Mock dependencies
vi.mock("../db/d1-client.js");
// Mock the entire payment service so we can control its functions
vi.mock("../services/paymentService.js");

describe("Payment Routes", () => {
  const mockUser = { id: "user-payment-456", email: "test@payment.com" };
  const MOCK_ENV = {
    JWT_SECRET: "a-secure-test-secret-for-users",
    DB: db,
    // Add any env vars the payment service might need, even if mocked
    ENVIRONMENT: "development",
  };
  let token;

  beforeEach(async () => {
    vi.clearAllMocks();
    db.getUserById.mockResolvedValue(mockUser);
    token = await generateToken({ env: MOCK_ENV }, mockUser.id);
  });

  it("POST /api/payment/create should initiate a payment", async () => {
    // Arrange
    const planId = "silver_monthly";
    const mockPaymentResult = {
      paymentUrl: "https://checkout.test.paycom.uz/some-receipt-id",
      receiptId: "some-receipt-id",
    };
    // Mock the service function that the controller calls
    paymentService.initiatePayment.mockResolvedValue(mockPaymentResult);

    // Act
    const res = await app.request(
      "/api/payment/create",
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ provider: "payme", planId }),
      },
      MOCK_ENV
    );

    // Assert
    expect(res.status).toBe(201);
    const body = await res.json();
    expect(body.paymentUrl).toBe(mockPaymentResult.paymentUrl);
    expect(body.receiptId).toBe(mockPaymentResult.receiptId);

    // Verify the service was called correctly
    expect(paymentService.initiatePayment).toHaveBeenCalledOnce();
    expect(paymentService.initiatePayment).toHaveBeenCalledWith(
      expect.any(Object), // The Hono context 'c'
      "payme",
      planId,
      mockUser.id
    );
  });

  it("POST /api/payment/verify should verify a payment and grant access", async () => {
    // Arrange
    const verificationToken = "some-receipt-id-from-payme";
    const mockVerificationResult = {
      success: true,
      message: "Successfully upgraded to silver!",
      subscription: {
        tier: "silver",
        expiresAt: "2025-01-01T00:00:00.000Z",
      },
    };
    paymentService.verifyPurchase.mockResolvedValue(mockVerificationResult);

    // Act
    const res = await app.request(
      "/api/payment/verify",
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ provider: "payme", token: verificationToken }),
      },
      MOCK_ENV
    );

    // Assert
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.message).toBe(mockVerificationResult.message);
    expect(body.subscription.tier).toBe("silver");

    // Verify the service was called correctly
    expect(paymentService.verifyPurchase).toHaveBeenCalledOnce();
    expect(paymentService.verifyPurchase).toHaveBeenCalledWith(
      expect.any(Object), // The Hono context 'c'
      "payme",
      verificationToken,
      mockUser
    );
  });

  // ... in paymentRoutes.test.js

  it("POST /api/payment/create should fail if provider is missing", async () => {
    const res = await app.request(
      "/api/payment/create",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ planId: "silver_monthly" }),
      },
      MOCK_ENV
    );
    expect(res.status).toBe(400);
  });

  it("POST /api/payment/create should handle server errors", async () => {
    paymentService.initiatePayment.mockRejectedValue(new Error("Service is down"));
    const res = await app.request(
      "/api/payment/create",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ provider: "payme", planId: "silver_monthly" }),
      },
      MOCK_ENV
    );
    expect(res.status).toBe(500);
  });

  it("POST /api/payment/verify should fail if token is missing", async () => {
    const res = await app.request(
      "/api/payment/verify",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ provider: "payme" }),
      },
      MOCK_ENV
    );
    expect(res.status).toBe(400);
  });

  it("POST /api/payment/verify should handle server errors", async () => {
    paymentService.verifyPurchase.mockRejectedValue(new Error("Service is down"));
    const res = await app.request(
      "/api/payment/verify",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ provider: "payme", token: "a-token" }),
      },
      MOCK_ENV
    );
    expect(res.status).toBe(500);
  });
});
