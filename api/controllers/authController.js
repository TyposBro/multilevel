// {PATH_TO_PROJECT}/api/controllers/authController.js
const User = require("../models/UserModel");
const generateToken = require("../utils/generateToken");
const { OAuth2Client } = require("google-auth-library");

const client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

// @desc    Register a new user
// @route   POST /api/auth/register
// @access  Public
const registerUser = async (req, res) => {
  const { email, password } = req.body;

  if (!email || !password) {
    return res.status(400).json({ message: "Please provide email and password" });
  }

  try {
    const userExists = await User.findOne({ email });

    if (userExists) {
      // --- Optional but recommended: Handle conflicts ---
      if (userExists.authProvider === "google") {
        return res.status(400).json({
          message: "This email was used to sign up with Google. Please use Google Sign-In.",
        });
      }
      return res.status(400).json({ message: "User already exists with this email." });
    }

    // --- CHANGE: Explicitly set authProvider ---
    const user = await User.create({
      email,
      password,
      authProvider: "email", // Ensure this is set for standard registration
    });

    if (user) {
      res.status(201).json({
        _id: user._id,
        email: user.email,
        token: generateToken(user._id),
      });
    } else {
      res.status(400).json({ message: "Invalid user data" });
    }
  } catch (error) {
    console.error("Registration Error:", error);
    if (error.name === "ValidationError") {
      return res.status(400).json({ message: error.message });
    }
    res.status(500).json({ message: "Server error during registration" });
  }
};

// @desc    Authenticate user & get token (Login)
// @route   POST /api/auth/login
// @access  Public
const loginUser = async (req, res) => {
  // ... loginUser function remains the same ...
  const { email, password } = req.body;

  if (!email || !password) {
    return res.status(400).json({ message: "Please provide email and password" });
  }

  try {
    const user = await User.findOne({ email }).select("+password");

    // --- CHANGE: Add check for auth provider ---
    if (user && user.authProvider === "google") {
      return res
        .status(400)
        .json({ message: "This account uses Google Sign-In. Please log in with Google." });
    }

    if (user && (await user.matchPassword(password))) {
      res.json({
        _id: user._id,
        email: user.email,
        token: generateToken(user._id),
      });
    } else {
      res.status(401).json({ message: "Invalid credentials" });
    }
  } catch (error) {
    console.error("Login Error:", error);
    res.status(500).json({ message: "Server error during login" });
  }
};

// @desc    Get user profile (Example of a protected route)
// @route   GET /api/auth/profile
// @access  Private
const getUserProfile = async (req, res) => {
  // req.user is attached by the 'protect' middleware
  res.json({
    _id: req.user._id,
    email: req.user.email,
    createdAt: req.user.createdAt,
  });
};

// @desc    Authenticate user with Google & get token
// @route   POST /api/auth/google-signin
// @access  Public
const googleSignIn = async (req, res) => {
  const { idToken } = req.body;

  if (!idToken) {
    return res.status(400).json({ message: "Google ID token is required." });
  }

  try {
    // 1. Verify the ID token with Google
    const ticket = await client.verifyIdToken({
      idToken,
      audience: process.env.GOOGLE_CLIENT_ID,
    });
    const { email, name } = ticket.getPayload(); // You can also get 'name', 'picture' etc.

    // 2. Find user in your DB
    let user = await User.findOne({ email });

    // 3. If user doesn't exist, create a new one
    if (!user) {
      user = await User.create({
        email,
        authProvider: "google",
        // Note: No password is set
      });
    }

    // 4. If user exists but signed up with email, handle it
    // This is an important edge case. Here, we just log them in.
    // You could also return an error if you want to keep accounts separate.
    if (user.authProvider === "email") {
      console.log(`User ${email} originally signed up with email/password. Logging in via Google.`);
    }

    // 5. Generate your own JWT and send it back to the client
    res.status(200).json({
      _id: user._id,
      email: user.email,
      token: generateToken(user._id),
    });
  } catch (error) {
    console.error("Google Sign-In Error:", error);
    // This can happen if the token is invalid, expired, or the audience doesn't match
    res.status(401).json({ message: "Google Sign-In failed. Invalid token." });
  }
};

module.exports = {
  registerUser,
  loginUser,
  getUserProfile,
  googleSignIn,
};
