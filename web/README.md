# Client web SecureBridge

Le client web est volontairement générique : aucune adresse de production n'est committée.

Avant un déploiement réel :

1. copier `config.example.js` vers `config.local.js` ;
2. renseigner le même `deploymentId` que l'APK et le backend ;
3. renseigner le préfixe de chemin autorisé ;
4. renseigner l'URL exacte de `server/api.php` ;
5. servir le site en HTTPS.

`config.local.js` est ignoré par Git.

Important : les valeurs visibles dans le navigateur ne sont pas des secrets cryptographiques. Le but est de ne pas publier ton déploiement réel dans le dépôt public ; la sécurité ne dépend pas du secret de l'URL.
