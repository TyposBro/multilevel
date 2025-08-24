<?php
// Click COMPLETE webhook (action=1)
require_once __DIR__ . '/../../config/config.php';
require_once __DIR__ . '/../../includes/Logger.php';
require_once __DIR__ . '/../../includes/Database.php';
require_once __DIR__ . '/../../includes/ClickService.php';

header('Content-Type: application/json; charset=utf-8');
$logger = new Logger(LOG_DIR);
$raw = file_get_contents('php://input');
$logger->logRequest('click-complete', $_SERVER, $raw);

$data = json_decode($raw, true);
if (!is_array($data)) { parse_str($raw, $tmp); if ($tmp) { $data = $tmp; $logger->log('click-complete','FALLBACK_FORM'); } }
if (!is_array($data)) { echo json_encode(['error'=>-8,'error_note'=>'Error in request from click']); exit; }

$action = (string)($data['action'] ?? '1');
if ($action !== '1') { echo json_encode(['error'=>-3,'error_note'=>'Action not found']); exit; }

if (!ClickService::verifySignature($data)) { echo json_encode(['error'=>-1,'error_note'=>'SIGN CHECK FAILED!']); exit; }

$error = (int)($data['error'] ?? 0);
if ($error < 0) { echo json_encode(['error'=>-9,'error_note'=>'Transaction cancelled']); exit; }

$merchantTransId = $data['merchant_trans_id'] ?? null;
$merchantPrepareId = $data['merchant_prepare_id'] ?? null;
$serviceId = $data['service_id'] ?? null;
$amount = $data['amount'] ?? null;
if (!$merchantTransId || !$merchantPrepareId) { echo json_encode(['error'=>-6,'error_note'=>'Transaction does not exist']); exit; }

$db = new Database();
$tx = $db->getTransaction($merchantTransId);
if (!$tx) { echo json_encode(['error'=>-6,'error_note'=>'Transaction does not exist']); exit; }
if ($merchantPrepareId !== $tx['id']) { echo json_encode(['error'=>-6,'error_note'=>'Transaction does not exist']); exit; }

// Plan lookup
$plans = click_get_plans(); $plan=null; foreach ($plans as $p) { if (($p['click_service_id'] ?? '') === (string)$serviceId) { $plan=$p; break; } }
if (!$plan) { echo json_encode(['error'=>-3,'error_note'=>'Action not found']); exit; }

// Amount validation
$webhookAmountTiyin = (int)$amount * 100; $stored = (int)($tx['amount'] ?? 0);
if (abs($webhookAmountTiyin - $stored) > 1) { echo json_encode(['error'=>-2,'error_note'=>'Incorrect parameter amount']); exit; }

if (($tx['status'] ?? '') === 'COMPLETED') { echo json_encode(['error'=>-4,'error_note'=>'Already paid']); exit; }

// Subscription update
$user = $db->getUserById($tx['userId']);
if ($user) {
  $now = new DateTimeImmutable();
  $currentExpiry = isset($user['subscription_expiresAt']) && $user['subscription_expiresAt'] ? new DateTimeImmutable($user['subscription_expiresAt']) : null;
  $start = ($currentExpiry && $currentExpiry > $now) ? $currentExpiry : $now;
  $newExpiry = $start->modify('+' . (int)$plan['duration_days'] . ' days');
  $db->updateUserSubscription($user['id'], $plan['tier'], $newExpiry->format('Y-m-d H:i:s'));
  $logger->log('click-complete','SUB_UPDATED',['user'=>$user['id'],'tier'=>$plan['tier'],'expires'=>$newExpiry->format(DateTime::ATOM)]);
}

$db->updateTransactionStatus($tx['id'], 'COMPLETED', $data['click_trans_id'] ?? null);

$resp = [
  'click_trans_id'=>$data['click_trans_id'] ?? null,
  'merchant_trans_id'=>$merchantTransId,
  'merchant_prepare_id'=>$merchantTransId,
  'merchant_confirm_id'=>$merchantTransId,
  'error'=>0,
  'error_note'=>'Success'
];
$logger->log('click-complete','COMPLETE_OK',$resp);
echo json_encode($resp);
?>
