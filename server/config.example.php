<?php
declare(strict_types=1);

return [
    'deployment_id' => 'replace-with-the-same-deployment-id-as-the-apk-and-web-client',
    'transport_ttl_seconds' => 604800,

    // Idéalement hors de la racine web si l'hébergement le permet.
    'storage_dir' => __DIR__ . '/data',

    'admin_username' => 'admin',
    // Générer avec password_hash(..., PASSWORD_ARGON2ID) ou PASSWORD_DEFAULT.
    'admin_password_hash' => '',
];
