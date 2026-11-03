# Installer TV Assistant

## Home Assistant et plusieurs TV

Installez le dépôt personnalisé HACS décrit dans le README, redémarrez HA, puis ajoutez l’intégration TV Assistant. Son panneau est réservé aux administrateurs.

Sur chaque TV, saisissez votre propre adresse HTTPS valide et le jeton d’un utilisateur TV non administrateur. Vous pouvez employer un utilisateur distinct par TV pour faciliter la révocation. Dans les réglages de liaison au panneau, donnez un nom à l’appareil. Dans HA, vérifiez l’identifiant affiché par la TV, créez le profil et attribuez-le explicitement. Le profil peut être commun à plusieurs TV ou indépendant.

Le jeton reste local à la TV. Le panneau HA utilise votre session administrateur normale pour éditer, sans transformer le compte TV en administrateur. Les mises à jour de configuration sont envoyées par WebSocket ; pas de rafraîchissement complet toutes les deux minutes.

## Android TV / Google TV

Android 11 à 16 sont ciblés. Installez l’APK signé avec l’outil de votre choix, puis ouvrez-le. L’assistant de première ouverture propose le choix de l’accueil et explique les autorisations. La superposition est facultative et ne suffit pas à remplacer un accueil imposé par le fabricant.

Pour une mise à jour, utilisez `adb -s SERIAL install -r TVAssistant.apk` avec le bon appareil et une signature identique. Ne désinstallez pas d’abord l’ancienne version : cela supprime les réglages et la clé locale du jeton. Si Android signale une signature incompatible, arrêtez-vous et vérifiez la provenance de l’APK.

## Si le bouton Home ouvre encore Google TV

Il n’existe pas de méthode universelle sur tous les fabricants. Essayez d’abord les réglages Android d’application d’accueil par défaut. Les commandes suivantes sont **avancées et manuelles**. Elles ne doivent être utilisées que sur votre appareil, après avoir confirmé le nom exact de son launcher et gardé ADB connecté.

```sh
adb devices
adb -s SERIAL shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s SERIAL shell am start -n fr.maison.tvlauncher/.MainActivity
adb -s SERIAL shell cmd package set-home-activity fr.maison.tvlauncher/.MainActivity
adb -s SERIAL shell input keyevent KEYCODE_HOME
```

Uniquement si le launcher imposé a été confirmé comme **`com.google.android.tvlauncher`**, si TV Assistant s’ouvre correctement et si vous acceptez de désactiver cet accueil système :

```sh
adb -s SERIAL shell pm disable-user --user 0 com.google.android.tvlauncher
adb -s SERIAL shell input keyevent KEYCODE_HOME
```

Cette commande désactive le paquet sans le désinstaller. Le nom peut être différent selon la TV : **ne copiez pas cette commande pour un autre paquet**. L’application n’exécute jamais cette opération elle-même.

Pour revenir à Google TV, et impérativement **avant de retirer TV Assistant** :

```sh
adb -s SERIAL shell pm enable --user 0 com.google.android.tvlauncher
adb -s SERIAL shell input keyevent KEYCODE_HOME
```

Désactivez le débogage réseau après l’installation. Ne laissez pas ADB accessible à des appareils non fiables.

## Dépannage

- **HTTPS refusé** : utilisez un certificat valide pour le nom de votre serveur et une horloge Android correcte. Aucun bouton « ignorer le certificat » n’est fourni.
- **TV en attente** : attribuez un profil depuis votre compte administrateur dans HA.
- **Carte inaccessible** : vérifiez le compte propriétaire de la TV, son approbation, la disponibilité de la ressource Lovelace et la mise à jour de WebView.
- **TV hors ligne dans l’aperçu** : ouvrez le launcher ; aucune TV n’est réveillée automatiquement.
- **Migration d’une ancienne adresse HTTP** : configurez d’abord HTTPS puis saisissez de nouveau le jeton. Ne publiez pas vos anciennes préférences pour demander de l’aide.
