// {PATH_TO_PROJECT}/src/controllers/authController.js

import { db } from "../db/d1-client";
import { generateToken } from "../utils/generateToken";

// Add this to serverless/src/controllers/authController.js

export const reviewerLogin = async (c) => {
  try {
    const { email } = await c.req.json();
    const REVIEWER_EMAIL = "google.reviewer@typosbro.app"; // This is your "secret" username

    if (email !== REVIEWER_EMAIL) {
      return c.json({ message: "Invalid reviewer credentials." }, 401);
    }

    let user = await db.findUserByEmail(c.env.DB, REVIEWER_EMAIL);

    if (!user) {
      // Create a new reviewer user if one doesn't exist
      user = await db.createUser(c.env.DB, {
        email: REVIEWER_EMAIL,
        authProvider: "reviewer", // Use a special provider name
      });
    }

    // Grant permanent premium access
    await db.updateUserSubscription(c.env.DB, user.id, {
      tier: "gold", // Your highest tier
      expiresAt: "2999-12-31T23:59:59Z", // A "never-expires" date
    });

    // Issue a standard, time-limited JWT
    const token = await generateToken(c, user.id);
    return c.json({ _id: user.id, email: user.email, token });
  } catch (error) {
    console.error("Reviewer Login Error:", error);
    return c.json({ message: "Server error during reviewer login." }, 500);
  }
};

/**
 * @desc    Get user profile
 */
export const getUserProfile = async (c) => {
  const user = c.get("user");
  if (user) {
    return c.json({
      _id: user.id,
      email: user.email,
      firstName: user.firstName,
      telegramId: user.telegramId,
      username: user.username,
      authProvider: user.authProvider,
      createdAt: user.createdAt,
      subscription_tier: user.subscription_tier,
      subscription_expiresAt: user.subscription_expiresAt,
    });
  } else {
    return c.json({ message: "User not found in context" }, 404);
  }
};

/**
 * @desc    Authenticate user with Google & get token
 */
export const googleSignIn = async (c) => {
  const { idToken } = await c.req.json();
  if (!idToken) {
    return c.json({ message: "Google ID token is required." }, 400);
  }
  try {
    // In production, you MUST verify this token with Google's API
    const response = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${idToken}`);
    if (!response.ok) {
      throw new Error("Invalid Google token");
    }
    const { sub, email, name } = await response.json();

    let user = await db.findUserByProviderId(c.env.DB, { provider: "google", id: sub });
    if (!user) {
      user = await db.createUser(c.env.DB, {
        googleId: sub,
        email: email,
        firstName: name,
        authProvider: "google",
      });
    }

    const token = await generateToken(c, user.id);
    return c.json({ _id: user.id, email: user.email, firstName: user.firstName, token });
  } catch (error) {
    console.error("Google Sign-In Error:", error);
    return c.json({ message: "Google Sign-In failed. Invalid token." }, 401);
  }
};

/**
 * @desc    Delete user profile and all associated data
 */
export const deleteUserProfile = async (c) => {
  try {
    const user = c.get("user");
    await db.deleteUser(c.env.DB, user.id);
    return c.json({ message: "User account and all associated data deleted successfully." });
  } catch (error) {
    console.error("Delete User Error:", error);
    return c.json({ message: "Server error during account deletion." }, 500);
  }
};

/**
 * @desc    Verify a one-time token from the Telegram login flow
 */

export const verifyTelegramToken = async (c) => {
  try {
    const { oneTimeToken } = await c.req.json();
    if (!oneTimeToken) {
      return c.json({ message: "One-time token is required." }, 400);
    }

    const foundToken = await db.findOneTimeTokenAndDelete(c.env.DB, oneTimeToken);
    if (!foundToken) {
      return c.json({ message: "Invalid or expired token. Please try again." }, 401);
    }

    const TELEGRAM_API = `https://api.telegram.org/bot${c.env.TELEGRAM_BOT_TOKEN}`;

    // This function will be executed in the background.
    const deleteMessagesInBackground = async () => {
      try {
        await fetch(`${TELEGRAM_API}/deleteMessage`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            chat_id: foundToken.telegramId,
            message_id: foundToken.botMessageId,
          }),
        });
        await fetch(`${TELEGRAM_API}/deleteMessage`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            chat_id: foundToken.telegramId,
            message_id: foundToken.userMessageId,
          }),
        });
      } catch (e) {
        console.error("Non-blocking error during background message deletion:", e.message);
      }
    };
    c.executionCtx.waitUntil(deleteMessagesInBackground());

    let user = await db.findUserByProviderId(c.env.DB, {
      provider: "telegram",
      id: foundToken.telegramId,
    });
    if (!user) {
      user = await db.createUser(c.env.DB, {
        telegramId: foundToken.telegramId,
        authProvider: "telegram",
      });
    }

    const token = await generateToken(c, user.id);
    return c.json({ _id: user.id, email: user.email, firstName: user.firstName, token });
  } catch (error) {
    console.error("Error verifying one-time token:", error);
    return c.json({ message: "Server error during login." }, 500);
  }
};

/**
 * @desc    Handles web-based login via Telegram, generates JWT, and redirects to the account page.
 */
export const telegramLoginWeb = async (c) => {
  try {
    const oneTimeToken = c.req.query("token");
    if (!oneTimeToken) {
      return c.html("<html><body>Error: Missing login token.</body></html>", 400);
    }

    const foundToken = await db.findOneTimeTokenAndDelete(c.env.DB, oneTimeToken);
    if (!foundToken) {
      return c.html(
        "<html><body>Error: Invalid or expired token. Please try again.</body></html>",
        401
      );
    }

    // This can run in the background without blocking the user's login.
    const deleteMessagesInBackground = async () => {
      try {
        const TELEGRAM_API = `https://api.telegram.org/bot${c.env.TELEGRAM_BOT_TOKEN}`;
        await fetch(`${TELEGRAM_API}/deleteMessage`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            chat_id: foundToken.telegramId,
            message_id: foundToken.botMessageId,
          }),
        });
        await fetch(`${TELEGRAM_API}/deleteMessage`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            chat_id: foundToken.telegramId,
            message_id: foundToken.userMessageId,
          }),
        });
      } catch (e) {
        console.error("Non-blocking error during background message deletion:", e.message);
      }
    };
    c.executionCtx.waitUntil(deleteMessagesInBackground());

    let user = await db.findUserByProviderId(c.env.DB, {
      provider: "telegram",
      id: foundToken.telegramId,
    });
    if (!user) {
      user = await db.createUser(c.env.DB, {
        telegramId: foundToken.telegramId,
        authProvider: "telegram",
      });
    }

    const jwt = await generateToken(c, user.id);
    // Redirect to the frontend account page, passing the JWT in the URL hash.
    const redirectUrl = `${c.env.FRONTEND_ACCOUNT_URL}#token=${jwt}`;

    return c.redirect(redirectUrl);
  } catch (error) {
    console.error("Error during web-based Telegram login:", error);
    return c.html(
      "<html><body>Server error during login. Please try again later.</body></html>",
      500
    );
  }
};

/**
 * @desc    Serves a simple HTML page that redirects to the mobile app deep link.
 * @desc    Enhanced to fall back to a web login flow if the app isn't installed.
 */
export const telegramRedirect = (c) => {
  const token = c.req.query("token");
  if (!token) {
    return c.html("<html><body>Error: Missing login token. Please try again.</body></html>", 400);
  }

  const deepLink = `multilevelapp://login?token=${token}`;
  const webLoginUrl = `/api/auth/telegram/login-web?token=${token}`;

  // Use Hono's c.html() to send an HTML response
  return c.html(`
    <!DOCTYPE html>
    <html>
      <head>
        <title>Logging in...</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { font-family: sans-serif; text-align: center; padding-top: 50px; background-color: #f0f0f0; }
        </style>
        <script>
          // This script attempts to open the mobile app.
          // If the app is not installed, the user remains on this page.
          // A timeout then triggers a redirect to the web-based login flow.
          (function() {
            let redirected = false;
            function handleVisibilityChange() {
              if (document.hidden) {
                redirected = true;
              }
            }
            document.addEventListener("visibilitychange", handleVisibilityChange);

            // After a short delay, check if we've successfully navigated away.
            setTimeout(function() {
              document.removeEventListener("visibilitychange", handleVisibilityChange);
              if (!redirected) {
                window.location.replace("${webLoginUrl}");
              }
            }, 1000);

            // Immediately attempt to navigate to the app's custom URL scheme.
            window.location.href = "${deepLink}";
          })();
        </script>
      </head>
      <body>
        <p>Redirecting you to the app...</p>
        <p>If you are not redirected automatically, <a href="${webLoginUrl}">click here to log in on the web</a>.</p>
      </body>
    </html>
  `);
};
