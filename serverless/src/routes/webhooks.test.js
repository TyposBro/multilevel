import { describe, it, expect, vi, beforeEach } from 'vitest';
import { testClient } from 'hono/testing';
import webhooks from './webhooks.js';

// Mock the Google Play service
vi.mock('../services/providers/googlePlayService.js', () => ({
  handleGooglePlayWebhook: vi.fn()
}));

import { handleGooglePlayWebhook } from '../services/providers/googlePlayService.js';

describe('Webhooks', () => {
  const client = testClient(webhooks);

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('POST /google-play', () => {
    it('should process valid Google Play webhook', async () => {
      const mockNotificationData = {
        subscriptionNotification: {
          version: '1.0',
          notificationType: 2, // SUBSCRIPTION_RENEWED
          purchaseToken: 'test-purchase-token',
          subscriptionId: 'gold_monthly'
        }
      };

      const base64Data = btoa(JSON.stringify(mockNotificationData));
      const webhookPayload = {
        message: {
          data: base64Data,
          messageId: 'msg-123',
          publishTime: '2024-01-01T00:00:00Z'
        }
      };

      handleGooglePlayWebhook.mockResolvedValue({
        success: true,
        processed: true,
        action: 'subscription_renewed'
      });

      const response = await client['google-play'].$post({
        json: webhookPayload,
        header: {
          'Authorization': 'Bearer mock-token'
        }
      });

      expect(response.status).toBe(200);
      const result = await response.json();
      expect(result.status).toBe('success');
      expect(result.processed).toBe(true);
      expect(result.action).toBe('subscription_renewed');
      expect(handleGooglePlayWebhook).toHaveBeenCalledWith(
        expect.any(Object),
        mockNotificationData
      );
    });

    it('should handle test notification', async () => {
      const testNotificationData = {
        testNotification: {
          version: '1.0'
        }
      };

      const base64Data = btoa(JSON.stringify(testNotificationData));
      const webhookPayload = {
        message: {
          data: base64Data,
          messageId: 'test-msg-123',
          publishTime: '2024-01-01T00:00:00Z'
        }
      };

      handleGooglePlayWebhook.mockResolvedValue({
        success: true,
        processed: true
      });

      const response = await client['google-play'].$post({
        json: webhookPayload
      });

      expect(response.status).toBe(200);
      expect(handleGooglePlayWebhook).toHaveBeenCalledWith(
        expect.any(Object),
        testNotificationData
      );
    });

    it('should reject invalid payload - missing message.data', async () => {
      const invalidPayload = {
        message: {
          messageId: 'msg-123',
          publishTime: '2024-01-01T00:00:00Z'
          // missing data field
        }
      };

      const response = await client['google-play'].$post({
        json: invalidPayload
      });

      expect(response.status).toBe(400);
      const result = await response.text();
      expect(result).toBe('Invalid payload');
    });

    it('should handle malformed base64 data', async () => {
      const webhookPayload = {
        message: {
          data: 'invalid-base64-data!!!',
          messageId: 'msg-123',
          publishTime: '2024-01-01T00:00:00Z'
        }
      };

      const response = await client['google-play'].$post({
        json: webhookPayload
      });

      expect(response.status).toBe(400);
      const result = await response.text();
      expect(result).toBe('Invalid message format');
    });

    it('should handle webhook processing errors', async () => {
      const mockNotificationData = {
        subscriptionNotification: {
          version: '1.0',
          notificationType: 2,
          purchaseToken: 'test-purchase-token',
          subscriptionId: 'gold_monthly'
        }
      };

      const base64Data = btoa(JSON.stringify(mockNotificationData));
      const webhookPayload = {
        message: {
          data: base64Data,
          messageId: 'msg-123',
          publishTime: '2024-01-01T00:00:00Z'
        }
      };

      handleGooglePlayWebhook.mockResolvedValue({
        success: false,
        error: 'Database connection failed'
      });

      const response = await client['google-play'].$post({
        json: webhookPayload
      });

      expect(response.status).toBe(500);
      const result = await response.json();
      expect(result.status).toBe('error');
      expect(result.error).toBe('Database connection failed');
    });

    it('should handle JSON parsing errors in request body', async () => {
      const response = await client['google-play'].$post({
        body: 'invalid-json',
        header: {
          'Content-Type': 'application/json'
        }
      });

      expect(response.status).toBe(500);
      const result = await response.text();
      expect(result).toBe('Internal server error');
    });
  });

  describe('GET /google-play/test', () => {
    it('should return webhook status', async () => {
      const response = await client['google-play']['test'].$get();

      expect(response.status).toBe(200);
      const result = await response.json();
      expect(result.status).toBe('ok');
      expect(result.message).toContain('reachable');
      expect(result.timestamp).toBeDefined();
    });
  });

  describe('GET /status', () => {
    it('should return service status', async () => {
      const response = await client.status.$get();

      expect(response.status).toBe(200);
      const result = await response.json();
      expect(result.service).toBe('webhooks');
      expect(result.status).toBe('healthy');
      expect(result.endpoints).toBeInstanceOf(Array);
      expect(result.endpoints).toContain('/webhooks/google-play');
      expect(result.timestamp).toBeDefined();
    });
  });
});
