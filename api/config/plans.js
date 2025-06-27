// {PATH_TO_PROJECT}/api/config/plans.js

const PLANS = {
  silver_monthly: {
    tier: "silver",
    durationDays: 30,
    prices: {
      // Prices in the smallest currency unit (tiyin/cents)
      uzs: 1500000,
      usd: 149, // For Google Play
    },
    // IDs from each payment provider's dashboard
    providerIds: {
      google: "silver_monthly_sub_id", // Your Google Play Subscription ID
      payme: "product_id_silver_monthly",
      paynet: "product_id_silver_monthly",
      click: "service_id_silver_monthly",
    },
  },
  gold_monthly: {
    tier: "gold",
    durationDays: 30,
    prices: {
      uzs: 5000000,
      usd: 499,
    },
    providerIds: {
      google: "gold_monthly_sub_id",
      payme: "product_id_gold_monthly",
      paynet: "product_id_gold_monthly",
      click: "service_id_gold_monthly",
    },
  },
  // This is the ONE-TIME purchase (non-recurring subscription)
  gold_one_time_month: {
    tier: "gold",
    durationDays: 30,
    prices: {
      uzs: 5000000, // Same price, different product
      usd: 499,
    },
    providerIds: {
      google: "gold_one_time_purchase_id", // This would be an "In-App Product" in Google Play
      payme: "product_id_gold_one_time",
      paynet: "product_id_gold_one_time",
      click: "service_id_gold_one_time",
    },
  },
};

module.exports = PLANS;
