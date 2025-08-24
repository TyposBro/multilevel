<?php
/**
 * Configuration loader for Click integration on cPanel.
 * NOTE: Do NOT hard‑code production secrets here.
 * Provide secrets in a private file outside web root, e.g. /home/USERNAME/private/click_secrets.php
 */

$externalSecrets = __DIR__ . '/../../../private/click_secrets.php';
if (is_readable($externalSecrets)) {
	require_once $externalSecrets; // defines CLICK_* and DB_* constants
}

if (!defined('CLICK_ENV')) {
	define('CLICK_ENV', getenv('CLICK_ENV') ?: 'test');
}

// Safe placeholders if external file missing
foreach ([
	'CLICK_MERCHANT_ID_LIVE','CLICK_MERCHANT_USER_ID_LIVE','CLICK_SECRET_KEY_LIVE',
	'CLICK_MERCHANT_ID_TEST','CLICK_MERCHANT_USER_ID_TEST','CLICK_SECRET_KEY_TEST',
	'DB_DSN','DB_USER','DB_PASS'
] as $c) {
	if (!defined($c)) define($c, '');
}

if (!defined('LOG_DIR')) {
	$candidate = __DIR__ . '/../logs';
	if (!is_dir($candidate)) @mkdir($candidate, 0750, true);
	define('LOG_DIR', realpath($candidate) ?: $candidate);
}

if (!defined('CLICK_PLANS')) {
	define('CLICK_PLANS', json_encode([
		'premium_1_month' => [
			'click_service_id' => '80012', // example
			'price_tiyin' => 1500000,
			'duration_days' => 30,
			'tier' => 'premium'
		],
	]));
}

function click_get_plans(): array { return json_decode(CLICK_PLANS, true) ?: []; }
function click_current_env_is_production(): bool { return strtolower(CLICK_ENV) === 'production'; }
function click_get_secret_key(): string { return click_current_env_is_production() ? CLICK_SECRET_KEY_LIVE : CLICK_SECRET_KEY_TEST; }
function click_get_merchant_id(): string { return click_current_env_is_production() ? CLICK_MERCHANT_ID_LIVE : CLICK_MERCHANT_ID_TEST; }
function click_get_merchant_user_id(): string { return click_current_env_is_production() ? CLICK_MERCHANT_USER_ID_LIVE : CLICK_MERCHANT_USER_ID_TEST; }

?>
