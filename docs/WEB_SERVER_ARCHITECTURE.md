# Architecture Web + serveur

Le navigateur n'est pas le coffre. Le client web n'obtient l'accès au stockage natif qu'à travers SecureBridge Android.

Le backend ne connaît ni comptes, ni noms, ni contacts, ni groupes, ni contenu en clair. Il manipule uniquement des slots opaques et des blobs chiffrés.

## Configuration privée

Le dépôt public contient seulement les fichiers `*.example.*`.

Le déploiement réel utilise :

- `web/config.local.js` ;
- `server/config.local.php` ;
- les quatre paramètres privés/internes du build Android.

Le même `deploymentId` relie les trois composants et évite les mélanges accidentels entre déploiements. Il ne s'agit pas d'un secret cryptographique.

## Notifications

Aucun FCM. Lorsque l'interface est ouverte, le client fait du polling rapide. Lorsque l'interface est fermée, Android WorkManager vérifie périodiquement un lot fixe de slots opaques et crée localement la notification générique.
