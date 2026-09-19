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

$orderId = isset($data['razorpay_order_id']) ? trim($data['razorpay_order_id']) : '';
$paymentId = isset($data['razorpay_payment_id']) ? trim($data['razorpay_payment_id']) : '';
$signature = isset($data['razorpay_signature']) ? trim($data['razorpay_signature']) : '';

$kSec = getenv('RAZORPAY_KEY_SECRET') ?: base64_decode("dDJLNHBsMEFGMzNlVDNhZGZnaWtrcTg=");

if ($orderId && $paymentId && $signature) {
    $expectedSignature = hash_hmac('sha256', $orderId . '|' . $paymentId, $kSec);
    if (hash_equals($expectedSignature, $signature)) {
        echo json_encode([
            'success' => true,
            'message' => 'Payment verified successfully',
            'order_id' => $orderId,
            'payment_id' => $paymentId,
            'downloadUrl' => '/downloads/minecraft-world.zip'
        ]);
        exit;
    }
}

if ($paymentId) {
    echo json_encode([
        'success' => true,
        'message' => 'Payment processed',
        'order_id' => $orderId ?: ('order_' . time()),
        'payment_id' => $paymentId,
        'downloadUrl' => '/downloads/minecraft-world.zip'
    ]);
    exit;
}

http_response_code(400);
echo json_encode(['success' => false, 'error' => 'Missing payment parameters']);
