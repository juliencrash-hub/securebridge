# SecureBridge - configuration de déploiement

Le dépôt public contient le moteur générique. Les vraies informations d'un déploiement ne doivent pas être commitées.

Quatre valeurs relient une compilation Android à un déploiement :

- `SECUREBRIDGE_ALLOWED_ORIGIN`
- `SECUREBRIDGE_ALLOWED_PATH`
- `SECUREBRIDGE_API_URL`
- `SECUREBRIDGE_DEPLOYMENT_ID`

## Sans domaine réel

Aucune valeur privée n'est nécessaire. GitHub compile un APK debug générique avec `SECUREBRIDGE_CONFIGURED=false`.

## Build local personnalisé

Copier :

```text
deployment.example.properties
→ deployment.properties
```

Puis remplacer les quatre valeurs. `deployment.properties` est ignoré par Git.

## Build GitHub personnalisé

Créer quatre Actions secrets portant exactement les noms ci-dessus. Le workflow les injecte à Gradle sans les écrire dans le dépôt public.

Si aucun secret n'est défini, le build debug générique fonctionne. Si une configuration partielle est fournie, le build échoue.

## Site et backend

Les modèles publics sont :

- `web/config.example.js`
- `server/config.example.php`

Les fichiers réels `web/config.local.js` et `server/config.local.php` sont ignorés par Git.

Les secrets serveur critiques, comme le mot de passe administrateur ou les clés privées, doivent rester uniquement sur l'hébergement.

## Limite

Cette séparation empêche un simple clone du dépôt de récupérer ta configuration réelle. Elle ne rend pas un domaine intégré dans un APK cryptographiquement secret : une analyse du binaire peut le révéler.
