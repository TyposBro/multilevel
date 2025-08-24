<?php
class Logger {
	private string $dir;
	public function __construct(string $dir) {
		$this->dir = $dir;
		if (!is_dir($dir)) @mkdir($dir, 0750, true);
	}

	public function log(string $channel, string $message, array $context = []): void {
		$date = date('Y-m-d');
		$file = $this->dir . "/{$channel}-{$date}.log";
		$time = date('H:i:s');
		$line = "[$time] $message";
		if ($context) {
			$json = json_encode($context, JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
			$line .= " | $json";
		}
		$line .= "\n";
		file_put_contents($file, $line, FILE_APPEND | LOCK_EX);
	}

	public function logRequest(string $channel, array $server, string $rawBody): void {
		$this->log($channel, 'REQUEST', [
			'ip' => $server['REMOTE_ADDR'] ?? null,
			'method' => $server['REQUEST_METHOD'] ?? null,
			'uri' => $server['REQUEST_URI'] ?? null,
			'headers' => $this->getAllHeadersSafe(),
			'body_raw' => $rawBody,
		]);
	}

	private function getAllHeadersSafe(): array {
		if (function_exists('getallheaders')) {
			$h = getallheaders();
			if (is_array($h)) return $h;
		}
		$headers = [];
		foreach ($_SERVER as $key => $value) {
			if (str_starts_with($key, 'HTTP_')) {
				$name = str_replace(' ', '-', ucwords(strtolower(str_replace('_', ' ', substr($key, 5)))));
				$headers[$name] = $value;
			}
		}
		return $headers;
	}
}
?>
