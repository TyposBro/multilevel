// {PATH_TO_PROJECT}/api/controllers/authController.js
const User = require("../models/userModel");
const ExamResult = require("../models/ieltsExamResultModel");
const generateToken = require("../utils/generateToken");
const { OAuth2Client } = require("google-auth-library");
const OneTimeToken = require("../models/oneTimeTokenModel");

const client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

// @desc    Get user profile (Example of a protected route)
// @route   GET /api/auth/profile
// @access  Private
const getUserProfile = async (req, res) => {
  // The `protect` middleware has already fetched the user object and attached it to `req.user`.
  // The object contains all the fields from our userModel.
  const user = req.user;

  if (user) {
    // --- THIS IS THE FIX ---
    // Construct a response object that includes all the fields the frontend expects.
    res.json({
      _id: user._id,
      email: user.email,
      firstName: user.firstName,
      telegramId: user.telegramId,
      username: user.username,
      authProvider: user.authProvider,
      createdAt: user.createdAt,
    });
    // --- END OF FIX ---
  } else {
    // This case should ideally not be hit if the `protect` middleware is working correctly.
    res.status(404).json({ message: "User not found" });
  }
};

/**
 * @desc    Authenticate user with Google & get token
 * @route   POST /api/auth/google-signin
 * @access  Public
 */
const googleSignIn = async (req, res) => {
  const { idToken } = req.body;

  if (!idToken) {
    return res.status(400).json({ message: "Google ID token is required." });
  }

  try {
    const ticket = await client.verifyIdToken({
      idToken,
      audience: process.env.GOOGLE_CLIENT_ID,
    });

    // IMPORTANT: Use 'sub' as the unique ID from Google
    const { sub, email, name } = ticket.getPayload();

    // Find user by their unique Google ID, not email
    let user = await User.findOne({ googleId: sub });

    if (!user) {
      // If no user with this Google ID, create one.
      user = await User.create({
        googleId: sub,
        email: email,
        firstName: name,
        authProvider: "google",
      });
    }

    // Generate our app's JWT and send it back.
    // Also include firstName in the response so the app can greet the user immediately.
    res.status(200).json({
      _id: user._id,
      email: user.email,
      firstName: user.firstName,
      token: generateToken(user._id),
    });
  } catch (error) {
    console.error("Google Sign-In Error:", error);
    res.status(401).json({ message: "Google Sign-In failed. Invalid token." });
  }
};

// @desc    Delete user profile and all associated data
// @route   DELETE /api/auth/profile
// @access  Private
const deleteUserProfile = async (req, res) => {
  try {
    // req.user is attached by the 'protect' middleware. We get the user's ID from there.
    const userId = req.user._id;

    // 1. Delete all associated data first (cascading delete)
    // This prevents orphaned data in your database.
    // Adjust the field names ('userId') to match your schemas.
    await ExamResult.deleteMany({ userId: userId });
    // await Chat.deleteMany({ userId: userId }); // Uncomment when you have a Chat model
    // Add any other models associated with the user here...

    // 2. Find and delete the user themselves
    const user = await User.findByIdAndDelete(userId);

    if (!user) {
      return res.status(404).json({ message: "User not found." });
    }

    // 3. Respond with success
    console.log(`User ${userId} and their data have been deleted successfully.`);
    res.status(200).json({ message: "User account and all associated data deleted successfully." });
  } catch (error) {
    console.error("Delete User Error:", error);
    res.status(500).json({ message: "Server error during account deletion." });
  }
};

const verifyTelegramToken = async (req, res) => {
  const { oneTimeToken } = req.body;
  if (!oneTimeToken) {
    return res.status(400).json({ message: "One-time token is required." });
  }

  try {
    // 1. Find and DELETE the token in one atomic operation to ensure it's single-use.
    const foundToken = await OneTimeToken.findOneAndDelete({ token: oneTimeToken });

    if (!foundToken) {
      return res.status(401).json({ message: "Invalid or expired token. Please try again." });
    }

    // 2. We have the user's telegramId, now find or create the main user account.
    let user = await User.findOne({ telegramId: foundToken.telegramId });

    if (!user) {
      // Because the webhook doesn't give us the user's name, we can't pre-populate it here.
      // The app can fetch it later if needed.
      user = await User.create({
        telegramId: foundToken.telegramId,
        authProvider: "telegram",
      });
    }

    // 3. Issue the standard JWT
    res.status(200).json({
      _id: user._id,
      email: user.email,
      firstName: user.firstName,
      token: generateToken(user._id),
    });
  } catch (error) {
    console.error("Error verifying one-time token:", error);
    res.status(500).json({ message: "Server error during login." });
  }
};

/**
 * @desc    Serves a simple HTML page that redirects the user to the mobile app deep link.
 *          This is the target for Telegram's login_url button.
 * @route   GET /api/auth/telegram/redirect
 * @access  Public
 */
const telegramRedirect = (req, res) => {
  const { token } = req.query;
  if (!token) {
    return res
      .status(400)
      .send("<html><body>Error: Missing login token. Please try again.</body></html>");
  }

  const deepLink = `multilevelapp://login?token=${token}`;

  // Serve a simple HTML page with a JavaScript redirect.
  // This gives the user a seamless transition from Telegram's webview to your app.
  res.send(`
    <!DOCTYPE html>
    <html>
      <head>
        <title>Logging in...</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { font-family: sans-serif; text-align: center; padding-top: 50px; }
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

module.exports = {
  getUserProfile,
  googleSignIn,
  deleteUserProfile,
  verifyTelegramToken,
  telegramRedirect,
};
