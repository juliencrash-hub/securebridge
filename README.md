# SecureBridge

SecureBridge est un POC Android + Web + PHP de messagerie privée.

> **État : v0.5.1-alpha — prototype de sécurité non audité.**
> Cette version sert à valider l'architecture et les tests. Elle ne doit pas être présentée comme équivalente à Signal ni comme prête pour des usages sensibles en production.

## Branches

- `main` : version stable du dépôt, compilée par GitHub Actions.
- `development` : branche de travail. Les changements y sont testés avant d'être proposés à `main`.

La branche de développement exécute automatiquement les tests JavaScript, PHP, protocole, backend et Android sans modifier `main`.

## Arborescence

```text
app/                         application Android SecureBridge
web/                         client web
  assets/app.js              logique du coffre, contacts, ratchets, groupes
  assets/style.css
  config.example.js          modèle public de configuration
server/                      backend PHP mutualisé
  api.php                    PUT / PEEK_MANY / TAKE
  admin.php                  statistiques agrégées
  config.example.php         modèle public serveur
tests/                       tests statiques, protocole et backend
docs/                        architecture et déploiement
.github/workflows/           builds et validations
```

## Code public / configuration privée

Aucune vraie adresse de production ni aucun secret ne doit être committé.

Android reçoit au build :

- `SECUREBRIDGE_ALLOWED_ORIGIN`
- `SECUREBRIDGE_ALLOWED_PATH`
- `SECUREBRIDGE_API_URL`
- `SECUREBRIDGE_DEPLOYMENT_ID`

Le site utilise `web/config.local.js` et le serveur `server/config.local.php`. Ces fichiers sont ignorés par Git.

Sans configuration privée, l'APK debug reste volontairement **générique et verrouillé** : il compile, mais ne peut s'associer à aucun vrai site.

## Client web

Le client web ne constitue pas le coffre à lui seul. Le stockage sensible reste dans SecureBridge Android, protégé par le stockage privé de l'application et Android Keystore.

Le client gère notamment :

- création / ouverture du coffre ;
- objet NFC personnel pour le déverrouillage ;
- pairing physique téléphone-à-téléphone par NFC/HCE ;
- contacts locaux ;
- messages texte + emoji ;
- messages standard, 1 h, 24 h et 7 jours ;
- Double Ratchet expérimental et ratchet de slots ;
- groupes v0.5 en fan-out sur les relations 1-à-1 existantes.

## Backend PHP

Compatible hébergement mutualisé : pas de Node, pas de daemon permanent et pas de SQL obligatoire.

Le serveur manipule uniquement des slots opaques et des blobs chiffrés :

- `put`
- `peek_many`
- `take`
- TTL technique
- statistiques globales agrégées

Il ne possède pas les noms des contacts, le contenu en clair, les clés du coffre ou un annuaire d'utilisateurs.

## Notifications

Aucun Firebase / FCM / ntfy.

Lorsque le coffre est ouvert, le site effectue du polling rapide. Lorsqu'il est fermé, Android WorkManager vérifie périodiquement des lots fixes de slots opaques et crée localement une notification générique.

## Android

- pas d'icône launcher ;
- libellé neutre : **Stockage local** ;
- sauvegarde/transfert désactivés ;
- cleartext HTTP interdit ;
- WebView liée au déploiement compilé ;
- Android Keystore pour le stockage natif ;
- permissions limitées à `INTERNET`, `NFC` et `POST_NOTIFICATIONS`.

## Tests

La branche `development` vérifie automatiquement :

```text
JavaScript syntax
PHP lint
garde-fous statiques Android/Web/PHP
propriétés cryptographiques de référence
PUT → PEEK → TAKE → suppression one-shot
tests Android debug
compilation APK Android
```

## Avertissement cryptographique

Argon2id, X25519, Ed25519 et AES-GCM sont des primitives reconnues, mais le protocole ratchet et son intégration restent expérimentaux dans ce POC.

Avant une v1.0 : tests sur plusieurs téléphones physiques, revue protocolaire complète, davantage de tests adversariaux et idéalement audit indépendant.
