<?php
// Legacy combined endpoint (still supports both). Prefer using separate:
//   /api/click/prepare.php  (action=0)
//   /api/click/complete.php (action=1)
// This file retained for backward compatibility.

require_once __DIR__ . '/../../config/config.php';
require_once __DIR__ . '/../../includes/Logger.php';
require_once __DIR__ . '/../../includes/Database.php';
require_once __DIR__ . '/../../includes/ClickService.php';

header('Content-Type: application/json; charset=utf-8');

$logger = new Logger(LOG_DIR);
$rawBody = file_get_contents('php://input');
$logger->logRequest('click-webhook', $_SERVER, $rawBody);

// Parse JSON
$data = json_decode($rawBody, true);
if (!is_array($data)) {
	// Fallback: maybe form-encoded
	$form = [];
	parse_str($rawBody, $form);
	if ($form) {
		$data = $form;
		$logger->log('click-webhook', 'FALLBACK_FORM_DECODE');
	} else {
		$logger->log('click-webhook', 'BODY_PARSE_ERROR');
		echo json_encode(['error' => -8, 'error_note' => 'Error in request from click']);
		exit;
	}
}

// Extract parameters with defaults
$action = $data['action'] ?? null; // 0 prepare, 1 complete
$error = isset($data['error']) ? (int)$data['error'] : 0;
$merchantTransId = $data['merchant_trans_id'] ?? null;
$amount = $data['amount'] ?? null; // integer sums or string? We accept numeric
$merchantPrepareId = $data['merchant_prepare_id'] ?? null;
$serviceId = $data['service_id'] ?? null;
$clickTransId = $data['click_trans_id'] ?? null;
$signString = $data['sign_string'] ?? null;
$signTime = $data['sign_time'] ?? null;

$logger->log('click-webhook', 'PARAMS', compact('action','error','merchantTransId','amount','merchantPrepareId','serviceId','clickTransId','signString','signTime'));

// Signature verification
if (!ClickService::verifySignature($data)) {
	$logger->log('click-webhook', 'SIGNATURE_FAILED');
	echo json_encode(['error' => -1, 'error_note' => 'SIGN CHECK FAILED!']);
	exit;
}

// Validation/test probe handling
if (!$merchantTransId || $merchantTransId === 'test' || $merchantTransId === '0' || (int)$clickTransId === 0) {
	$resp = [
		'click_trans_id' => $clickTransId,
		'merchant_trans_id' => $merchantTransId ?: 'test',
		'merchant_prepare_id' => $merchantTransId ?: 'test',
		'error' => 0,
		'error_note' => 'Success'
	];
	$logger->log('click-webhook', 'VALIDATION_RESP', $resp);
	echo json_encode($resp);
	exit;
}

// External error from Click
if ($error < 0) {
	$logger->log('click-webhook', 'CLICK_REPORTED_ERROR', ['error' => $error]);
	$db = new Database();
	if ($db->isAvailable()) {
		$db->updateTransactionStatus($merchantTransId, 'FAILED', $clickTransId);
	}
	echo json_encode(['error' => -9, 'error_note' => 'Transaction cancelled']);
	exit;
}

$db = new Database();
$transaction = $db->isAvailable() ? $db->getTransaction($merchantTransId) : null;
if (!$transaction) {
	$logger->log('click-webhook', 'TX_NOT_FOUND', ['merchant_trans_id' => $merchantTransId]);
	echo json_encode(['error' => -5, 'error_note' => 'User does not exist']);
	exit;
}

// Plan lookup via service_id
$plans = click_get_plans();
$plan = null;
foreach ($plans as $k => $p) {
	if (($p['click_service_id'] ?? null) === (string)$serviceId) { $plan = $p; break; }
}
if (!$plan) {
	$logger->log('click-webhook', 'PLAN_NOT_FOUND', ['service_id' => $serviceId]);
	echo json_encode(['error' => -3, 'error_note' => 'Action not found']);
	exit;
}

// Amount validation (amount comes in sums; stored amount assumed tiyin)
$webhookAmountTiyin = (int)$amount * 100; // Click sends sums
$storedAmountTiyin = (int)($transaction['amount'] ?? 0);
if (abs($webhookAmountTiyin - $storedAmountTiyin) > 1) {
	$logger->log('click-webhook', 'AMOUNT_MISMATCH', ['webhook' => $webhookAmountTiyin, 'stored' => $storedAmountTiyin]);
	echo json_encode(['error' => -2, 'error_note' => 'Incorrect parameter amount']);
	exit;
}

$response = [
	'click_trans_id' => $clickTransId,
	'merchant_trans_id' => $merchantTransId,
	'merchant_prepare_id' => $merchantTransId,
	'error' => 0,
	'error_note' => 'Success'
];

if ($action === 0 || $action === '0') { // PREPARE
	if (($transaction['status'] ?? '') !== 'PENDING') {
		$logger->log('click-webhook', 'ALREADY_PROCESSED_PREPARE', ['status' => $transaction['status']]);
		echo json_encode(['error' => -4, 'error_note' => 'Already paid']);
		exit;
	}
	$logger->log('click-webhook', 'PREPARE_OK', $response);
	echo json_encode($response);
	exit;
}

if ($action === 1 || $action === '1') { // COMPLETE
	if ($merchantPrepareId && $merchantPrepareId !== $transaction['id']) {
		$logger->log('click-webhook', 'PREPARE_ID_MISMATCH', ['merchant_prepare_id' => $merchantPrepareId, 'tx_id' => $transaction['id']]);
		echo json_encode(['error' => -6, 'error_note' => 'Transaction does not exist']);
		exit;
	}
	if (($transaction['status'] ?? '') === 'COMPLETED') {
		$logger->log('click-webhook', 'ALREADY_COMPLETED');
		echo json_encode(['error' => -4, 'error_note' => 'Already paid']);
		exit;
	}

	// Update subscription
	$user = $db->getUserById($transaction['userId']);
	if ($user) {
		$now = new DateTimeImmutable();
		$currentExpiry = isset($user['subscription_expiresAt']) && $user['subscription_expiresAt'] ? new DateTimeImmutable($user['subscription_expiresAt']) : null;
		$start = ($currentExpiry && $currentExpiry > $now) ? $currentExpiry : $now;
		$newExpiry = $start->modify('+' . (int)$plan['duration_days'] . ' days');
		$db->updateUserSubscription($user['id'], $plan['tier'], $newExpiry->format('Y-m-d H:i:s'));
		$logger->log('click-webhook', 'SUBSCRIPTION_UPDATED', ['user_id' => $user['id'], 'tier' => $plan['tier'], 'expires_at' => $newExpiry->format(DateTime::ATOM)]);
	} else {
		$logger->log('click-webhook', 'USER_NOT_FOUND_FOR_COMPLETE', ['userId' => $transaction['userId']]);
	}

	$db->updateTransactionStatus($transaction['id'], 'COMPLETED', $clickTransId);
	$response['merchant_confirm_id'] = $transaction['id'];
	$logger->log('click-webhook', 'COMPLETE_OK', $response);
	echo json_encode($response);
	exit;
}

$logger->log('click-webhook', 'UNKNOWN_ACTION', ['action' => $action]);
echo json_encode(['error' => -3, 'error_note' => 'Action not found']);
?>
