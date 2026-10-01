# Backend PHP SecureBridge

Backend compatible avec un hébergement mutualisé PHP : pas de Node, pas de base SQL obligatoire, pas de daemon permanent.

Actions réseau :

- `put` : dépose un blob chiffré dans un slot opaque ;
- `peek_many` : teste un lot de slots sans les consommer ;
- `take` : récupère puis supprime le blob (one-shot) ;
- `stat_event` : incrémente uniquement des compteurs globaux.

Avant déploiement :

1. copier `config.example.php` vers `config.local.php` ;
2. mettre le même `deployment_id` que l'APK et le site ;
3. choisir un mot de passe admin et stocker uniquement son hash ;
4. si possible, placer `storage_dir` hors de la racine web.

`config.local.php` est ignoré par Git et ne doit pas être committé.
