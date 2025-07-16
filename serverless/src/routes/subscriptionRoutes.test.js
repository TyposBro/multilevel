// serverless/src/routes/subscriptionRoutes.test.js
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import app from "../index"; // Your Hono app
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";
import { checkSubscriptionStatus } from "../middleware/subscriptionMiddleware";
import * as paymentService from "../services/paymentService"; // Import the payment service

// 1. Mock the database client
vi.mock("../db/d1-client.js");
vi.mock("../services/paymentService.js");

describe("Subscription Routes", () => {
  const MOCK_ENV = {
    JWT_SECRET: "a-secure-test-secret-for-users",
    DB: db,
  };

  // Use fake timers to control `new Date()`
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2024-01-01T00:00:00.000Z"));
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("POST /api/subscriptions/start-trial should successfully start a trial for a free user", async () => {
    // 2. Setup Mocks
    const mockFreeUser = {
      id: "free-user-123",
      subscription_tier: "free",
      subscription_hasUsedGoldTrial: 0, // User has NOT used a trial
    };

    const mockUpdatedUser = {
      ...mockFreeUser,
      subscription_tier: "gold",
      subscription_expiresAt: new Date("2024-02-01T00:00:00.000Z").toISOString(),
    };

    // When the middleware calls getUserById, return our mock free user
    db.getUserById.mockResolvedValue(mockFreeUser);
    // When the controller calls updateUserSubscription, return the updated user
    db.updateUserSubscription.mockResolvedValue(mockUpdatedUser);

    const token = await generateToken({ env: MOCK_ENV }, mockFreeUser.id);

    // 3. Make the Request
    const res = await app.request(
      "/api/subscriptions/start-trial",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      },
      MOCK_ENV
    );

    // 4. Assert the Outcome
    expect(res.status).toBe(200);

    const body = await res.json();
    expect(body.message).toBe("Gold trial started! You have access for 1 month.");
    expect(body.subscription.tier).toBe("gold");
    expect(body.subscription.expiresAt).toBe(mockUpdatedUser.subscription_expiresAt);

    // 5. Assert the mocks were called correctly
    expect(db.updateUserSubscription).toHaveBeenCalledOnce();
    expect(db.updateUserSubscription).toHaveBeenCalledWith(MOCK_ENV.DB, mockFreeUser.id, {
      tier: "gold",
      expiresAt: "2024-02-01T00:00:00.000Z", // One month from our faked time
      hasUsedGoldTrial: 1,
    });
  });

  it("POST /api/subscriptions/start-trial should fail if trial has been used", async () => {
    const mockUserWithTrialUsed = {
      id: "trial-user-456",
      subscription_tier: "free",
      subscription_hasUsedGoldTrial: 1, // User HAS used a trial
    };
    db.getUserById.mockResolvedValue(mockUserWithTrialUsed);
    const token = await generateToken({ env: MOCK_ENV }, mockUserWithTrialUsed.id);

    const res = await app.request(
      "/api/subscriptions/start-trial",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      },
      MOCK_ENV
    );

    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.message).toBe("Free trial has already been used.");
    // Ensure the database was NOT updated
    expect(db.updateUserSubscription).not.toHaveBeenCalled();
  });

  it("should revert an expired GOLD subscription to FREE", async () => {
    // Arrange
    const now = new Date("2024-02-01T00:00:00.000Z");
    const oneDayAgo = new Date("2024-01-31T00:00:00.000Z");
    vi.setSystemTime(now); // Set the current time to AFTER the expiry date

    const mockExpiredUser = {
      id: "expired-user-789",
      subscription_tier: "gold",
      subscription_expiresAt: oneDayAgo.toISOString(), // Expired yesterday!
      subscription_hasUsedGoldTrial: 1,
    };
    db.getUserById.mockResolvedValue(mockExpiredUser);

    // Mock the reversion call
    const revertedUser = {
      ...mockExpiredUser,
      subscription_tier: "free",
      subscription_expiresAt: null,
    };
    db.updateUserSubscription.mockResolvedValue(revertedUser);

    const token = await generateToken({ env: MOCK_ENV }, mockExpiredUser.id);

    // We'll call the /start-trial endpoint. The sub check middleware runs first.
    // The endpoint itself will fail with a 400, which is fine. We are testing the middleware.
    // Act
    const res = await app.request(
      "/api/subscriptions/start-trial",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      },
      MOCK_ENV
    );

    // Assert that the reversion happened
    expect(db.updateUserSubscription).toHaveBeenCalledOnce();
    expect(db.updateUserSubscription).toHaveBeenCalledWith(MOCK_ENV.DB, mockExpiredUser.id, {
      tier: "free",
      expiresAt: null,
      providerSubscriptionId: null,
      hasUsedGoldTrial: 1, // Make sure trial status is preserved
    });

    // The final response from the controller is secondary, but we can check it
    expect(res.status).toBe(400); // Because a user on a 'free' tier can't start a trial if they've used it
  });

  it("should return 500 if DB fails during subscription status check", async () => {
    // Arrange
    const expiredUser = {
      id: "u1",
      subscription_tier: "gold",
      subscription_expiresAt: "2020-01-01T00:00:00.000Z",
    };
    db.getUserById.mockResolvedValue(expiredUser);
    // Simulate the reversion call failing
    db.updateUserSubscription.mockRejectedValue(new Error("DB write failed"));
    const token = await generateToken({ env: MOCK_ENV }, expiredUser.id);

    // Act
    const res = await app.request(
      "/api/subscriptions/start-trial",
      { method: "POST", headers: { Authorization: `Bearer ${token}` } },
      MOCK_ENV
    );

    // Assert
    expect(res.status).toBe(500);
    const body = await res.json();
    expect(body.message).toBe("Server error while checking subscription.");
  });

  it("POST /api/subscriptions/verify-purchase should fail with missing data", async () => {
    // Define a user for this test case so we can generate a token.
    const mockUserForTest = { id: "some-user-id" };
    db.getUserById.mockResolvedValue(mockUserForTest);
    const token = await generateToken({ env: MOCK_ENV }, mockUserForTest.id);

    const res = await app.request(
      "/api/subscriptions/verify-purchase",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ provider: "google" }), // Missing token and planId
      },
      MOCK_ENV
    );
    expect(res.status).toBe(400);
  });

  it("POST /api/subscriptions/start-trial should fail for a non-free user", async () => {
    const mockGoldUser = { id: "gold-user", subscription_tier: "gold" };
    db.getUserById.mockResolvedValue(mockGoldUser);
    const token = await generateToken({ env: MOCK_ENV }, mockGoldUser.id);
    const res = await app.request(
      "/api/subscriptions/start-trial",
      { method: "POST", headers: { Authorization: `Bearer ${token}` } },
      MOCK_ENV
    );
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ message: "Trials are only for free users." });
  });

  it("POST /api/subscriptions/verify-purchase should fail on server error", async () => {
    // Arrange
    const mockUser = { id: "user-error-1" };
    db.getUserById.mockResolvedValue(mockUser);
    const token = await generateToken({ env: MOCK_ENV }, mockUser.id);
    vi.spyOn(paymentService, "verifyPurchase").mockRejectedValue(
      new Error("Internal server error.")
    );

    // Act
    const res = await app.request(
      "/api/subscriptions/verify-purchase",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ provider: "google", token: "a-token", planId: "a-plan" }),
      },
      MOCK_ENV
    );

    // Assert
    expect(res.status).toBe(500);
  });

  it("POST /api/subscriptions/start-trial should fail on server error", async () => {
    // Arrange
    const mockUser = {
      id: "user-error-2",
      subscription_tier: "free",
      subscription_hasUsedGoldTrial: 0,
    };
    db.getUserById.mockResolvedValue(mockUser);
    const token = await generateToken({ env: MOCK_ENV }, mockUser.id);
    // Mock the DB update to fail
    db.updateUserSubscription.mockRejectedValue(new Error("DB connection failed"));

    // Act
    const res = await app.request(
      "/api/subscriptions/start-trial",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      },
      MOCK_ENV
    );

    // Assert
    expect(res.status).toBe(500);
  });
});

describe("Subscription Middleware (checkSubscriptionStatus)", () => {
  it("should return 401 if user is not found in context", async () => {
    // Arrange: Create a mock context where c.get('user') returns null
    const mockContext = {
      get: vi.fn((key) => {
        if (key === "user") return null;
      }),
      json: vi.fn(),
    };
    const next = vi.fn();

    // Act: Call the middleware directly
    await checkSubscriptionStatus(mockContext, next);

    // Assert
    expect(mockContext.json).toHaveBeenCalledWith({ message: "User not found in context" }, 401);
    expect(next).not.toHaveBeenCalled();
  });
});
