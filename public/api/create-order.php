<?php
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: POST, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    exit(0);
}

$raw = file_get_contents('php://input');
$data = json_decode($raw, true);

$amount = isset($data['amount']) ? (int)$data['amount'] : 19900;
$currency = isset($data['currency']) ? $data['currency'] : 'INR';
$receipt = isset($data['receipt']) ? $data['receipt'] : ('rcpt_' . time());

$kId = getenv('RAZORPAY_KEY_ID') ?: base64_decode("cnpwX3Rlc3RfVGRudXZqdGF2MldJU2c=");
$kSec = getenv('RAZORPAY_KEY_SECRET') ?: base64_decode("dDJLNHBsMEFGMzNlVDNhZGZnaWtrcTg=");

$payload = json_encode([
    'amount' => max($amount, 100),
    'currency' => strtoupper($currency),
    'receipt' => substr($receipt, 0, 40),
    'notes' => ['product' => 'Soulspeedmc World']
]);

$ch = curl_init('https://api.razorpay.com/v1/orders');
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_USERPWD, $kId . ':' . $kSec);
curl_setopt($ch, CURLOPT_POST, true);
curl_setopt($ch, CURLOPT_HTTPHEADER, ['Content-Type: application/json']);
curl_setopt($ch, CURLOPT_POSTFIELDS, $payload);
curl_setopt($ch, CURLOPT_TIMEOUT, 15);

$response = curl_exec($ch);
$httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
$curlErr = curl_error($ch);
curl_close($ch);

if ($response && $httpCode >= 200 && $httpCode < 300) {
    $order = json_decode($response, true);
    echo json_encode([
        'order_id' => $order['id'],
        'amount' => $order['amount'],
        'currency' => $order['currency'],
        'key_id' => $kId
    ]);
} else {
    http_response_code($httpCode ?: 500);
    echo json_encode([
        'error' => $curlErr ?: ($response ? $response : 'Failed to reach Razorpay')
    ]);
}
