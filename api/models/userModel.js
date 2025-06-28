const mongoose = require("mongoose");

const userSchema = new mongoose.Schema(
  {
    // Email is optional and not a unique identifier. It's just profile data.
    email: {
      type: String,
      lowercase: true,
      trim: true,
    },

    // The method used to create and log in to the account.
    authProvider: {
      type: String,
      required: true,
      enum: ["google", "telegram", "apple"], // List of supported providers
    },

    // --- Provider-Specific Unique IDs ---
    // We use sparse indexes to allow multiple documents to have a null value for these fields,
    // but enforce that any non-null value must be unique across the collection.

    googleId: {
      type: String,
      unique: true,
      sparse: true,
      index: true,
    },
    telegramId: {
      type: Number,
      unique: true,
      sparse: true,
      index: true,
    },
    appleId: {
      type: String,
      unique: true,
      sparse: true,
      index: true,
    },

    // --- Profile Information (can be populated from providers) ---
    firstName: {
      type: String,
    },
    username: {
      // Specifically for Telegram's @username
      type: String,
    },

    // --- Subscription & Monetization Logic ---
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
      // For auto-renewing subscriptions, this is the ID from the payment provider.
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

    // --- Usage Tracking for Freemium Limits ---
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
    // Note: For Silver tier's monthly limit, you would add a similar 'monthlyUsage' object here.
  },
  { timestamps: true }
); // Adds createdAt and updatedAt automatically

module.exports = mongoose.model("User", userSchema);
