<?php
declare(strict_types=1);

return [
    'deployment_id' => 'replace-with-the-same-deployment-id',
    'transport_ttl_seconds' => 604800,
    'admin_password_hash' => getenv('SECUREBRIDGE_ADMIN_PASSWORD_HASH') ?: '',
];
