# Contrat de sécurité Web ↔ SecureBridge

Objectif : aucune compilation de production ne doit accepter arbitrairement le premier domaine HTTPS qui demande l'association.

La configuration de déploiement contient une origine HTTPS exacte, un chemin autorisé, une URL d'API et un identifiant de déploiement.

La validation Android doit comparer séparément :

- schéma HTTPS ;
- hôte ;
- port ;
- chemin autorisé.

Le futur client web devra utiliser la même configuration logique et présenter le même identifiant de déploiement.

L'URL utilisée par WorkManager doit correspondre à l'API configurée dans l'APK ; le JavaScript ne doit pas pouvoir choisir librement un autre serveur.

Le WebView doit continuer à refuser le cleartext, les accès fichier et le contenu mixte.

L'identifiant de déploiement sert à détecter les erreurs de configuration ; ce n'est pas un secret cryptographique.
