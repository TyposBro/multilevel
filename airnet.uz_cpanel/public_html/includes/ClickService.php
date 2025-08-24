<?php
require_once __DIR__ . '/../config/config.php';

class ClickService {
	public static function verifySignature(array $data): bool {
		$required = ['click_trans_id','service_id','merchant_trans_id','amount','action','sign_time','sign_string'];
		foreach ($required as $r) { if (!isset($data[$r])) return false; }

		$secretKey = click_get_secret_key();
		if (!$secretKey) return false; // secret not set

		$clickTransId = $data['click_trans_id'];
		$serviceId = $data['service_id'];
		$merchantTransId = $data['merchant_trans_id'];
		$merchantPrepareId = $data['merchant_prepare_id'] ?? '';
		$amount = number_format((float)$data['amount'], 2, '.', '');
		$action = $data['action'];
		$signTime = $data['sign_time'];
		$received = strtolower($data['sign_string']);

		$preparePart = $action == '1' ? $merchantPrepareId : '';
		$source = $clickTransId . $serviceId . $secretKey . $merchantTransId . $preparePart . $amount . $action . $signTime;
		$generated = md5($source);
		return hash_equals($generated, $received);
	}
}
?>
