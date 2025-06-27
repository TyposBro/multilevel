// {PATH_TO_PROJECT}/api/models/userModel.js

const mongoose = require("mongoose");
const bcrypt = require("bcryptjs");

const userSchema = new mongoose.Schema({
  email: {
    type: String,
    required: [true, "Please provide an email"],
    unique: true,
    lowercase: true,
    match: [
      /^\w+([\.-]?\w+)*@\w+([\.-]?\w+)*(\.\w{2,3})+$/,
      "Please provide a valid email address",
    ],
    trim: true,
  },
  password: {
    type: String,
    minlength: 6,
    select: false,
  },
  authProvider: {
    type: String,
    required: true,
    enum: ["email", "google"],
    default: "email",
  },
  // --- NEW: SUBSCRIPTION & USAGE LOGIC ---
  subscription: {
    tier: {
      type: String,
      enum: ["free", "silver", "gold"],
      default: "free",
    },
    // The date when the current subscription or one-time purchase expires.
    expiresAt: {
      type: Date,
      default: null,
    },
    // For auto-renewing subscriptions, this is the ID from the payment provider (e.g., Google Play).
    providerSubscriptionId: {
      type: String,
      default: null,
    },
    // Tracks if the user has already activated their one-time Gold trial.
    hasUsedGoldTrial: {
      type: Boolean,
      default: false,
    },
  },
  dailyUsage: {
    fullExams: {
      count: { type: Number, default: 0 },
      lastReset: { type: Date, default: () => new Date() },
    },
    partPractices: {
      count: { type: Number, default: 0 },
      lastReset: { type: Date, default: () => new Date() },
    },
  },
  // --- END OF NEW LOGIC ---
  createdAt: {
    type: Date,
    default: Date.now,
  },
});

// Mongoose middleware remains the same...

userSchema.pre("save", async function (next) {
  if (this.authProvider !== "email" || !this.isModified("password")) {
    return next();
  }
  const salt = await bcrypt.genSalt(10);
  this.password = await bcrypt.hash(this.password, salt);
  next();
});

userSchema.methods.matchPassword = async function (enteredPassword) {
  return await bcrypt.compare(enteredPassword, this.password);
};

module.exports = mongoose.model("User", userSchema);
