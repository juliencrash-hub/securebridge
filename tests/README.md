# Tests SecureBridge

- `security_static.py` vérifie les garde-fous Web/PHP et les marqueurs du protocole.
- `protocol_properties.py` vérifie des propriétés cryptographiques de référence.
- `backend_smoke.py` vérifie PUT → PEEK → TAKE → suppression one-shot.

Exemple local :

```bash
cp server/config.example.php server/config.local.php
# mettre deployment_id à test-deployment-00000001
php -S 127.0.0.1:8799 -t server
DEPLOYMENT_ID=test-deployment-00000001 python tests/backend_smoke.py
```
