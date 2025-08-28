// serverless/src/routes/webhooks.js

import { Hono } from "hono";
import { handleGooglePlayWebhook } from "../services/providers/googlePlayService.js";

const webhooks = new Hono();

/**
 * Google Play Real-time Developer Notifications (RTDN) webhook endpoint.
 * This receives notifications about subscription events from Google Cloud Pub/Sub.
 */
webhooks.post("/google-play", async (c) => {
  try {
    const body = await c.req.json();

    // Validate the incoming Pub/Sub message structure.
    if (!body.message || !body.message.data) {
      console.warn("Invalid webhook payload from Pub/Sub: missing message.data");
      return c.body(null, 204); // Acknowledge to prevent retries
    }

    // The actual notification is a Base64 encoded string.
    let notification;
    try {
      // atob() is available in the Cloudflare Workers runtime for Base64 decoding.
      const decodedData = atob(body.message.data);
      notification = JSON.parse(decodedData);
    } catch (error) {
      console.error("Failed to decode or parse Google Play notification data:", error);
      // Return 500 to signal Pub/Sub to retry sending this corrupted message.
      return c.json({ message: "Invalid message format" }, 500);
    }

    // Use `waitUntil` to process the notification without blocking the response.
    // This ensures we can acknowledge the message quickly while processing happens reliably.
    c.executionCtx.waitUntil(handleGooglePlayWebhook(c, notification));

    // IMPORTANT: Immediately return a 2xx response to Pub/Sub to acknowledge receipt.
    // If you don't, Pub/Sub will retry, causing duplicate processing. 204 is ideal.
    console.log("Webhook acknowledged. Processing will continue in background.");
    return c.body(null, 204);
  } catch (error) {
    console.error("CRITICAL: Error in Google Play webhook entry point:", error);
    // Return 500 to have Pub/Sub retry the delivery.
    return c.json({ message: "Internal server error" }, 500);
  }
});

export default webhooks;
