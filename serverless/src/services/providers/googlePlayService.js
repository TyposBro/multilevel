// {PATH_TO_PROJECT}/src/services/providers/googlePlayService.js
import { sign } from "hono/jwt";

const GOOGLE_AUTH_URL = "https://oauth2.googleapis.com/token";
const GOOGLE_API_SCOPES = ["https://www.googleapis.com/auth/androidpublisher"];
const GOOGLE_PLAY_API_BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3";

// Cache for access tokens to avoid frequent JWT signing
const tokenCache = new Map();
const TOKEN_CACHE_DURATION = 3000; // 50 minutes (tokens expire in 1 hour)

/**
 * Clears the token cache - useful for testing
 */
export const clearTokenCache = () => {
  tokenCache.clear();
};

/**
 * Creates a signed JWT to authenticate with Google APIs using a service account.
 * @param {object} c - The Hono context.
 * @param {object} serviceAccount - The parsed service account JSON.
 * @returns {Promise<string>} The signed JWT.
 */
const createGoogleAuthJwt = async (c, serviceAccount) => {
  const iat = Math.floor(Date.now() / 1000);
  const exp = iat + 3600; // Token is valid for 1 hour

  const payload = {
    iss: serviceAccount.client_email,
    scope: GOOGLE_API_SCOPES.join(" "),
    aud: GOOGLE_AUTH_URL,
    exp,
    iat,
  };

  try {
    return await sign(payload, serviceAccount.private_key, "RS256");
  } catch (error) {
    console.error("Failed to create JWT:", error);
    throw new Error("JWT creation failed. Check your service account private key.");
  }
};

/**
 * Exchanges the signed JWT for a Google API access token with caching.
 * @param {object} c - The Hono context.
 * @param {string} signedJwt - The signed JWT.
 * @param {string} cacheKey - Cache key for the token.
 * @returns {Promise<string>} The access token.
 */
const getGoogleAccessToken = async (c, signedJwt, cacheKey) => {
  // Check cache first
  const cached = tokenCache.get(cacheKey);
  if (cached && Date.now() < cached.expires) {
    console.log("Using cached Google access token");
    return cached.token;
  }

  console.log("Fetching new Google access token");
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
    console.error("Failed to get Google access token:", response.status, errorBody);
    throw new Error(`Google auth failed: ${response.status} - ${errorBody}`);
  }

  const data = await response.json();
  
  // Cache the token
  tokenCache.set(cacheKey, {
    token: data.access_token,
    expires: Date.now() + TOKEN_CACHE_DURATION * 1000,
  });

  return data.access_token;
};

/**
 * Validates and parses the service account JSON.
 * @param {string} serviceAccountJson - The service account JSON string.
 * @returns {object} The parsed and validated service account.
 */
const validateServiceAccount = (serviceAccountJson) => {
  let serviceAccount;
  try {
    serviceAccount = JSON.parse(serviceAccountJson);
  } catch (error) {
    throw new Error("Invalid service account JSON format");
  }

  const requiredFields = ['client_email', 'private_key', 'project_id'];
  for (const field of requiredFields) {
    if (!serviceAccount[field]) {
      throw new Error(`Missing required field in service account: ${field}`);
    }
  }

  return serviceAccount;
};

/**
 * Verifies a Google Play subscription purchase token.
 * @param {object} c - The Hono context.
 * @param {string} purchaseToken - The purchase token from the Android app.
 * @param {string} subscriptionId - The ID of the subscription product (e.g., 'gold_monthly').
 * @returns {Promise<{success: boolean, planId?: string, error?: string, purchaseInfo?: object}>}
 */
export const verifyGooglePurchase = async (c, purchaseToken, subscriptionId) => {
  const startTime = Date.now();
  
  try {
    // Input validation
    if (!purchaseToken || typeof purchaseToken !== 'string') {
      return { success: false, error: "Invalid purchase token provided" };
    }
    
    if (!subscriptionId || typeof subscriptionId !== 'string') {
      return { success: false, error: "Invalid subscription ID provided" };
    }

    // Get and validate service account
    const serviceAccountJson = c.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
    if (!serviceAccountJson) {
      console.error("Google Service Account JSON not configured in environment");
      return { success: false, error: "Google Play verification not configured" };
    }

    const serviceAccount = validateServiceAccount(serviceAccountJson);
    const packageName = c.env.GOOGLE_PLAY_PACKAGE_NAME || "org.milliytechnology.spiko";
    const cacheKey = `${serviceAccount.client_email}:${packageName}`;

    console.log(`Verifying Google Play purchase: ${subscriptionId} for package: ${packageName}`);

    // 1. Create JWT and get Access Token
    const signedJwt = await createGoogleAuthJwt(c, serviceAccount);
    const accessToken = await getGoogleAccessToken(c, signedJwt, cacheKey);

    // 2. Call Google Play Developer API to verify the purchase
    const url = `${GOOGLE_PLAY_API_BASE}/applications/${packageName}/purchases/subscriptions/${subscriptionId}/tokens/${purchaseToken}`;

    const response = await fetch(url, {
      headers: { 
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
        'User-Agent': 'Spiko-ServerlessAPI/1.0'
      },
    });

    if (!response.ok) {
      const errorBody = await response.text();
      console.error(`Google Play API error (${response.status}):`, errorBody);
      
      // Handle specific error cases
      if (response.status === 400) {
        return { success: false, error: "Invalid purchase token or subscription ID" };
      } else if (response.status === 401) {
        return { success: false, error: "Authentication failed with Google Play" };
      } else if (response.status === 404) {
        return { success: false, error: "Purchase not found" };
      } else {
        return { success: false, error: `Google Play API error: ${response.status}` };
      }
    }

    const purchaseData = await response.json();
    console.log("Google Play purchase data received:", {
      purchaseState: purchaseData.purchaseState,
      autoRenewing: purchaseData.autoRenewing,
      expiryTime: purchaseData.expiryTimeMillis,
    });

    // 3. Validate purchase state
    const validationResult = validatePurchaseState(purchaseData, subscriptionId);
    
    const duration = Date.now() - startTime;
    console.log(`Google Play verification completed in ${duration}ms`);
    
    return validationResult;
    
  } catch (error) {
    const duration = Date.now() - startTime;
    console.error(`Google Play verification failed after ${duration}ms:`, error.message);
    return { success: false, error: "Internal error during purchase verification" };
  }
};

/**
 * Validates the purchase state and returns appropriate result.
 * @param {object} purchaseData - The purchase data from Google Play API.
 * @param {string} subscriptionId - The subscription ID.
 * @returns {object} Validation result.
 */
const validatePurchaseState = (purchaseData, subscriptionId) => {
  const { purchaseState, autoRenewing, expiryTimeMillis, cancelReason } = purchaseData;
  
  // Purchase states: 0 = PURCHASED, 1 = CANCELED
  switch (purchaseState) {
    case 0: // PURCHASED
      const now = Date.now();
      const expiryTime = parseInt(expiryTimeMillis);
      
      if (expiryTime && expiryTime < now) {
        return { 
          success: false, 
          error: "Subscription has expired",
          purchaseInfo: {
            expired: true,
            expiryTime: new Date(expiryTime).toISOString()
          }
        };
      }
      
      return { 
        success: true, 
        planId: subscriptionId,
        purchaseInfo: {
          autoRenewing: Boolean(autoRenewing),
          expiryTime: expiryTime ? new Date(expiryTime).toISOString() : null,
          purchaseState: 'active'
        }
      };
      
    case 1: // CANCELED
      return { 
        success: false, 
        error: `Subscription is canceled${cancelReason ? ` (reason: ${cancelReason})` : ''}`,
        purchaseInfo: {
          canceled: true,
          cancelReason
        }
      };
      
    default:
      return { 
        success: false, 
        error: `Unknown purchase state: ${purchaseState}`,
        purchaseInfo: { purchaseState }
      };
  }
};

/**
 * Verifies a one-time product purchase (for non-subscription items).
 * @param {object} c - The Hono context.
 * @param {string} purchaseToken - The purchase token from the Android app.
 * @param {string} productId - The ID of the product.
 * @returns {Promise<{success: boolean, planId?: string, error?: string}>}
 */
export const verifyGoogleProductPurchase = async (c, purchaseToken, productId) => {
  try {
    const serviceAccountJson = c.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
    if (!serviceAccountJson) {
      return { success: false, error: "Google Play verification not configured" };
    }

    const serviceAccount = validateServiceAccount(serviceAccountJson);
    const packageName = c.env.GOOGLE_PLAY_PACKAGE_NAME || "org.milliytechnology.spiko";
    const cacheKey = `${serviceAccount.client_email}:${packageName}`;

    console.log(`Verifying Google Play product purchase: ${productId}`);

    const signedJwt = await createGoogleAuthJwt(c, serviceAccount);
    const accessToken = await getGoogleAccessToken(c, signedJwt, cacheKey);

    const url = `${GOOGLE_PLAY_API_BASE}/applications/${packageName}/purchases/products/${productId}/tokens/${purchaseToken}`;

    const response = await fetch(url, {
      headers: { 
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
    });

    if (!response.ok) {
      const errorBody = await response.text();
      console.error("Google Play product verification failed:", errorBody);
      return { success: false, error: "Product purchase verification failed" };
    }

    const data = await response.json();

    // For products: 0 = PURCHASED, 1 = CANCELED
    if (data.purchaseState === 0 && data.consumptionState !== 1) {
      return { success: true, planId: productId };
    } else {
      return { success: false, error: `Product purchase is not valid. State: ${data.purchaseState}` };
    }
  } catch (error) {
    console.error("Error during Google Play product verification:", error.message);
    return { success: false, error: "Internal error during product verification" };
  }
};

/**
 * Gets subscription details for a user (useful for subscription management).
 * @param {object} c - The Hono context.
 * @param {string} purchaseToken - The purchase token.
 * @param {string} subscriptionId - The subscription ID.
 * @returns {Promise<{success: boolean, subscription?: object, error?: string}>}
 */
export const getSubscriptionDetails = async (c, purchaseToken, subscriptionId) => {
  try {
    const serviceAccountJson = c.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
    if (!serviceAccountJson) {
      return { success: false, error: "Google Play not configured" };
    }

    const serviceAccount = validateServiceAccount(serviceAccountJson);
    const packageName = c.env.GOOGLE_PLAY_PACKAGE_NAME || "org.milliytechnology.spiko";
    const cacheKey = `${serviceAccount.client_email}:${packageName}`;

    const signedJwt = await createGoogleAuthJwt(c, serviceAccount);
    const accessToken = await getGoogleAccessToken(c, signedJwt, cacheKey);

    const url = `${GOOGLE_PLAY_API_BASE}/applications/${packageName}/purchases/subscriptions/${subscriptionId}/tokens/${purchaseToken}`;

    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    if (!response.ok) {
      return { success: false, error: "Failed to get subscription details" };
    }

    const data = await response.json();
    
    return {
      success: true,
      subscription: {
        purchaseState: data.purchaseState,
        autoRenewing: data.autoRenewing,
        expiryTime: data.expiryTimeMillis ? new Date(parseInt(data.expiryTimeMillis)).toISOString() : null,
        startTime: data.startTimeMillis ? new Date(parseInt(data.startTimeMillis)).toISOString() : null,
        cancelReason: data.cancelReason,
        userCancellationTime: data.userCancellationTimeMillis ? new Date(parseInt(data.userCancellationTimeMillis)).toISOString() : null
      }
    };
  } catch (error) {
    console.error("Error getting subscription details:", error.message);
    return { success: false, error: "Failed to retrieve subscription details" };
  }
};

/**
 * Handles Google Play Real-time Developer Notifications (RTDN) webhook.
 * This processes notifications about subscription renewals, cancellations, etc.
 * @param {object} c - The Hono context.
 * @param {object} notificationData - The decoded notification data from Google.
 * @returns {Promise<{success: boolean, processed?: boolean, error?: string}>}
 */
export const handleGooglePlayWebhook = async (c, notificationData) => {
  try {
    const { subscriptionNotification, testNotification } = notificationData;
    
    // Handle test notifications
    if (testNotification) {
      console.log("Received Google Play test notification:", testNotification);
      return { success: true, processed: true };
    }

    if (!subscriptionNotification) {
      console.warn("Received Google Play notification without subscription data");
      return { success: false, error: "No subscription notification data" };
    }

    const {
      version,
      notificationType,
      purchaseToken,
      subscriptionId
    } = subscriptionNotification;

    console.log(`Processing Google Play notification: type=${notificationType}, subscription=${subscriptionId}`);

    // Get current subscription details
    const detailsResult = await getSubscriptionDetails(c, purchaseToken, subscriptionId);
    if (!detailsResult.success) {
      console.error("Failed to get subscription details for webhook:", detailsResult.error);
      return { success: false, error: "Failed to retrieve subscription details" };
    }

    const subscription = detailsResult.subscription;
    
    // Process different notification types
    const result = await processSubscriptionNotification(c, {
      notificationType,
      purchaseToken,
      subscriptionId,
      subscription,
      version
    });

    return result;

  } catch (error) {
    console.error("Error processing Google Play webhook:", error.message);
    return { success: false, error: "Internal error processing webhook" };
  }
};

/**
 * Processes different types of subscription notifications.
 * @param {object} c - The Hono context.
 * @param {object} params - Notification parameters.
 * @returns {Promise<{success: boolean, processed?: boolean, action?: string}>}
 */
const processSubscriptionNotification = async (c, { notificationType, purchaseToken, subscriptionId, subscription, version }) => {
  const db = c.env.DB;
  
  try {
    // Find user by purchase token in our database
    const userResult = await db.prepare(`
      SELECT user_id, plan_id FROM payment_transactions 
      WHERE payment_provider = 'google_play' 
      AND reference_id = ? 
      AND status = 'completed'
      ORDER BY created_at DESC LIMIT 1
    `).bind(purchaseToken).first();

    if (!userResult) {
      console.warn(`No user found for Google Play purchase token: ${purchaseToken}`);
      return { success: true, processed: false, action: "no_user_found" };
    }

    const userId = userResult.user_id;
    
    switch (notificationType) {
      case 1: // SUBSCRIPTION_RECOVERED
        console.log(`Subscription recovered for user ${userId}`);
        await updateUserSubscription(c, userId, subscriptionId, 'active', subscription.expiryTime);
        return { success: true, processed: true, action: "subscription_recovered" };

      case 2: // SUBSCRIPTION_RENEWED
        console.log(`Subscription renewed for user ${userId}`);
        await updateUserSubscription(c, userId, subscriptionId, 'active', subscription.expiryTime);
        await recordRenewalTransaction(c, userId, subscriptionId, purchaseToken, subscription);
        return { success: true, processed: true, action: "subscription_renewed" };

      case 3: // SUBSCRIPTION_CANCELED
        console.log(`Subscription canceled for user ${userId}`);
        // Don't immediately deactivate - wait for expiry
        await updateSubscriptionCancelStatus(c, userId, true, subscription.userCancellationTime);
        return { success: true, processed: true, action: "subscription_canceled" };

      case 4: // SUBSCRIPTION_PURCHASED
        console.log(`New subscription purchased for user ${userId}`);
        await updateUserSubscription(c, userId, subscriptionId, 'active', subscription.expiryTime);
        return { success: true, processed: true, action: "subscription_purchased" };

      case 5: // SUBSCRIPTION_ON_HOLD
        console.log(`Subscription on hold for user ${userId}`);
        await updateUserSubscription(c, userId, subscriptionId, 'on_hold', subscription.expiryTime);
        return { success: true, processed: true, action: "subscription_on_hold" };

      case 6: // SUBSCRIPTION_IN_GRACE_PERIOD
        console.log(`Subscription in grace period for user ${userId}`);
        await updateUserSubscription(c, userId, subscriptionId, 'grace_period', subscription.expiryTime);
        return { success: true, processed: true, action: "subscription_grace_period" };

      case 7: // SUBSCRIPTION_RESTARTED
        console.log(`Subscription restarted for user ${userId}`);
        await updateUserSubscription(c, userId, subscriptionId, 'active', subscription.expiryTime);
        return { success: true, processed: true, action: "subscription_restarted" };

      case 8: // SUBSCRIPTION_PRICE_CHANGE_CONFIRMED
        console.log(`Price change confirmed for user ${userId}`);
        return { success: true, processed: true, action: "price_change_confirmed" };

      case 9: // SUBSCRIPTION_DEFERRED
        console.log(`Subscription deferred for user ${userId}`);
        return { success: true, processed: true, action: "subscription_deferred" };

      case 10: // SUBSCRIPTION_PAUSED
        console.log(`Subscription paused for user ${userId}`);
        await updateUserSubscription(c, userId, subscriptionId, 'paused', subscription.expiryTime);
        return { success: true, processed: true, action: "subscription_paused" };

      case 11: // SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED
        console.log(`Subscription pause schedule changed for user ${userId}`);
        return { success: true, processed: true, action: "pause_schedule_changed" };

      case 12: // SUBSCRIPTION_REVOKED
        console.log(`Subscription revoked for user ${userId}`);
        await updateUserSubscription(c, userId, subscriptionId, 'revoked', null);
        return { success: true, processed: true, action: "subscription_revoked" };

      case 13: // SUBSCRIPTION_EXPIRED
        console.log(`Subscription expired for user ${userId}`);
        await updateUserSubscription(c, userId, subscriptionId, 'expired', subscription.expiryTime);
        return { success: true, processed: true, action: "subscription_expired" };

      default:
        console.warn(`Unknown notification type: ${notificationType}`);
        return { success: true, processed: false, action: "unknown_notification_type" };
    }
  } catch (error) {
    console.error("Error processing subscription notification:", error.message);
    return { success: false, error: "Database error processing notification" };
  }
};

/**
 * Updates user subscription status in the database.
 */
const updateUserSubscription = async (c, userId, planId, status, expiryTime) => {
  const db = c.env.DB;
  
  await db.prepare(`
    UPDATE users 
    SET plan_id = ?, subscription_status = ?, subscription_expires_at = ?
    WHERE id = ?
  `).bind(planId, status, expiryTime, userId).run();

  console.log(`Updated subscription for user ${userId}: plan=${planId}, status=${status}, expires=${expiryTime}`);
};

/**
 * Updates subscription cancellation status.
 */
const updateSubscriptionCancelStatus = async (c, userId, isCanceled, canceledAt) => {
  const db = c.env.DB;
  
  await db.prepare(`
    UPDATE users 
    SET subscription_canceled = ?, subscription_canceled_at = ?
    WHERE id = ?
  `).bind(isCanceled ? 1 : 0, canceledAt, userId).run();

  console.log(`Updated cancellation status for user ${userId}: canceled=${isCanceled}, at=${canceledAt}`);
};

/**
 * Records a subscription renewal transaction.
 */
const recordRenewalTransaction = async (c, userId, planId, purchaseToken, subscription) => {
  const db = c.env.DB;
  
  try {
    await db.prepare(`
      INSERT INTO payment_transactions (
        user_id, plan_id, amount, currency, payment_provider, 
        reference_id, status, created_at, metadata
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      userId,
      planId,
      0, // Renewal amount - would need to be fetched from pricing
      'USD', // Default currency
      'google_play',
      purchaseToken,
      'completed',
      new Date().toISOString(),
      JSON.stringify({
        type: 'renewal',
        autoRenewing: subscription.autoRenewing,
        expiryTime: subscription.expiryTime,
        startTime: subscription.startTime
      })
    ).run();

    console.log(`Recorded renewal transaction for user ${userId}, plan ${planId}`);
  } catch (error) {
    console.error("Error recording renewal transaction:", error.message);
    // Don't fail the webhook for this
  }
};
