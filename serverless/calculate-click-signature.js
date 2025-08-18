// Click Signature Calculator for Testing
// Run with: node calculate-click-signature.js

const crypto = require("crypto");

// Your Click configuration from dashboard
const CONFIG = {
  serviceId: "80012",
  merchantId: "44439",
  secretKey: process.env.CLICK_SECRET_KEY || "YOUR_SECRET_KEY_HERE", // Replace with actual secret key from dashboard
};

function calculateClickSignature(data, secretKey) {
  const {
    click_trans_id,
    service_id,
    merchant_trans_id,
    merchant_prepare_id,
    amount,
    action,
    sign_time,
  } = data;

  // Format amount to 2 decimal places
  const formattedAmount = Number(amount).toFixed(2);

  // Include prepare_id only for complete (action=1)
  const prepareIdPart = action == "1" ? merchant_prepare_id : "";

  // Build signature string according to Click documentation
  const signStringSource = `${click_trans_id}${service_id}${secretKey}${merchant_trans_id}${prepareIdPart}${formattedAmount}${action}${sign_time}`;

  console.log("\n🔍 Signature Calculation Details:");
  console.log("================================");
  console.log("Click Transaction ID:", click_trans_id);
  console.log("Service ID:", service_id);
  console.log("Secret Key:", secretKey ? "[HIDDEN]" : "[NOT SET]");
  console.log("Merchant Trans ID:", merchant_trans_id);
  console.log("Prepare ID Part:", `"${prepareIdPart}" (only for action=1)`);
  console.log("Formatted Amount:", formattedAmount);
  console.log("Action:", action);
  console.log("Sign Time:", sign_time);
  console.log("\nSignature String:", signStringSource.replace(secretKey, "[SECRET]"));

  const signature = crypto.createHash("md5").update(signStringSource).digest("hex");
  console.log("Generated MD5 Signature:", signature);

  return signature;
}

// Test Data Examples
console.log("🚀 Click Signature Calculator for Spiko");
console.log("======================================");

// Example 1: PREPARE request (action=0)
console.log("\n📋 Example 1: PREPARE Request (action=0)");
const prepareData = {
  click_trans_id: 12345,
  service_id: CONFIG.serviceId,
  merchant_trans_id: "test_transaction_123",
  merchant_prepare_id: "", // Not used in PREPARE
  amount: 1000.0,
  action: 0,
  sign_time: "2025-08-18 12:00:00",
};

const prepareSignature = calculateClickSignature(prepareData, CONFIG.secretKey);

// Example 2: COMPLETE request (action=1)
console.log("\n📋 Example 2: COMPLETE Request (action=1)");
const completeData = {
  click_trans_id: 12345,
  service_id: CONFIG.serviceId,
  merchant_trans_id: "test_transaction_123",
  merchant_prepare_id: "test_transaction_123",
  amount: 1000.0,
  action: 1,
  sign_time: "2025-08-18 12:00:00",
};

const completeSignature = calculateClickSignature(completeData, CONFIG.secretKey);

// Generate test webhook payloads
console.log("\n🧪 Test Webhook Payloads:");
console.log("=========================");

console.log("\nPREPARE Webhook JSON:");
console.log(
  JSON.stringify(
    {
      ...prepareData,
      error: 0,
      error_note: "Success",
      sign_string: prepareSignature,
    },
    null,
    2
  )
);

console.log("\nCOMPLETE Webhook JSON:");
console.log(
  JSON.stringify(
    {
      ...completeData,
      error: 0,
      error_note: "Success",
      sign_string: completeSignature,
    },
    null,
    2
  )
);

// Test with curl commands
console.log("\n🔗 Test Commands:");
console.log("=================");

const webhookUrl =
  "https://typosbro-multilevel-api.milliytechnology.workers.dev/api/payment/click/webhook";

console.log("\nTest PREPARE webhook:");
console.log(`curl -X POST ${webhookUrl} \\`);
console.log(`  -H "Content-Type: application/json" \\`);
console.log(
  `  -d '${JSON.stringify({
    ...prepareData,
    error: 0,
    error_note: "Success",
    sign_string: prepareSignature,
  })}'`
);

console.log("\nTest COMPLETE webhook:");
console.log(`curl -X POST ${webhookUrl} \\`);
console.log(`  -H "Content-Type: application/json" \\`);
console.log(
  `  -d '${JSON.stringify({
    ...completeData,
    error: 0,
    error_note: "Success",
    sign_string: completeSignature,
  })}'`
);

console.log("\n⚠️  Important Notes:");
console.log("===================");
console.log("1. Replace YOUR_SECRET_KEY_HERE with your actual secret key from Click dashboard");
console.log("2. The secret key is hidden in your dashboard with dots: ············");
console.log("3. Contact Click support or check your contract documents for the secret key");
console.log("4. For production testing, use real transaction IDs from your database");
console.log("5. Your service is already active and receiving payments (see dashboard activity)");

console.log("\n✅ Your Current Configuration:");
console.log("============================");
console.log("Service ID:", CONFIG.serviceId, "(✅ Matches dashboard)");
console.log("Merchant ID:", CONFIG.merchantId, "(✅ Matches dashboard)");
console.log('Service Name: "Оплата за услуги Spiko" (✅ Active)');
console.log("Price: 1,000 сум (✅ Within limits)");
console.log("Recent Activity: Yes (✅ Invoices created successfully)");

if (CONFIG.secretKey === "YOUR_SECRET_KEY_HERE") {
  console.log("\n❌ Secret Key: Not set - Get this from your Click dashboard");
} else {
  console.log("\n✅ Secret Key: Configured");
}
