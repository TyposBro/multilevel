<?php
// =================================================================
//  complete.php (Robust Version)
//  Handles both JSON and POST data, and logs all errors/exceptions.
// =================================================================

// -----------------------------------------------------------------
//  1. SETUP: GLOBAL ERROR & EXCEPTION HANDLING
//  This section must be at the VERY TOP to catch all possible errors.
// -----------------------------------------------------------------

// Set a global exception handler to catch any uncaught exceptions.
set_exception_handler(function ($exception) {
    // Log the detailed exception information.
    log_message("FATAL EXCEPTION: " . $exception->getMessage() . " in " . $exception->getFile() . " on line " . $exception->getLine());

    // Send a generic, safe error response to Click.
    if (!headers_sent()) {
        header('Content-Type: application/json');
        echo json_encode(['error' => -8, 'error_note' => 'An internal server error occurred.']);
    }
    exit;
});

// Set a shutdown function to catch fatal errors.
register_shutdown_function(function () {
    $error = error_get_last();
    if ($error !== null && in_array($error['type'], [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR])) {
        log_message("FATAL ERROR: " . $error['message'] . " in " . $error['file'] . " on line " . $error['line']);
        if (!headers_sent()) {
            header('Content-Type: application/json');
            echo json_encode(['error' => -8, 'error_note' => 'A fatal internal server error occurred.']);
        }
    }
});

// -----------------------------------------------------------------
//  2. MAIN EXECUTION BLOCK
// -----------------------------------------------------------------
try {
    // Include the secure configuration file.
    require_once '/home/typos492/private/config.php';
    
    header('Content-Type: application/json');

    // --- 2.1. PARSE INCOMING DATA (Handles JSON and POST) ---
    $data = null;
    $contentType = isset($_SERVER['CONTENT_TYPE']) ? trim($_SERVER['CONTENT_TYPE']) : '';

    if (stripos($contentType, 'application/json') !== false) {
        $request_body = file_get_contents('php://input');
        $data = json_decode($request_body, true);
        log_message("COMPLETE: Incoming request type: JSON. Body: " . $request_body);
    } else {
        $data = $_POST;
        log_message("COMPLETE: Incoming request type: POST. Data: " . json_encode($data));
    }
    
    // Validate that data was parsed successfully.
    if (empty($data) || !is_array($data) || !isset($data['click_trans_id'])) {
        throw new Exception("No valid POST or JSON data received or key fields missing.");
    }
    
    // --- 2.2. VERIFY THE CLICK SIGNATURE (Correct formula for 'complete') ---
    $sign_string_source = "{$data['click_trans_id']}" .
                          "{$data['service_id']}" .
                          CLICK_SECRET_KEY .
                          "{$data['merchant_trans_id']}" .
                          "{$data['merchant_prepare_id']}" . // Included for complete
                          "{$data['amount']}" .
                          "{$data['action']}" .
                          "{$data['sign_time']}";

    $generated_signature = md5($sign_string_source);

    if ($generated_signature !== $data['sign_string']) {
        log_message("COMPLETE ERROR: Signature check failed. Generated: $generated_signature, Received: {$data['sign_string']}");
        echo json_encode(['error' => -1, 'error_note' => 'SIGN CHECK FAILED!']);
        exit;
    }
    log_message("COMPLETE: Signature verified successfully.");
    
    // --- 2.3. FORWARD THE REQUEST TO THE CLOUDFLARE WORKER ---
    $json_to_forward = json_encode($data);
    
    $ch = curl_init(WORKER_BASE_URL . '/api/payment/click/complete');
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, $json_to_forward);
    curl_setopt($ch, CURLOPT_HTTPHEADER, [
        'Content-Type: application/json',
        'X-Proxy-Auth: ' . WORKER_PROXY_SECRET
    ]);

    $worker_response = curl_exec($ch);
    $http_code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    
    if (curl_errno($ch)) {
        $curl_error = curl_error($ch);
        curl_close($ch);
        throw new Exception("cURL Error while contacting worker: " . $curl_error);
    }
    curl_close($ch);

    log_message("COMPLETE: Worker response (HTTP $http_code): " . $worker_response);
    
    // --- 2.4. RELAY THE WORKER'S RESPONSE BACK TO CLICK ---
    echo $worker_response;

} catch (Throwable $e) {
    // Re-throw the exception to be caught by our global handler for logging.
    throw $e;
}