// serverless/src/routes/subscriptionRoutes.test.js
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import app from "../index"; // Your Hono app
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";

// 1. Mock the database client
vi.mock("../db/d1-client.js");

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
});
