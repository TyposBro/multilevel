import { Hono } from 'hono';
import { handleGooglePlayWebhook } from '../services/providers/googlePlayService.js';

const webhooks = new Hono();

/**
 * Google Play Real-time Developer Notifications webhook endpoint.
 * This endpoint receives notifications about subscription events like renewals, cancellations, etc.
 * 
 * Google sends notifications as base64-encoded Pub/Sub messages.
 * To set this up in Google Play Console:
 * 1. Go to Monetization > Monetization setup
 * 2. Set up Real-time developer notifications
 * 3. Point to: https://your-domain.com/webhooks/google-play
 */
webhooks.post('/google-play', async (c) => {
  try {
    const body = await c.req.json();
    
    // Validate that this is a Pub/Sub message
    if (!body.message || !body.message.data) {
      console.warn("Invalid Google Play webhook payload - missing message.data");
      return c.text('Invalid payload', 400);
    }

    // Decode the base64 message data
    let notificationData;
    try {
      const decodedData = atob(body.message.data);
      notificationData = JSON.parse(decodedData);
    } catch (error) {
      console.error("Failed to decode Google Play notification data:", error.message);
      return c.text('Invalid message format', 400);
    }

    console.log("Received Google Play notification:", {
      messageId: body.message.messageId,
      publishTime: body.message.publishTime,
      notificationType: notificationData.subscriptionNotification?.notificationType,
      testNotification: !!notificationData.testNotification
    });

    // Verify the notification comes from Google (optional but recommended)
    const isValid = await verifyGooglePlayWebhookSignature(c, body);
    if (!isValid) {
      console.warn("Google Play webhook signature verification failed");
      // In production, you might want to reject unsigned requests
      // return c.text('Unauthorized', 401);
    }

    // Process the notification
    const result = await handleGooglePlayWebhook(c, notificationData);
    
    if (result.success) {
      console.log(`Google Play webhook processed successfully: ${result.action || 'unknown'}`);
      return c.json({ 
        status: 'success', 
        processed: result.processed,
        action: result.action 
      });
    } else {
      console.error("Failed to process Google Play webhook:", result.error);
      return c.json({ 
        status: 'error', 
        error: result.error 
      }, 500);
    }

  } catch (error) {
    console.error("Error handling Google Play webhook:", error.message);
    return c.text('Internal server error', 500);
  }
});

/**
 * Test endpoint for Google Play webhook setup.
 * Use this to verify your webhook is reachable during Google Play Console setup.
 */
webhooks.get('/google-play/test', async (c) => {
  return c.json({
    status: 'ok',
    message: 'Google Play webhook endpoint is reachable',
    timestamp: new Date().toISOString()
  });
});

/**
 * Verifies the authenticity of a Google Play webhook request.
 * This is optional but recommended for production security.
 * 
 * @param {object} c - The Hono context.
 * @param {object} body - The webhook request body.
 * @returns {Promise<boolean>} Whether the signature is valid.
 */
const verifyGooglePlayWebhookSignature = async (c, body) => {
  try {
    // For Google Cloud Pub/Sub, you would typically verify:
    // 1. The JWT token in the Authorization header
    // 2. The message signature
    
    // For now, we'll do basic validation
    const authHeader = c.req.header('Authorization');
    
    // Check if the request has proper authorization
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return false;
    }

    // In a full implementation, you would:
    // 1. Verify the JWT token using Google's public keys
    // 2. Check that the audience matches your project
    // 3. Verify the message signature
    
    // For development, we'll accept any bearer token
    return true;
    
  } catch (error) {
    console.error("Error verifying Google Play webhook signature:", error.message);
    return false;
  }
};

/**
 * Generic webhook status endpoint for monitoring.
 */
webhooks.get('/status', async (c) => {
  return c.json({
    service: 'webhooks',
    status: 'healthy',
    endpoints: [
      '/webhooks/google-play',
      '/webhooks/google-play/test',
      '/webhooks/status'
    ],
    timestamp: new Date().toISOString()
  });
});

export default webhooks;
