# SecureBridge

SecureBridge est le composant Android natif du POC de messagerie privée.

> **État : v0.5.0-alpha — prototype de sécurité non audité.**
> Ne pas considérer cette version comme équivalente à Signal ni comme prête pour des usages sensibles en production.

## Principes actuels

- aucun Firebase / FCM ;
- notifications locales via Android WorkManager ;
- vérification de fond périodique, donc notifications potentiellement retardées par Android ;
- stockage privé Android chiffré avec une clé Android Keystore ;
- sauvegarde et transfert Android désactivés ;
- pairing physique téléphone-à-téléphone par NFC/HCE ;
- objet NFC personnel réservé au déverrouillage du coffre ;
- Argon2id pour la dérivation de secrets humains ;
- X25519 + Ed25519 ;
- Double Ratchet expérimental avec rotation des chaînes de messages et de slots ;
- anti-replay sur le pairing NFC et les messages ;
- messages éphémères : 1 h, 24 h ou 7 jours ;
- aucune icône launcher ; libellé Android neutre : **Stockage local**.

## Build automatique

Chaque modification de la branche `main` lance :

`.github/workflows/android-build.yml`

Le workflow utilise Java 17, Android SDK 35 et Gradle 8.9, compile `assembleDebug`, calcule le SHA-256 puis publie l'APK comme artifact GitHub Actions.

APK attendu :

`SecureBridge-v0.5.0-alpha-debug.apk`

## Compilation locale

Prérequis : JDK 17, Android SDK 35 et Gradle 8.9.

```bash
gradle --no-daemon assembleDebug
```

Sortie :

`app/build/outputs/apk/debug/app-debug.apk`

## Permissions Android

Le manifeste demande uniquement les permissions nécessaires au POC :

- `INTERNET`
- `NFC`
- `POST_NOTIFICATIONS`

Il ne demande pas les contacts, SMS, microphone, caméra ou localisation.

## Avertissement cryptographique

La v0.5 implémente un prototype de protocole ratchet afin de valider l'architecture. Même si elle utilise des primitives modernes, l'ensemble du protocole et son implémentation n'ont pas encore fait l'objet d'un audit cryptographique indépendant.

Avant toute v1.0, il reste notamment à réaliser des tests sur plusieurs appareils physiques, une revue complète du protocole, des tests de sécurité supplémentaires et idéalement un audit externe.
