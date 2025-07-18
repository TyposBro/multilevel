// {PATH_TO_PROJECT}/src/services/providers/googlePlayService.js
import { sign } from "hono/jwt";

const GOOGLE_AUTH_URL = "https://oauth2.googleapis.com/token";
const GOOGLE_API_SCOPES = ["https://www.googleapis.com/auth/androidpublisher"];

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

  return await sign(payload, serviceAccount.private_key, "RS256");
};

/**
 * Exchanges the signed JWT for a Google API access token.
 * @param {object} c - The Hono context.
 * @param {string} signedJwt - The signed JWT.
 * @returns {Promise<string>} The access token.
 */
const getGoogleAccessToken = async (c, signedJwt) => {
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
    console.error("Failed to get Google access token:", errorBody);
    throw new Error("Could not authenticate with Google.");
  }

  const data = await response.json();
  return data.access_token;
};

/**
 * Verifies a Google Play subscription purchase token.
 * @param {object} c - The Hono context.
 * @param {string} purchaseToken - The purchase token from the Android app.
 * @param {string} subscriptionId - The ID of the subscription product (e.g., 'gold_monthly').
 * @returns {Promise<{success: boolean, planId?: string, error?: string}>}
 */
export const verifyGooglePurchase = async (c, purchaseToken, subscriptionId) => {
  try {
    const serviceAccountJson = c.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
    if (!serviceAccountJson) {
      throw new Error("Google Service Account JSON is not configured.");
    }
    const serviceAccount = JSON.parse(serviceAccountJson);
    const packageName = "com.typosbro.multilevel"; // Your app's package name

    // 1. Create JWT and get Access Token
    const signedJwt = await createGoogleAuthJwt(c, serviceAccount);
    const accessToken = await getGoogleAccessToken(c, signedJwt);

    // 2. Call Google Play Developer API to verify the purchase
    const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/subscriptions/${subscriptionId}/tokens/${purchaseToken}`;

    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    if (!response.ok) {
      const errorBody = await response.text();
      console.error("Google Play verification failed:", errorBody);
      return { success: false, error: "Purchase verification failed with Google." };
    }

    const data = await response.json();

    // 3. Check purchase state. 0 = PURCHASED, 1 = CANCELED, 2 = PENDING
    if (data.purchaseState === 0) {
      return { success: true, planId: subscriptionId };
    } else {
      return { success: false, error: `Purchase is not active. State: ${data.purchaseState}` };
    }
  } catch (error) {
    console.error("Error during Google Play verification:", error.message);
    return { success: false, error: "An internal error occurred during verification." };
  }
};
