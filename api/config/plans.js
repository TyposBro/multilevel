const PLANS = {
  // --- Monthly Recurring Subscriptions ---
  silver_monthly: {
    tier: "silver",
    durationDays: 30,
    prices: {
      uzs: 1500000, // 15,000 UZS in Tiyin
      usd: 149, // $1.49 in cents for Google Play
    },
    // IDs from each payment provider's dashboard
    providerIds: {
      google: "silver_monthly_subscription_id", // Your Google Play Subscription ID
      payme: "product_id_for_silver_monthly", // Your Payme product/receipt identifier
      click: "service_id_for_silver_monthly", // Your Click service ID
    },
  },
  gold_monthly: {
    tier: "gold",
    durationDays: 30,
    prices: {
      uzs: 5000000, // 50,000 UZS in Tiyin
      usd: 499, // $4.99 in cents
    },
    providerIds: {
      google: "gold_monthly_subscription_id",
      payme: "product_id_for_gold_monthly",
      click: "service_id_for_gold_monthly",
    },
  },

  // --- One-Time Purchases (Non-Recurring) ---
  gold_one_time_month: {
    tier: "gold",
    durationDays: 30,
    prices: {
      uzs: 5000000, // Same price, different product type
      usd: 499,
    },
    providerIds: {
      // This would be an "In-App Product" ID in Google Play, not a "Subscription" ID
      google: "gold_one_time_purchase_id",
      payme: "product_id_for_gold_one_time",
      click: "service_id_for_gold_one_time",
    },
  },
};

module.exports = PLANS;
