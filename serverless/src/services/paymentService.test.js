// serverless/src/services/paymentService.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import * as paymentService from "./paymentService";
import * as paymeService from "./providers/paymeService";
import { db } from "../db/d1-client";

vi.mock("./providers/paymeService");
vi.mock("../db/d1-client");

describe("Payment Service", () => {
  const mockContext = { env: { DB: db } };
  const mockUser = { id: "user-1", subscription_expiresAt: null };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("initiatePayment", () => {
    it("should call paymeService for payme provider", async () => {
      paymeService.createTransaction.mockResolvedValue({ url: "http://payme.url" });
      await paymentService.initiatePayment(mockContext, "payme", "silver_monthly", "user-1");
      expect(paymeService.createTransaction).toHaveBeenCalled();
    });

    it("should throw an error for an unsupported provider", async () => {
      await expect(
        paymentService.initiatePayment(mockContext, "unknown", "silver_monthly", "user-1")
      ).rejects.toThrow("Unsupported payment provider for creation: unknown");
    });

    it("should throw an error for an unknown plan", async () => {
      await expect(
        paymentService.initiatePayment(mockContext, "payme", "unknown_plan", "user-1")
      ).rejects.toThrow("Plan not found");
    });
  });

  describe("verifyPurchase", () => {
    const planId = "silver_monthly"; // Define planId for tests

    it("should fail for an unsupported provider", async () => {
      const result = await paymentService.verifyPurchase(
        mockContext,
        "unknown",
        "token",
        mockUser,
        planId
      );
      expect(result.success).toBe(false);
      expect(result.message).toBe("Invalid payment provider.");
    });

    it("should fail if the provider check returns a falsy value", async () => {
      paymeService.checkTransaction.mockResolvedValue(null);
      const result = await paymentService.verifyPurchase(
        mockContext,
        "payme",
        "token",
        mockUser,
        planId
      );
      expect(result.success).toBe(false);
      expect(result.message).toBe("Purchase verification failed with provider.");
    });

    it("should fail if provider verification fails (e.g., bad state)", async () => {
      paymeService.checkTransaction.mockResolvedValue({ state: 1, planId: "silver_monthly" });
      const result = await paymentService.verifyPurchase(
        mockContext,
        "payme",
        "token",
        mockUser,
        planId
      );
      expect(result.success).toBe(false);
      expect(result.message).toBe("Invalid Payme transaction state: 1");
    });

    it("should fail if plan is not found after a SUCCESSFUL verification", async () => {
      paymeService.checkTransaction.mockResolvedValue({ state: 4, planId: "non_existent_plan" });
      const result = await paymentService.verifyPurchase(
        mockContext,
        "payme",
        "token",
        mockUser,
        planId
      );
      expect(result.success).toBe(false);
      expect(result.message).toContain("Plan not configured");
    });

    it("should succeed for a valid transaction and update user", async () => {
      const goldPlanId = "gold_monthly";
      paymeService.checkTransaction.mockResolvedValue({ state: 4, planId: goldPlanId });
      db.updateUserSubscription.mockResolvedValue({
        subscription_tier: "gold",
        subscription_expiresAt: "2025-01-01T00:00:00.000Z",
      });

      const result = await paymentService.verifyPurchase(
        mockContext,
        "payme",
        "token",
        mockUser,
        goldPlanId
      );

      expect(result.success).toBe(true);
      expect(result.message).toContain("Successfully upgraded to gold");
      expect(result.subscription.tier).toBe("gold");
      expect(db.updateUserSubscription).toHaveBeenCalledOnce();
    });
  });
});
