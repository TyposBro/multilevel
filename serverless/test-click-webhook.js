// test-click-webhook.js
const crypto = require("crypto");

// Script to genera// Test data for Prepare (action=0)
const testDataPrepare = {
  click_trans_id: 12345,
  service_id: 80012,
  secret_key: "OlqTnah7TU", // From your .dev.vars
  merchant_trans_id: "txn-test-123",
  amount: 1000.0,
  action: 0, // Prepare
  sign_time: "2025-08-16 07:50:00",
};

// Test data for Complete (action=1)
const testDataComplete = {
  click_trans_id: 12345,
  service_id: 80012,
  secret_key: "OlqTnah7TU", // From your .dev.vars
  merchant_trans_id: "txn-test-123",
  merchant_prepare_id: "txn-test-123", // Required for Complete
  amount: 1000.0,
  action: 1, // Complete
  sign_time: "2025-08-16 07:51:00",
};

function generateClickSignature(data) {
  const {
    click_trans_id,
    service_id,
    secret_key,
    merchant_trans_id,
    merchant_prepare_id = "",
    amount,
    action,
    sign_time,
  } = data;

  // Format amount as string with 2 decimal places
  const formattedAmount = Number(amount).toFixed(2);

  // merchant_prepare_id is only included for action=1 (Complete)
  const prepareIdPart = action == "1" ? merchant_prepare_id : "";

  // Construct signature string exactly as Click expects
  const signStringSource = `${click_trans_id}${service_id}${secret_key}${merchant_trans_id}${prepareIdPart}${formattedAmount}${action}${sign_time}`;

  console.log("Signature components:");
  console.log("- click_trans_id:", click_trans_id);
  console.log("- service_id:", service_id);
  console.log("- secret_key:", "[HIDDEN]");
  console.log("- merchant_trans_id:", merchant_trans_id);
  console.log("- prepare_id_part:", `"${prepareIdPart}"`);
  console.log("- amount (formatted):", `"${formattedAmount}"`);
  console.log("- action:", action);
  console.log("- sign_time:", sign_time);
  console.log("- sign_string_source:", signStringSource);

  // Generate MD5 hash
  const signature = crypto.createHash("md5").update(signStringSource).digest("hex");
  console.log("- generated_signature:", signature);

  return signature;
}

// Get command line argument to choose test type
const testType = process.argv[2] || "prepare";

let testData;
if (testType === "complete") {
  testData = testDataComplete;
  console.log("=== GENERATING CLICK WEBHOOK SIGNATURE FOR COMPLETE ===");
} else {
  testData = testDataPrepare;
  console.log("=== GENERATING CLICK WEBHOOK SIGNATURE FOR PREPARE ===");
}

const signature = generateClickSignature(testData);

// Prepare webhook payload
const webhookPayload = {
  click_trans_id: testData.click_trans_id,
  service_id: testData.service_id,
  merchant_trans_id: testData.merchant_trans_id,
  merchant_prepare_id: testData.merchant_prepare_id,
  amount: testData.amount,
  action: testData.action,
  error: 0,
  error_note: "Success",
  sign_time: testData.sign_time,
  sign_string: signature,
};

console.log("\n=== WEBHOOK PAYLOAD ===");
console.log(JSON.stringify(webhookPayload, null, 2));

console.log("\n=== CURL COMMAND ===");
console.log(`curl -X POST "https://22f006751417.ngrok-free.app/api/payment/click/webhook" \\
  -H "Content-Type: application/json" \\
  -d '${JSON.stringify(webhookPayload)}'`);

console.log("\n=== USAGE ===");
console.log("node test-click-webhook.js prepare   # Test Prepare step");
console.log("node test-click-webhook.js complete  # Test Complete step");
