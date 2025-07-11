// serverless/src/services/providers/paymeService.test.js
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import * as paymeService from "./paymeService";
import PLANS from "../../config/plans";

describe("Payme Service", () => {
  // Store the original fetch function
  const originalFetch = global.fetch;

  // Before each test, reset mocks
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // After each test, restore the original fetch
  afterEach(() => {
    global.fetch = originalFetch;
  });

  const mockContext = {
    env: {
      ENVIRONMENT: "development", // Use test environment
      PAYME_CHECKOUT_URL_TEST: "https://checkout.test.paycom.uz",
      PAYME_MERCHANT_ID_TEST: "test-merchant-id",
      PAYME_SECRET_KEY_TEST: "test-secret-key",
    },
  };
  const silverPlan = PLANS["silver_monthly"];
  const userId = "user-123";

  describe("createTransaction", () => {
    it("should successfully create a transaction and return a payment URL", async () => {
      // Arrange: Mock a successful fetch response from Payme
      const mockPaymeResponse = {
        result: {
          receipt: {
            _id: "receipt-id-12345",
          },
        },
      };
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(mockPaymeResponse),
      });

      // Act
      const result = await paymeService.createTransaction(mockContext, silverPlan, userId);

      // Assert
      expect(result.receiptId).toBe("receipt-id-12345");
      expect(result.paymentUrl).toBe("https://test.paycom.uz/receipt-id-12345");
      expect(global.fetch).toHaveBeenCalledOnce();
    });

    it("should throw an error if Payme API returns an error", async () => {
      // Arrange: Mock a fetch response containing a Payme error
      const mockPaymeErrorResponse = {
        error: {
          message: "Invalid amount",
        },
      };
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(mockPaymeErrorResponse),
      });

      // Act & Assert
      await expect(paymeService.createTransaction(mockContext, silverPlan, userId)).rejects.toThrow(
        "Payme API Error: Invalid amount"
      );
    });

    it("should throw an error if the fetch call itself fails", async () => {
      // Arrange: Mock fetch to reject (e.g., network error)
      global.fetch = vi.fn().mockRejectedValue(new Error("Network failure"));

      // Act & Assert
      await expect(paymeService.createTransaction(mockContext, silverPlan, userId)).rejects.toThrow(
        "Failed to create Payme transaction."
      );
    });
  });

  describe("checkTransaction", () => {
    const receiptId = "receipt-id-12345";

    it("should successfully check a transaction and return its state", async () => {
      // Arrange
      const mockPaymeResponse = {
        result: {
          state: 4, // Successful payment state
          receipt: {
            account: [{ name: "plan_id", value: "silver_monthly" }],
          },
        },
      };
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(mockPaymeResponse),
      });

      // Act
      const result = await paymeService.checkTransaction(mockContext, receiptId);

      // Assert
      expect(result.state).toBe(4);
      expect(result.planId).toBe("silver_monthly");
      expect(global.fetch).toHaveBeenCalledOnce();
      const fetchCall = global.fetch.mock.calls[0];
      expect(fetchCall[1].headers["X-Auth"]).toBe("test-merchant-id:test-secret-key");
    });

    it("should throw an error if Payme API returns an error", async () => {
      // Arrange
      const mockPaymeErrorResponse = {
        error: {
          message: "Receipt not found",
        },
      };
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(mockPaymeErrorResponse),
      });

      // Act & Assert
      await expect(paymeService.checkTransaction(mockContext, receiptId)).rejects.toThrow(
        "Payme API Error: Receipt not found"
      );
    });

    it("should throw an error if the fetch call itself fails", async () => {
      // Arrange
      global.fetch = vi.fn().mockRejectedValue(new Error("Network failure"));

      // Act & Assert
      await expect(paymeService.checkTransaction(mockContext, receiptId)).rejects.toThrow(
        "Failed to check Payme transaction status."
      );
    });
  });
});
