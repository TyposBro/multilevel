// serverless/src/config/plans.js
const PLANS = {
  // --- Monthly Recurring Subscriptions ---
  // The key "silver_monthly" should exactly match Product ID in the Google Play Console.
  silver_monthly: {
    tier: "silver",
    durationDays: 30,
    prices: {
      // uzs: 1500000, // 15,000 UZS in Tiyin
      uzs: 100000, // 1,000 UZS in Tiyin
      usd: 149, // $1.49 in cents for Google Play
    },
    // providerIds are now only for providers who use different IDs than the main key
    providerIds: {
      payme: "product_id_for_silver_monthly", // Payme product/receipt identifier
      click: "80012", // Click service ID
    },
  },
  // The key "gold_monthly" should exactly match Product ID in the Google Play Console.
  gold_monthly: {
    tier: "gold",
    durationDays: 30,
    prices: {
      uzs: 5000000, // 50,000 UZS in Tiyin
      usd: 499, // $4.99 in cents
    },
    providerIds: {
      payme: "product_id_for_gold_monthly",
      click: "80012",
    },
  },

  // --- One-Time Purchases (Non-Recurring) ---
  // IMPORTANT: The key for this should be the "In-App Product" ID from Google Play.
  gold_one_time_purchase_id: {
    tier: "gold",
    durationDays: 30,
    prices: {
      uzs: 5000000, // Same price, different product type
      usd: 499,
    },
    providerIds: {
      payme: "product_id_for_gold_one_time",
      click: "80012",
    },
  },
};

module.exports = PLANS;
