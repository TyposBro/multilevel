// {PATH_TO_PROJECT}/src/controllers/authController.js

import { db } from "../db/d1-client";
import { generateToken } from "../utils/generateToken";

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

    // --- START OF FINAL FIX ---
    // This function will be executed in the background. By placing a try/catch
    // INSIDE it, we ensure that any error (like a failed fetch) is contained
    // and cannot possibly affect the main request handler's execution.
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
    // --- END OF FINAL FIX ---

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
 * @desc    Serves a simple HTML page that redirects to the mobile app deep link.
 */
export const telegramRedirect = (c) => {
  const token = c.req.query("token");
  if (!token) {
    return c.html("<html><body>Error: Missing login token. Please try again.</body></html>", 400);
  }

  const deepLink = `multilevelapp://login?token=${token}`;

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
          window.location.replace("${deepLink}");
        </script>
      </head>
      <body>
        <p>Redirecting you to the app...</p>
        <p>If you are not redirected automatically, <a href="${deepLink}">click here to log in</a>.</p>
      </body>
    </html>
  `);
};
