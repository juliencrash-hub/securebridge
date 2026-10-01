<?php
declare(strict_types=1);

function securebridge_config(): array {
    static $cfg = null;
    if (is_array($cfg)) return $cfg;

    $local = __DIR__ . '/config.local.php';
    if (is_file($local)) {
        $loaded = require $local;
        if (is_array($loaded)) {
            $cfg = $loaded;
            return $cfg;
        }
    }

    $cfg = [
        'deployment_id' => (string)(getenv('SECUREBRIDGE_DEPLOYMENT_ID') ?: ''),
        'transport_ttl_seconds' => (int)(getenv('SECUREBRIDGE_TRANSPORT_TTL_SECONDS') ?: 604800),
        'storage_dir' => (string)(getenv('SECUREBRIDGE_STORAGE_DIR') ?: (__DIR__ . '/data')),
        'admin_username' => (string)(getenv('SECUREBRIDGE_ADMIN_USERNAME') ?: 'admin'),
        'admin_password_hash' => (string)(getenv('SECUREBRIDGE_ADMIN_PASSWORD_HASH') ?: ''),
    ];
    return $cfg;
}
