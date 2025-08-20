import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// Mock the JWT signing to avoid private key issues in tests
vi.mock('hono/jwt', () => ({
  sign: vi.fn(() => Promise.resolve('mock-jwt-token'))
}));

import { 
  verifyGooglePurchase, 
  verifyGoogleProductPurchase,
  getSubscriptionDetails,
  handleGooglePlayWebhook,
  clearTokenCache
} from './googlePlayService.js';

// Mock the global fetch function
global.fetch = vi.fn();

// Mock Cloudflare environment
const mockEnv = {
  GOOGLE_PLAY_SERVICE_ACCOUNT_JSON: JSON.stringify({
    client_email: 'test@test-project.iam.gserviceaccount.com',
    private_key: '-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC...\n-----END PRIVATE KEY-----\n',
    project_id: 'test-project'
  }),
  GOOGLE_PLAY_PACKAGE_NAME: 'org.milliytechnology.spiko.test',
  DB: {
    prepare: vi.fn(() => ({
      bind: vi.fn(() => ({
        first: vi.fn(),
        run: vi.fn()
      }))
    }))
  }
};

const mockContext = {
  env: mockEnv,
  req: {
    header: vi.fn()
  }
};

describe('Google Play Service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetch.mockClear();
    clearTokenCache();
  });

  describe('verifyGooglePurchase', () => {
    it('should successfully verify a valid subscription', async () => {
      fetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ access_token: 'mock-access-token' })
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({
            purchaseState: 0,
            autoRenewing: true,
            expiryTimeMillis: (Date.now() + 86400000).toString()
          })
        });

      const result = await verifyGooglePurchase(
        mockContext,
        'valid-purchase-token',
        'gold_monthly'
      );

      expect(result.success).toBe(true);
      expect(result.planId).toBe('gold_monthly');
      expect(result.purchaseInfo.autoRenewing).toBe(true);
    });

    it('should handle invalid subscription ID', async () => {
      const result = await verifyGooglePurchase(
        mockContext,
        'some-token',
        ''
      );

      expect(result.success).toBe(false);
      expect(result.error).toBe('Invalid subscription ID provided');
    });

    it('should handle missing service account configuration', async () => {
      const contextWithoutServiceAccount = {
        env: { ...mockEnv, GOOGLE_PLAY_SERVICE_ACCOUNT_JSON: undefined }
      };

      const result = await verifyGooglePurchase(
        contextWithoutServiceAccount,
        'token',
        'gold_monthly'
      );

      expect(result.success).toBe(false);
      expect(result.error).toBe('Google Play verification not configured');
    });

    it('should handle expired subscription', async () => {
      fetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ access_token: 'mock-access-token-2' })
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({
            purchaseState: 0,
            autoRenewing: false,
            expiryTimeMillis: (Date.now() - 86400000).toString()
          })
        });

      const result = await verifyGooglePurchase(
        mockContext,
        'expired-purchase-token',
        'gold_monthly'
      );

      expect(result.success).toBe(false);
      expect(result.error).toContain('expired');
      expect(result.purchaseInfo.expired).toBe(true);
    });

    it('should handle canceled subscription', async () => {
      fetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ access_token: 'mock-access-token-3' })
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({
            purchaseState: 1,
            cancelReason: 0
          })
        });

      const result = await verifyGooglePurchase(
        mockContext,
        'canceled-purchase-token',
        'gold_monthly'
      );

      expect(result.success).toBe(false);
      expect(result.error).toContain('canceled');
      expect(result.purchaseInfo.canceled).toBe(true);
    });

    it('should handle Google Play API errors', async () => {
      fetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ access_token: 'mock-access-token-4' })
        })
        .mockResolvedValueOnce({
          ok: false,
          status: 404,
          text: () => Promise.resolve('Purchase not found')
        });

      const result = await verifyGooglePurchase(
        mockContext,
        'invalid-purchase-token',
        'gold_monthly'
      );

      expect(result.success).toBe(false);
      expect(result.error).toBe('Purchase not found');
    });
  });

  describe('verifyGoogleProductPurchase', () => {
    it('should successfully verify a valid product purchase', async () => {
      fetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ access_token: 'mock-access-token-5' })
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({
            purchaseState: 0,
            consumptionState: 0
          })
        });

      const result = await verifyGoogleProductPurchase(
        mockContext,
        'test-product-token',
        'remove_ads'
      );

      expect(result.success).toBe(true);
      expect(result.planId).toBe('remove_ads');
    });

    it('should handle consumed product', async () => {
      fetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ access_token: 'mock-access-token' })
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({
            purchaseState: 0,
            consumptionState: 1
          })
        });

      const result = await verifyGoogleProductPurchase(
        mockContext,
        'consumed-product-token',
        'remove_ads'
      );

      expect(result.success).toBe(false);
      expect(result.error).toContain('not valid');
    });
  });

  describe('getSubscriptionDetails', () => {
    it('should return detailed subscription information', async () => {
      fetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ access_token: 'mock-access-token-6' })
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({
            purchaseState: 0,
            autoRenewing: true,
            expiryTimeMillis: '1640995200000',
            startTimeMillis: '1609459200000'
          })
        });

      const result = await getSubscriptionDetails(
        mockContext,
        'test-purchase-token',
        'gold_monthly'
      );

      expect(result.success).toBe(true);
      expect(result.subscription.autoRenewing).toBe(true);
      expect(result.subscription.expiryTime).toBe('2022-01-01T00:00:00.000Z');
      expect(result.subscription.startTime).toBe('2021-01-01T00:00:00.000Z');
    });
  });

  describe('handleGooglePlayWebhook', () => {
    it('should handle subscription renewal notification', async () => {
      // Mock database to return a user by purchase token (payment_transactions lookup)
      const mockPrepare = vi.fn();
      const mockBind = vi.fn();
      const mockFirst = vi.fn();
      const mockRun = vi.fn();

      mockFirst.mockResolvedValue({
        user_id: 'test-user-id',
        plan_id: 'gold_monthly'
      });
      mockRun.mockResolvedValue({ success: true });
      mockBind.mockReturnValue({ first: mockFirst, run: mockRun });
      mockPrepare.mockReturnValue({ bind: mockBind });
      mockEnv.DB.prepare = mockPrepare;

      // Mock fetch for Google API calls in webhook
      fetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ access_token: 'mock-access-token-webhook' })
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({
            purchaseState: 0,
            autoRenewing: true,
            expiryTimeMillis: (Date.now() + 86400000).toString(),
            startTimeMillis: Date.now().toString()
          })
        });

      const notification = {
        subscriptionNotification: {
          version: '1.0',
          notificationType: 2, // SUBSCRIPTION_RENEWED
          purchaseToken: 'test-purchase-token',
          subscriptionId: 'gold_monthly'
        }
      };

      const result = await handleGooglePlayWebhook(mockContext, notification);

      expect(result.success).toBe(true);
      expect(result.processed).toBe(true);
      expect(result.action).toBe('subscription_renewed');
    });

    it('should handle subscription cancellation notification', async () => {
      // Mock database to return a user by purchase token (payment_transactions lookup)
      const mockPrepare = vi.fn();
      const mockBind = vi.fn();
      const mockFirst = vi.fn();
      const mockRun = vi.fn();

      mockFirst.mockResolvedValue({
        user_id: 'test-user-id',
        plan_id: 'gold_monthly'
      });
      mockRun.mockResolvedValue({ success: true });
      mockBind.mockReturnValue({ first: mockFirst, run: mockRun });
      mockPrepare.mockReturnValue({ bind: mockBind });
      mockEnv.DB.prepare = mockPrepare;

      // Mock fetch for Google API calls in webhook
      fetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ access_token: 'mock-access-token-webhook' })
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({
            purchaseState: 1,
            cancelReason: 0,
            userCancellationTimeMillis: Date.now().toString()
          })
        });

      const notification = {
        subscriptionNotification: {
          version: '1.0',
          notificationType: 3, // SUBSCRIPTION_CANCELED
          purchaseToken: 'test-purchase-token',
          subscriptionId: 'gold_monthly'
        }
      };

      const result = await handleGooglePlayWebhook(mockContext, notification);

      expect(result.success).toBe(true);
      expect(result.processed).toBe(true);
      expect(result.action).toBe('subscription_canceled');
    });

    it('should handle unknown notification type', async () => {
      // Mock database to return a user by purchase token (payment_transactions lookup)
      const mockPrepare = vi.fn();
      const mockBind = vi.fn();
      const mockFirst = vi.fn();
      const mockRun = vi.fn();

      mockFirst.mockResolvedValue({
        user_id: 'test-user-id',
        plan_id: 'gold_monthly'
      });
      mockRun.mockResolvedValue({ success: true });
      mockBind.mockReturnValue({ first: mockFirst, run: mockRun });
      mockPrepare.mockReturnValue({ bind: mockBind });
      mockEnv.DB.prepare = mockPrepare;

      // Mock fetch for Google API calls in webhook
      fetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ access_token: 'mock-access-token-webhook' })
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({
            purchaseState: 0,
            autoRenewing: true,
            expiryTimeMillis: (Date.now() + 86400000).toString()
          })
        });

      const notification = {
        subscriptionNotification: {
          version: '1.0',
          notificationType: 999, // Unknown type
          purchaseToken: 'test-purchase-token',
          subscriptionId: 'gold_monthly'
        }
      };

      const result = await handleGooglePlayWebhook(mockContext, notification);

      expect(result.success).toBe(true);
      expect(result.processed).toBe(false);
      expect(result.action).toBe('unknown_notification_type');
    });

    it('should handle case when no user is found', async () => {
      // Mock database to return null (no user found by purchase token)
      const mockPrepare = vi.fn();
      const mockBind = vi.fn();
      const mockFirst = vi.fn();

      mockFirst.mockResolvedValue(null); // No user found
      mockBind.mockReturnValue({ first: mockFirst });
      mockPrepare.mockReturnValue({ bind: mockBind });
      mockEnv.DB.prepare = mockPrepare;

      // Mock fetch for Google API calls in webhook (still needed for getSubscriptionDetails)
      fetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ access_token: 'mock-access-token-webhook' })
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({
            purchaseState: 0,
            autoRenewing: true,
            expiryTimeMillis: (Date.now() + 86400000).toString()
          })
        });

      const notification = {
        subscriptionNotification: {
          version: '1.0',
          notificationType: 2,
          purchaseToken: 'unknown-purchase-token',
          subscriptionId: 'gold_monthly'
        }
      };

      const result = await handleGooglePlayWebhook(mockContext, notification);

      expect(result.success).toBe(true);
      expect(result.processed).toBe(false);
      expect(result.action).toBe('no_user_found');
    });
  });
});
