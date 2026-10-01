<?php
declare(strict_types=1);

ini_set('display_errors', '0');
ini_set('log_errors', '0');
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, max-age=0');
header('Pragma: no-cache');
header('Referrer-Policy: no-referrer');
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header("Permissions-Policy: geolocation=(), microphone=(), camera=(), payment=(), usb=()");

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/lib/stats.php';

const MAX_BLOB = 32000;
const MAX_TTL = 604800;
const MAX_PEEK_SLOTS = 128;
const MAX_REQUEST_BYTES = 120000;

$cfg = securebridge_config();
$deploymentId = (string)($cfg['deployment_id'] ?? '');
if ($deploymentId === '') {
    http_response_code(503);
    echo json_encode(['ok' => false, 'error' => 'server_not_configured']);
    exit;
}

$base = rtrim((string)($cfg['storage_dir'] ?? (__DIR__ . '/data')), '/\\');
$qdir = $base . '/queue';
@mkdir($qdir, 0700, true);
stats_set_storage_dir($base);

function out(array $data, int $status = 200): never {
    http_response_code($status);
    echo json_encode($data, JSON_UNESCAPED_SLASHES);
    exit;
}
function body(): array {
    $raw = file_get_contents('php://input');
    if ($raw === false || strlen($raw) > MAX_REQUEST_BYTES) out(['ok'=>false,'error'=>'request'], 400);
    $json = json_decode($raw, true);
    if (!is_array($json)) out(['ok'=>false,'error'=>'json'], 400);
    return $json;
}
function valid_slot(string $slot): bool { return (bool)preg_match('/^[a-f0-9]{64}$/', $slot); }
function qpath(string $slot): string {
    global $qdir;
    $sub = $qdir . '/' . substr($slot, 0, 2);
    @mkdir($sub, 0700, true);
    return $sub . '/' . $slot . '.msg';
}
function live_slot(string $slot): bool {
    $path = qpath($slot);
    if (!is_file($path)) return false;
    $raw = @file_get_contents($path);
    $obj = $raw ? json_decode($raw, true) : null;
    if (!is_array($obj) || (int)($obj['expires'] ?? 0) < time()) {
        if (@unlink($path)) stats_inc('messages_expired');
        return false;
    }
    return true;
}
function cleanup_queue_sample(): void {
    global $qdir;
    $now = time(); $seen = 0; $expired = 0;
    foreach (glob($qdir . '/*/*.msg') ?: [] as $file) {
        if (++$seen > 100) break;
        $raw = @file_get_contents($file);
        $obj = $raw ? json_decode($raw, true) : null;
        if (!is_array($obj) || (int)($obj['expires'] ?? 0) < $now) {
            if (@unlink($file)) $expired++;
        }
    }
    if ($expired) stats_inc('messages_expired', $expired);
}

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') out(['ok'=>false,'error'=>'method'], 405);
$j = body();
if (!hash_equals($deploymentId, (string)($j['deploymentId'] ?? ''))) out(['ok'=>false,'error'=>'deployment'], 403);
$action = (string)($j['action'] ?? '');
cleanup_queue_sample();

if ($action === 'put') {
    $slot = strtolower((string)($j['slot'] ?? ''));
    $blob = (string)($j['blob'] ?? '');
    if (!valid_slot($slot) || $blob === '' || strlen($blob) > MAX_BLOB) out(['ok'=>false,'error'=>'invalid'], 400);
    $serverTtl = max(60, min(MAX_TTL, (int)($cfg['transport_ttl_seconds'] ?? MAX_TTL)));
    $ttl = max(60, min($serverTtl, (int)($j['ttl'] ?? $serverTtl)));
    $path = qpath($slot);
    if (is_file($path)) out(['ok'=>false,'error'=>'occupied'], 409);
    $payload = json_encode(['expires'=>time()+$ttl,'blob'=>$blob], JSON_UNESCAPED_SLASHES);
    $fp = @fopen($path, 'x');
    if (!$fp) out(['ok'=>false,'error'=>'write'], 500);
    flock($fp, LOCK_EX); fwrite($fp, $payload); fflush($fp); flock($fp, LOCK_UN); fclose($fp); @chmod($path, 0600);
    stats_inc('messages_put');
    out(['ok'=>true]);
}

if ($action === 'take') {
    $slot = strtolower((string)($j['slot'] ?? ''));
    if (!valid_slot($slot)) out(['ok'=>false,'error'=>'invalid'], 400);
    $path = qpath($slot);
    if (!is_file($path)) out(['ok'=>true,'blob'=>null]);
    $fp = @fopen($path, 'r+');
    if (!$fp) out(['ok'=>true,'blob'=>null]);
    if (!flock($fp, LOCK_EX)) { fclose($fp); out(['ok'=>true,'blob'=>null]); }
    $raw = stream_get_contents($fp);
    $obj = $raw ? json_decode($raw, true) : null;
    @unlink($path); flock($fp, LOCK_UN); fclose($fp);
    if (!is_array($obj) || (int)($obj['expires'] ?? 0) < time()) {
        stats_inc('messages_expired'); out(['ok'=>true,'blob'=>null]);
    }
    stats_inc('messages_taken');
    out(['ok'=>true,'blob'=>(string)$obj['blob']]);
}

if ($action === 'peek_many') {
    $slots = $j['slots'] ?? null;
    if (!is_array($slots) || count($slots) < 1 || count($slots) > MAX_PEEK_SLOTS) out(['ok'=>false,'error'=>'invalid'], 400);
    $index = -1;
    foreach ($slots as $i => $slot) {
        $slot = strtolower((string)$slot);
        if (!valid_slot($slot)) out(['ok'=>false,'error'=>'invalid'], 400);
        if (live_slot($slot)) { $index = (int)$i; break; }
    }
    stats_inc('slot_peeks');
    out(['ok'=>true,'index'=>$index]);
}

if ($action === 'stat_event') {
    $event = (string)($j['event'] ?? '');
    $version = (string)($j['version'] ?? '');
    if ($event === 'install') stats_inc('installs', 1, $version);
    elseif ($event === 'vault_created') stats_inc('vaults_created');
    else out(['ok'=>false,'error'=>'event'], 400);
    out(['ok'=>true]);
}

out(['ok'=>false,'error'=>'action'], 400);
