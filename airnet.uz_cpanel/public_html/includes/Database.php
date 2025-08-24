<?php
require_once __DIR__ . '/../config/config.php';

class Database {
	private ?PDO $pdo = null;

	public function __construct() {
		if (DB_DSN) {
			$opts = [
				PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
				PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
			];
			$this->pdo = new PDO(DB_DSN, DB_USER, DB_PASS, $opts);
		}
	}

	public function isAvailable(): bool { return $this->pdo !== null; }

	public function getTransaction(string $id): ?array {
		if (!$this->pdo) return null;
		$stmt = $this->pdo->prepare('SELECT * FROM payment_transactions WHERE id = :id');
		$stmt->execute([':id' => $id]);
		$row = $stmt->fetch();
		return $row ?: null;
	}

	public function updateTransactionStatus(string $id, string $status, ?string $providerTxId = null): void {
		if (!$this->pdo) return;
		$stmt = $this->pdo->prepare('UPDATE payment_transactions SET status = :s, providerTransactionId = COALESCE(:ptx, providerTransactionId), updatedAt = NOW() WHERE id = :id');
		$stmt->execute([':s' => $status, ':ptx' => $providerTxId, ':id' => $id]);
	}

	public function getUserById(string $id): ?array {
		if (!$this->pdo) return null;
		$stmt = $this->pdo->prepare('SELECT * FROM users WHERE id = :id');
		$stmt->execute([':id' => $id]);
		$row = $stmt->fetch();
		return $row ?: null;
	}

	public function updateUserSubscription(string $id, string $tier, string $expiresAt): void {
		if (!$this->pdo) return;
		$stmt = $this->pdo->prepare('UPDATE users SET subscription_tier = :t, subscription_expiresAt = :e WHERE id = :id');
		$stmt->execute([':t' => $tier, ':e' => $expiresAt, ':id' => $id]);
	}
}
?>
