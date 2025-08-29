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
const b64url = (input) => {
  const buffer = input instanceof ArrayBuffer ? input : new TextEncoder().encode(input);
  const bytes = new Uint8Array(buffer);
  let binary = "";
  // This is a reliable way to convert a Uint8Array to a binary string for btoa.
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/=+/g, "").replace(/\+/g, "-").replace(/\//g, "_");
};

let loggedKeyFingerprint = false;

// Helper: import PEM RSA private key
const importRsaPrivateKey = async (pem) => {
  const cleaned = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const binaryDer = Uint8Array.from(atob(cleaned), (c) => c.charCodeAt(0));
  // Compute a stable fingerprint (sha256 of DER) for diagnostics (not secret)
  if (!loggedKeyFingerprint) {
    try {
      const digest = await crypto.subtle.digest("SHA-256", binaryDer.buffer);
      const hex = Array.from(new Uint8Array(digest))
        .map((b) => b.toString(16).padStart(2, "0"))
        .join("");
      console.log(
        JSON.stringify({
          scope: "gplay.auth.key",
          event: "fingerprint",
          sha256: hex.slice(0, 16), // shortened to avoid noise
          derLength: binaryDer.length,
        })
      );
    } catch (_) {}
    loggedKeyFingerprint = true;
  }
  return crypto.subtle.importKey(
    "pkcs8",
    binaryDer.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    true, // extractable for debugging (can export public key if needed)
    ["sign"]
  );
};

// Helper to create JWT for Google Auth using RS256. Optionally omit the `kid` header
// because in some environments Google may reject a JWT if the provided private_key_id
// does not match (e.g. key rotated but stale JSON in secret). We can fallback.
const createGoogleAuthJwt = async (c, serviceAccount, { includeKid = true } = {}) => {
  const t0 = Date.now();
  const iat = Math.floor(Date.now() / 1000);
  const exp = iat + 3600; // 1 hour
  const header = includeKid
    ? { alg: "RS256", typ: "JWT", kid: serviceAccount.private_key_id }
    : { alg: "RS256", typ: "JWT" };
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
  // Local self-verification (sanity): re-import as public key via spki derived from pkcs8 isn't trivial here.
  // Instead, we trust subtle.sign output; we log first bytes hex for support.
  const sigHex = Array.from(new Uint8Array(sigBuf))
    .slice(0, 8)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
  const jwt = `${data}.${signature}`;
  c?.executionCtx &&
    console.log(
      JSON.stringify({
        scope: "gplay.auth.jwt",
        event: "jwt_created",
        kid: includeKid ? serviceAccount.private_key_id : undefined,
        includeKid,
        iss: serviceAccount.client_email,
        elapsedMs: Date.now() - t0,
        headerB64Len: encodedHeader.length,
        payloadB64Len: encodedPayload.length,
        sigLen: signature.length,
        sigFirstBytes: sigHex,
      })
    );
  return jwt;
};

// Internal: exchange a single signed assertion for an access token
const exchangeJwtForAccessToken = async (c, signedJwt, meta = {}) => {
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
    let jwtParts;
    try {
      const parts = signedJwt.split(".");
      if (parts.length === 3) {
        const decode = (s) =>
          JSON.parse(
            new TextDecoder().decode(
              Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0))
            )
          );
        jwtParts = { header: decode(parts[0]), payload: decode(parts[1]) };
      }
    } catch (e) {
      jwtParts = { decodeError: e.message };
    }
    console.error(
      JSON.stringify({
        scope: "gplay.auth",
        event: "auth_fail",
        status: response.status,
        body: errorBody.slice(0, 300),
        jwtHeader: jwtParts?.header,
        jwtPayload: jwtParts?.payload,
        jwtDecodeError: jwtParts?.decodeError,
        includeKid: meta.includeKid,
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

// Helper to get Google API Access Token with retry (kid + no kid)
const getGoogleAccessToken = async (c, serviceAccount) => {
  // First attempt (with kid)
  try {
    const jwtWithKid = await createGoogleAuthJwt(c, serviceAccount, { includeKid: true });
    return await exchangeJwtForAccessToken(c, jwtWithKid, { includeKid: true });
  } catch (e) {
    if (/(invalid_grant).*Invalid JWT Signature/i.test(e.message)) {
      console.warn(
        JSON.stringify({
          scope: "gplay.auth",
          event: "retry_without_kid",
          message: "Retrying Google OAuth without kid header due to invalid signature.",
        })
      );
      try {
        const jwtNoKid = await createGoogleAuthJwt(c, serviceAccount, { includeKid: false });
        return await exchangeJwtForAccessToken(c, jwtNoKid, { includeKid: false });
      } catch (inner) {
        throw inner; // propagate
      }
    }
    throw e; // non-signature related failure
  }
};

/**
 * Verifies a Google Play subscription purchase token.
 */
export const verifyGooglePurchase = async (c, purchaseToken, subscriptionId) => {
  try {
    const t0 = Date.now();
    const serviceAccountJson = c.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
    if (!serviceAccountJson) {
      return { success: false, error: "Google Play verification is not configured." };
    }
    const serviceAccount = JSON.parse(serviceAccountJson);
    const packageName = "org.milliytechnology.spiko";

    const accessToken = await getGoogleAccessToken(c, serviceAccount);

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
      return { success: false, error: "Purchase token is invalid or already used." };
    }

    const purchaseData = await response.json();
    console.log(
      JSON.stringify({
        scope: "gplay.verify",
        event: "response_success",
        paymentState: purchaseData.paymentState, // <-- THE FIX: Log the correct field name.
        expiryTimeMillis: purchaseData.expiryTimeMillis,
        subscriptionId,
        tokenSuffix: purchaseToken.slice(-8),
        elapsedMs: Date.now() - t0,
      })
    );

    // If the API call was successful, we consider the token valid.
    // Return the full payload for the calling service to inspect.
    return { success: true, purchaseInfo: purchaseData };
  } catch (error) {
    console.error(
      JSON.stringify({
        scope: "gplay.verify",
        event: "exception",
        message: error.message,
        stack: (error.stack || "").split("\n").slice(0, 3).join(" | "),
      })
    );
    return { success: false, error: "An internal server error occurred during verification." };
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

    const accessToken = await getGoogleAccessToken(c, serviceAccount);

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

    const accessToken = await getGoogleAccessToken(c, serviceAccount);

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
 * Handles incoming Real-time Developer Notifications from Google Play with robust logic
 * to prevent incorrect downgrades due to old subscription events.
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
  console.log(
    JSON.stringify({
      scope: "gplay.rtdn.v2",
      event: "received",
      notificationType,
      subscriptionId,
      tokenSuffix: purchaseToken?.slice(-8),
    })
  );

  const transaction = await db.getPaymentTransactionByProviderId(c.env.DB, "google", purchaseToken);
  if (!transaction) {
    console.error(JSON.stringify({ scope: "gplay.rtdn.v2", event: "unknown_token" }));
    // Future: Could potentially backfill if needed, but for now, we ignore unknown tokens.
    return;
  }

  const userId = transaction.userId;
  const user = await db.getUserById(c.env.DB, userId);
  if (!user) {
    console.error(`CRITICAL: User ${userId} not found for a valid RTDN.`);
    return;
  }

  // --- START OF THE FIX: Smarter Downgrade Logic ---
  switch (notificationType) {
    case 12: // SUBSCRIPTION_REVOKED
    case 13: // SUBSCRIPTION_EXPIRED
      console.log(
        `Expiration/Revocation notice for user: ${userId}. Verifying current active subscriptions.`
      );

      // Get ALL of the user's completed Google transactions, newest first.
      const { results: allTransactions } = await db.getAllCompletedGoogleTransactionsForUser(
        c.env.DB,
        userId
      );

      let latestActiveSub = null;

      // Loop through all past purchases to find the "best" active one.
      for (const trans of allTransactions) {
        const details = await getSubscriptionDetails(c, trans.providerTransactionId, trans.planId);
        if (details.success) {
          const state = details.subscription.paymentState;
          const expiry = new Date(parseInt(details.subscription.expiryTimeMillis, 10));

          // If the subscription is active (paid or in trial) and not expired...
          if ((state === 1 || state === 2) && expiry > new Date()) {
            // ...this is their current valid subscription.
            latestActiveSub = {
              tier: PLANS[trans.planId]?.tier,
              expiresAt: expiry.toISOString(),
            };
            // Since we sorted by newest first, the first one we find is the right one.
            break;
          }
        }
      }

      if (latestActiveSub) {
        // We found a different active subscription! Update the user to reflect this.
        console.log(
          `User has another active subscription (${latestActiveSub.tier}). Updating profile instead of downgrading.`
        );
        await db.updateUserSubscription(c.env.DB, userId, latestActiveSub);
      } else {
        // We checked all purchases and NONE are active. NOW it's safe to downgrade.
        console.log(
          `No other active subscriptions found for user: ${userId}. Reverting to 'free' tier.`
        );
        await db.updateUserSubscription(c.env.DB, userId, { tier: "free", expiresAt: null });
      }
      break;

    // --- Other cases remain the same ---
    case 4: // SUBSCRIPTION_PURCHASED
    case 2: // SUBSCRIPTION_RENEWED
    case 1: // SUBSCRIPTION_RECOVERED
      console.log(`Renewing/Activating subscription for user: ${userId}`);
      const details = await getSubscriptionDetails(c, purchaseToken, subscriptionId);
      if (details.success) {
        const plan = PLANS[subscriptionId];
        const newExpiryDate = new Date(parseInt(details.subscription.expiryTimeMillis, 10));
        await db.updateUserSubscription(c.env.DB, userId, {
          tier: plan.tier,
          expiresAt: newExpiryDate.toISOString(),
        });
        console.log(`Subscription for ${userId} extended to ${newExpiryDate.toISOString()}`);
      }
      break;

    case 3: // SUBSCRIPTION_CANCELED
      console.log(`User ${userId} cancelled. Access remains until expiry.`);
      // No action needed.
      break;

    // Other cases you might handle in the future
    case 5: // SUBSCRIPTION_ON_HOLD
    case 6: // SUBSCRIPTION_IN_GRACE_PERIOD
      console.log(`Subscription for user ${userId} is on hold or in grace period.`);
      // No downgrade action needed yet.
      break;

    default:
      console.log(`Received unhandled notification type: ${notificationType}. No action taken.`);
  }
};
