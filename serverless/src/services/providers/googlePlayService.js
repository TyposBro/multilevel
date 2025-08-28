// serverless/src/services/providers/googlePlayService.js

// NOTE: We are NOT using hono/jwt for RS256 because we encountered Invalid JWT Signature from Google.
// Implement a custom RS256 signer using WebCrypto to ensure proper handling of the PEM private key.
import PLANS from "../../config/plans";
import { db } from "../../db/d1-client";

const GOOGLE_AUTH_URL = "https://oauth2.googleapis.com/token";
const GOOGLE_API_SCOPES = ["https://www.googleapis.com/auth/androidpublisher"];
const GOOGLE_PLAY_API_BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3";

const tokenCache = new Map();
const TOKEN_CACHE_DURATION = 3000 * 1000; // 50 minutes

// Helper: base64url encode
const b64url = (input) =>
  btoa(
    String.fromCharCode(
      ...new Uint8Array(input instanceof ArrayBuffer ? input : new TextEncoder().encode(input))
    )
  )
    .replace(/=+/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");

// Helper: import PEM RSA private key
const importRsaPrivateKey = async (pem) => {
  const cleaned = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const binaryDer = Uint8Array.from(atob(cleaned), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    binaryDer.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
};

// Helper to create JWT for Google Auth using RS256
const createGoogleAuthJwt = async (c, serviceAccount) => {
  const t0 = Date.now();
  const iat = Math.floor(Date.now() / 1000);
  const exp = iat + 3600; // 1 hour
  const header = { alg: "RS256", typ: "JWT", kid: serviceAccount.private_key_id };
  const payload = {
    iss: serviceAccount.client_email,
    scope: GOOGLE_API_SCOPES.join(" "),
    aud: GOOGLE_AUTH_URL,
    exp,
    iat,
  };

  // Fix escaped newlines if secret stored with \n and remove CR chars / extra spaces
  const privateKeyPem = serviceAccount.private_key.replace(/\\n/g, "\n").replace(/\r/g, "").trim();

  const encodedHeader = b64url(JSON.stringify(header));
  const encodedPayload = b64url(JSON.stringify(payload));
  const data = `${encodedHeader}.${encodedPayload}`;

  const key = await importRsaPrivateKey(privateKeyPem);
  const sigBuf = await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    key,
    new TextEncoder().encode(data)
  );
  const signature = b64url(sigBuf);
  const jwt = `${data}.${signature}`;
  c?.executionCtx &&
    console.log(
      JSON.stringify({
        scope: "gplay.auth.jwt",
        event: "jwt_created",
        kid: serviceAccount.private_key_id,
        iss: serviceAccount.client_email,
        elapsedMs: Date.now() - t0,
        headerB64Len: encodedHeader.length,
        payloadB64Len: encodedPayload.length,
        sigLen: signature.length,
      })
    );
  return jwt;
};

// Helper to get Google API Access Token with caching
const getGoogleAccessToken = async (c, signedJwt) => {
  const cached = tokenCache.get("google_access_token");
  if (cached && Date.now() < cached.expires) {
    console.log(
      JSON.stringify({
        scope: "gplay.auth",
        event: "cache_hit",
        expiresInMs: cached.expires - Date.now(),
      })
    );
    return cached.token;
  }
  const t0 = Date.now();
  const response = await fetch(GOOGLE_AUTH_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: signedJwt,
    }),
  });
  if (!response.ok) {
    const errorBody = await response.text();
    console.error(
      JSON.stringify({
        scope: "gplay.auth",
        event: "auth_fail",
        status: response.status,
        body: errorBody.slice(0, 300),
      })
    );
    throw new Error(`Google auth failed: ${response.status} - ${errorBody}`);
  }
  const data = await response.json();
  tokenCache.set("google_access_token", {
    token: data.access_token,
    expires: Date.now() + TOKEN_CACHE_DURATION,
  });
  console.log(
    JSON.stringify({ scope: "gplay.auth", event: "auth_success", elapsedMs: Date.now() - t0 })
  );
  return data.access_token;
};

/**
 * Verifies a Google Play subscription purchase token from the client app.
 */
export const verifyGooglePurchase = async (c, purchaseToken, subscriptionId) => {
  try {
    const t0 = Date.now();
    const serviceAccountJson = c.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
    if (!serviceAccountJson)
      return { success: false, error: "Google Play verification is not configured." };
    const serviceAccount = JSON.parse(serviceAccountJson);
    const packageName = "org.milliytechnology.spiko";

    const signedJwt = await createGoogleAuthJwt(c, serviceAccount);
    const accessToken = await getGoogleAccessToken(c, signedJwt);

    const url = `${GOOGLE_PLAY_API_BASE}/applications/${packageName}/purchases/subscriptions/${subscriptionId}/tokens/${purchaseToken}`;
    const response = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });

    if (!response.ok) {
      const errorText = await response.text();
      console.error(
        JSON.stringify({
          scope: "gplay.verify",
          event: "http_error",
          status: response.status,
          body: errorText.slice(0, 300),
          subscriptionId,
          tokenSuffix: purchaseToken.slice(-8),
        })
      );
      return { success: false, error: "Purchase verification failed with Google." };
    }

    const data = await response.json();
    console.log(
      JSON.stringify({
        scope: "gplay.verify",
        event: "response",
        purchaseState: data.purchaseState,
        expiryTimeMillis: data.expiryTimeMillis,
        subscriptionId,
        tokenSuffix: purchaseToken.slice(-8),
        elapsedMs: Date.now() - t0,
      })
    );
    if (data.purchaseState === 0) {
      const expiryMs = data.expiryTimeMillis ? parseInt(data.expiryTimeMillis, 10) : null;
      const isExpired = expiryMs && Date.now() > expiryMs;
      if (!isExpired) {
        return { success: true, planId: subscriptionId, expiryTimeMillis: data.expiryTimeMillis };
      }
      return { success: false, error: "This subscription has already expired." };
    }
    return { success: false, error: "This subscription is not in an active state." };
  } catch (error) {
    console.error(
      JSON.stringify({
        scope: "gplay.verify",
        event: "exception",
        message: error.message,
        stack: (error.stack || "").split("\n").slice(0, 3).join(" | "),
      })
    );
    return { success: false, error: "An internal server error occurred." };
  }
};

/**
 * Verifies a one-time product purchase.
 */
export const verifyGoogleProductPurchase = async (c, purchaseToken, productId) => {
  try {
    const serviceAccountJson = c.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
    if (!serviceAccountJson)
      return { success: false, error: "Google Play verification not configured" };
    const serviceAccount = JSON.parse(serviceAccountJson);
    const packageName = "org.milliytechnology.spiko";

    const signedJwt = await createGoogleAuthJwt(c, serviceAccount);
    const accessToken = await getGoogleAccessToken(c, signedJwt);

    const url = `${GOOGLE_PLAY_API_BASE}/applications/${packageName}/purchases/products/${productId}/tokens/${purchaseToken}`;
    const response = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });

    if (!response.ok) {
      const errorBody = await response.text();
      console.error("Google Play product verification failed:", errorBody);
      return { success: false, error: "Product purchase verification failed" };
    }

    const data = await response.json();
    if (data.purchaseState === 0 && data.consumptionState === 0) {
      return { success: true, planId: productId };
    }
    return {
      success: false,
      error: `Product purchase is not in a valid state. State: ${data.purchaseState}`,
    };
  } catch (error) {
    console.error("Error during Google Play product verification:", error.message);
    return { success: false, error: "Internal error during product verification" };
  }
};

/**
 * Gets live subscription details from Google's servers.
 */
export const getSubscriptionDetails = async (c, purchaseToken, subscriptionId) => {
  try {
    const t0 = Date.now();
    const serviceAccountJson = c.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
    if (!serviceAccountJson) return { success: false, error: "Google Play not configured" };
    const serviceAccount = JSON.parse(serviceAccountJson);
    const packageName = "org.milliytechnology.spiko";

    const signedJwt = await createGoogleAuthJwt(c, serviceAccount);
    const accessToken = await getGoogleAccessToken(c, signedJwt);

    const url = `${GOOGLE_PLAY_API_BASE}/applications/${packageName}/purchases/subscriptions/${subscriptionId}/tokens/${purchaseToken}`;
    const response = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });

    if (!response.ok) {
      console.warn(
        JSON.stringify({
          scope: "gplay.details",
          event: "http_error",
          status: response.status,
          subscriptionId,
          tokenSuffix: purchaseToken.slice(-8),
        })
      );
      return { success: false, error: "Failed to get subscription details from Google." };
    }

    const data = await response.json();
    console.log(
      JSON.stringify({
        scope: "gplay.details",
        event: "response",
        purchaseState: data.purchaseState,
        expiryTimeMillis: data.expiryTimeMillis,
        subscriptionId,
        tokenSuffix: purchaseToken.slice(-8),
        elapsedMs: Date.now() - t0,
      })
    );
    return { success: true, subscription: data };
  } catch (error) {
    console.error(
      JSON.stringify({ scope: "gplay.details", event: "exception", message: error.message })
    );
    return { success: false, error: "Failed to retrieve subscription details" };
  }
};

/**
 * Handles incoming Real-time Developer Notifications from Google Play.
 */
export const handleGooglePlayWebhook = async (c, notification) => {
  if (notification.testNotification) {
    console.log("Received and successfully processed a test notification from Google Play.");
    return;
  }

  const { subscriptionNotification } = notification;
  if (!subscriptionNotification) {
    console.warn("Webhook received but it is not a subscription notification. Ignoring.");
    return;
  }

  const { notificationType, purchaseToken, subscriptionId } = subscriptionNotification;
  console.log(`RTDN Received: Type ${notificationType} for subscription ${subscriptionId}`);
  console.log(
    JSON.stringify({
      scope: "gplay.rtdn",
      event: "received",
      notificationType,
      subscriptionId,
      tokenSuffix: purchaseToken?.slice(-8),
    })
  );

  // Find the user associated with this purchase token. This is the critical link.
  let transaction = await db.getPaymentTransactionByProviderId(c.env.DB, "google", purchaseToken);

  if (!transaction) {
    console.error(
      JSON.stringify({
        scope: "gplay.rtdn",
        event: "unknown_token",
        subscriptionId,
        tokenSuffix: purchaseToken.slice(-8),
      })
    );
    // Try to fetch subscription details directly to recover (user may have purchased on another device before verification endpoint hit).
    const details = await getSubscriptionDetails(c, purchaseToken, subscriptionId);
    if (details.success) {
      const sub = details.subscription;
      const linkedUserId = null; // We don't know user yet.
      // Without mapping purchaseToken -> user, we cannot apply benefits. Log and exit.
      console.error(
        JSON.stringify({
          scope: "gplay.rtdn",
          event: "backfill_failed_no_mapping",
          subscriptionId,
          tokenSuffix: purchaseToken.slice(-8),
        })
      );
    }
    return;
  }

  const userId = transaction.userId;
  const plan = PLANS[subscriptionId];

  if (!plan) {
    console.error(`CRITICAL: RTDN received for an unknown planId: ${subscriptionId}`);
    return;
  }

  const user = await db.getUserById(c.env.DB, userId);
  if (!user) {
    console.error(`CRITICAL: User ${userId} not found for a valid RTDN.`);
    return;
  }

  // Process the notification based on its type
  switch (notificationType) {
    case 4: // SUBSCRIPTION_PURCHASED (initial purchase)
    case 2: // SUBSCRIPTION_RENEWED
    case 1: // SUBSCRIPTION_RECOVERED (e.g., user fixed a declined card)
      console.log(`Renewing/Recovering subscription for user: ${userId}`);

      // Prefer Google's authoritative expiry time when available to prevent drift.
      let newExpiryDate;
      const live = await getSubscriptionDetails(c, purchaseToken, subscriptionId);
      if (live.success && live.subscription.expiryTimeMillis) {
        newExpiryDate = new Date(parseInt(live.subscription.expiryTimeMillis, 10));
      } else {
        // Fallback: extend locally.
        const now = new Date();
        const startDate =
          user.subscription_expiresAt && new Date(user.subscription_expiresAt) > now
            ? new Date(user.subscription_expiresAt)
            : now;
        newExpiryDate = new Date(startDate);
        newExpiryDate.setDate(newExpiryDate.getDate() + plan.durationDays);
      }

      await db.updateUserSubscription(c.env.DB, userId, {
        tier: plan.tier,
        expiresAt: newExpiryDate.toISOString(),
      });
      console.log(`Subscription for ${userId} extended to ${newExpiryDate.toISOString()}`);
      break;

    case 3: // SUBSCRIPTION_CANCELED
      console.log(
        `User ${userId} cancelled their subscription. Access remains valid until expiry.`
      );
      // No change to expiry date is needed. The subscription will expire naturally.
      // You could optionally set a flag in your DB to change the UI for this user (e.g., show "Resubscribe").
      break;

    case 12: // SUBSCRIPTION_REVOKED (e.g., due to a payment chargeback)
    case 13: // SUBSCRIPTION_EXPIRED
      console.log(`Subscription expired/revoked for user: ${userId}. Reverting to 'free' tier.`);
      await db.updateUserSubscription(c.env.DB, userId, {
        tier: "free",
        expiresAt: null, // Clear the expiration date
      });
      break;

    case 5: // SUBSCRIPTION_ON_HOLD (Enters account hold due to payment issue)
    case 6: // SUBSCRIPTION_IN_GRACE_PERIOD
      console.log(`Subscription for user ${userId} is on hold or in grace period.`);
      // The user still has access during a grace period.
      // You could update a status field for the user to show a "Please update your payment method" message in the app.
      break;

    default:
      console.log(`Received unhandled notification type: ${notificationType}. No action taken.`);
  }
};
