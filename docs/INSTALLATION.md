# Installer TV Assistant

## Comprendre la connexion TV ↔ Home Assistant

Les deux liaisons sont distinctes : **données HA** (états et commandes de la maison) et **éditeur TV Assistant** (profils, mise en page et aperçu). Une TV peut donc accéder à HA sans apparaître connectée dans l’éditeur.

1. Sur la TV, dans les réglages Home Assistant, renseigner l’adresse HTTPS et le jeton d’un utilisateur non administrateur, puis tester la connexion.
2. Ouvrir **Édition depuis Home Assistant**, nommer la TV et activer la liaison. Revenir à l’accueil quelques secondes.
3. Dans HA, avec votre compte administrateur, ouvrir **TV Assistant → Téléviseurs**, comparer l’identifiant avec celui affiché sur la TV, puis affecter un profil enregistré.

L’écran **Édition depuis Home Assistant** distingue les deux états, donne une action à effectuer et précise la date du dernier contrôle. Il ne réalise pas un nouveau test réseau en arrière-plan. La veille, une autre application ou l’ouverture des réglages peuvent interrompre la liaison de l’éditeur : **hors ligne ne signifie pas nécessairement une panne de HA**. Le statut sur la barre d’accueil distingue désormais HA seul, l’autorisation en attente et HA avec l’éditeur relié.

Un ancien nom de TV peut correspondre à une association différente, notamment après un changement de compte ou une réinstallation. Comparer les identifiants avant de supprimer une entrée ; ne jamais publier ces identifiants ou un jeton pour demander de l’aide.

### Supprimer un profil ou une connexion

- **Supprimer le profil** retire définitivement sa configuration et son historique après confirmation. Un profil affecté à une TV, même hors ligne, est protégé : réaffecter cette TV ou supprimer sa connexion d’abord.
- **Téléviseurs → Supprimer la connexion** oublie cette association et arrête ses flux de profil et d’aperçu, sans supprimer le profil, réinitialiser la TV ou révoquer son jeton. Les autres TV sont conservées.
- Pour un retrait durable, désactiver d’abord **Édition depuis Home Assistant** sur la TV. Sinon, elle peut réapparaître à sa prochaine connexion, mais devra être autorisée de nouveau par un administrateur.
- Pour révoquer aussi l’accès aux données HA, supprimer explicitement le jeton dans la sécurité du compte Home Assistant concerné. La suppression d’une connexion dans TV Assistant ne remplace pas cette opération.

## Home Assistant et plusieurs TV

[![Installer TV Assistant avec HACS](https://my.home-assistant.io/badges/hacs_repository.svg)](https://my.home-assistant.io/redirect/hacs_repository/?owner=Philiphall6&repository=tv-assistant&category=integration)

Avec [HACS installé et configuré](https://www.hacs.xyz/docs/use/), cliquez sur le bouton ci-dessus ou recherchez **TV Assistant** dans le catalogue HACS par défaut. Le bouton ouvre la fiche de l’intégration dans votre Home Assistant ; si demandé, renseignez l’adresse de votre propre instance dans My Home Assistant. Aucun téléchargement ni redémarrage n’est lancé automatiquement.

Téléchargez l’intégration, redémarrez HA à un moment approprié, puis ajoutez **TV Assistant** dans **Paramètres → Appareils et services**. Son panneau est réservé aux administrateurs. HACS installe l’intégration, **pas l’APK Android TV**, disponible séparément dans les [versions publiées](https://github.com/Philiphall6/tv-assistant/releases).

Si l’intégration n’apparaît pas encore dans votre recherche, utilisez la solution de secours par **Dépôts personnalisés** décrite dans le [README](../README.md#installation).

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

## Reprise de lecture et télécommande

La rangée de reprise lit uniquement les contenus publiés par les applications dans le fournisseur Android TV. Si la TV demande une autorisation supplémentaire, acceptez la demande Android après avoir lu son explication ; vous pouvez aussi refuser et continuer à utiliser le launcher. L’autorisation peut être demandée à nouveau depuis **Paramètres → Rangées de l’accueil → Autoriser la reprise de lecture** ou l’assistant d’installation. Cet accès ne modifie pas votre historique.

La présence d’une application ne garantit pas qu’elle partage des reprises. Une rangée vide reste vide ; aucune affiche ou progression n’est inventée. Une image indisponible est remplacée par l’icône de l’application. Les images et liens proviennent des applications concernées, pas d’une connexion à leurs comptes depuis TV Assistant.

Dans **Paramètres → Général → Télécommande**, choisissez **Associer une touche à une application**, appuyez sur la touche puis sélectionnez l’application. L’association fonctionne lorsque TV Assistant traite la touche, pas dans les autres applications. Les touches Home, Retour, volume, alimentation et navigation ne sont pas réaffectées. Certaines touches réservées par le système ne parviennent pas au launcher.

## Diaporama Google et veille

L’accueil et le tableau de bord HA ne bloquent pas volontairement l’économiseur d’écran. Son choix, ses albums et son délai restent gérés dans les paramètres de la TV ou Google Home, selon le modèle. Le bouton d’alimentation et la veille de la TV restent gérés par Android.

L’économiseur Google est distinct du fond Google Photos partagé du launcher : il remplace l’accueil pendant l’inactivité, puis laisse Android reprendre l’application ou l’accueil lors d’une interaction. Les cartes HA ne se superposent pas au diaporama Google. Une lecture vidéo déclenchée dans une application peut naturellement différer l’économiseur.

## Dépannage

- **HTTPS refusé** : utilisez un certificat valide pour le nom de votre serveur et une horloge Android correcte. Aucun bouton « ignorer le certificat » n’est fourni.
- **TV en attente** : attribuez un profil depuis votre compte administrateur dans HA.
- **Carte inaccessible** : vérifiez le compte propriétaire de la TV, son approbation, la disponibilité de la ressource Lovelace et la mise à jour de WebView.
- **TV hors ligne dans l’aperçu** : ouvrez le launcher ; aucune TV n’est réveillée automatiquement.
- **Migration d’une ancienne adresse HTTP** : configurez d’abord HTTPS puis saisissez de nouveau le jeton. Ne publiez pas vos anciennes préférences pour demander de l’aide.
