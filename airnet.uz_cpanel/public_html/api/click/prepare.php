<?php
// Click PREPARE webhook (action=0)
require_once __DIR__ . '/../../config/config.php';
require_once __DIR__ . '/../../includes/Logger.php';
require_once __DIR__ . '/../../includes/Database.php';
require_once __DIR__ . '/../../includes/ClickService.php';

header('Content-Type: application/json; charset=utf-8');
$logger = new Logger(LOG_DIR);
$raw = file_get_contents('php://input');
$logger->logRequest('click-prepare', $_SERVER, $raw);

$data = json_decode($raw, true);
if (!is_array($data)) { parse_str($raw, $tmp); if ($tmp) { $data = $tmp; $logger->log('click-prepare','FALLBACK_FORM'); } }
if (!is_array($data)) { echo json_encode(['error'=>-8,'error_note'=>'Error in request from click']); exit; }

$action = (string)($data['action'] ?? '0');
if ($action !== '0') { echo json_encode(['error'=>-3,'error_note'=>'Action not found']); exit; }

// Validation / test probe
if (($data['merchant_trans_id'] ?? '') === '' || $data['merchant_trans_id'] === 'test' || $data['merchant_trans_id'] === '0' || (int)($data['click_trans_id'] ?? 0) === 0) {
    if (!ClickService::verifySignature($data)) { echo json_encode(['error'=>-1,'error_note'=>'SIGN CHECK FAILED!']); exit; }
    $resp = [
      'click_trans_id'=>$data['click_trans_id'] ?? 0,
      'merchant_trans_id'=>$data['merchant_trans_id'] ?? 'test',
      'merchant_prepare_id'=>$data['merchant_trans_id'] ?? 'test',
      'error'=>0,
      'error_note'=>'Success'
    ];
    $logger->log('click-prepare','VALIDATION_OK',$resp);
    echo json_encode($resp); exit;
}

if (!ClickService::verifySignature($data)) { echo json_encode(['error'=>-1,'error_note'=>'SIGN CHECK FAILED!']); exit; }

$error = (int)($data['error'] ?? 0);
if ($error < 0) { echo json_encode(['error'=>-9,'error_note'=>'Transaction cancelled']); exit; }

$merchantTransId = $data['merchant_trans_id'];
$serviceId = $data['service_id'] ?? null;
$amount = $data['amount'] ?? null; // sums
$db = new Database();
$tx = $db->getTransaction($merchantTransId);
if (!$tx) { echo json_encode(['error'=>-6,'error_note'=>'Transaction does not exist']); exit; }

// Plan lookup
$plans = click_get_plans(); $plan=null; foreach ($plans as $p) { if (($p['click_service_id'] ?? '') === (string)$serviceId) { $plan=$p; break; } }
if (!$plan) { echo json_encode(['error'=>-3,'error_note'=>'Action not found']); exit; }

// Amount validation
$webhookAmountTiyin = (int)$amount * 100; $stored = (int)($tx['amount'] ?? 0);
if (abs($webhookAmountTiyin - $stored) > 1) { echo json_encode(['error'=>-2,'error_note'=>'Incorrect parameter amount']); exit; }

if (($tx['status'] ?? '') !== 'PENDING') { echo json_encode(['error'=>-4,'error_note'=>'Already paid']); exit; }

$resp = [
  'click_trans_id'=>$data['click_trans_id'],
  'merchant_trans_id'=>$merchantTransId,
  'merchant_prepare_id'=>$merchantTransId,
  'error'=>0,
  'error_note'=>'Success'
];
$logger->log('click-prepare','PREPARE_OK',$resp);
echo json_encode($resp);
?>
