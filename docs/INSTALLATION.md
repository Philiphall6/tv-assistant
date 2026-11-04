# Installer TV Assistant

## Notifications Home Assistant

Nécessite **l’intégration 0.8.1 et l’APK 2.14.0**, ou des versions ultérieures, pour le bouton de test et les entités de notification. Les actions avancées restent compatibles avec l’intégration 0.8.0. HACS et l’APK se mettent à jour séparément ; aucun nouvel APK n’est nécessaire pour passer de 0.8.0 à 0.8.1.

1. Relier et approuver la TV comme indiqué ci-dessous.
2. Sur la TV, ouvrir **Paramètres → Notifications Home Assistant**, autoriser la superposition dans Android, puis activer la réception. Cette autorisation reste locale : un profil HA ne peut pas l’activer à distance.
3. Dans **TV Assistant → Téléviseurs**, cliquer sur **Tester la notification** sur la fiche de la TV. Un test de 8 secondes est envoyé uniquement à cette TV. Le retour distingue l’envoi de l’abandon si la réception n’est pas prête, sans réveil ni nouvelle tentative automatique. Le statut de réception est indépendant de l’aperçu de l’éditeur, qui peut être hors ligne pendant une autre application.

### Entité de notification et automatisations

Chaque TV approuvée obtient automatiquement un appareil HA et une entité **Notifications** dans **Paramètres → Appareils et services → TV Assistant**. Son identifiant réel apparaît également sur la fiche TV de l’éditeur. Aucun jeton supplémentaire à saisir.

Dans une automatisation, ajouter l’action **Notifications : Envoyer un message**, sélectionner l’entité de la TV, puis remplir le message et, si souhaité, le titre. Exemple avec un identifiant fictif :

```yaml
action: notify.send_message
target:
  entity_id: notify.tv_exemple_notifications
data:
  title: Maison
  message: Le programme est terminé.
```

Cette action utilise un bandeau en haut à droite pendant 8 secondes. L’entité est indisponible lorsque la réception est suspendue ou déconnectée ; aucun message n’est conservé pour plus tard. Son état est la date du dernier envoi par cette action, pas une confirmation d’affichage. Aucun texte de message n’est ajouté à ses attributs. Le renommage de la TV conserve l’identité de l’entité ; supprimer la connexion retire son entité. Une TV qui revient doit être approuvée de nouveau. Les TV partageant un profil gardent des entités distinctes.

Il s’agit d’une entité standard `notify` sélectionnable, pas d’une action dédiée `notify.mobile_app_…` comme celle de l’application iPhone. Les droits restent les mêmes : propriétaire actif de la TV, administrateur ou automatisation HA de confiance.

### Options avancées et retrait d’un bandeau

Déplier **Envoyer une notification depuis HA** dans la fiche TV pour obtenir l’exemple avec le bon identifiant TV Assistant. Les actions existantes restent disponibles pour régler la durée, la position et le `tag`.

Exemple d’action HA, avec un identifiant fictif à remplacer par celui affiché dans votre éditeur :

```yaml
action: home_assistant_tv.notify
data:
  device_ids: [tv_exemple]
  title: Maison
  message: Le programme est terminé.
  duration: 8
  position: top_right
  tag: lave_linge
```

Pour retirer le bandeau lorsqu’un appareil s’arrête, utiliser la même TV et le même `tag` dans une seconde automatisation :

```yaml
action: home_assistant_tv.dismiss_notification
data:
  device_ids: [tv_exemple]
  tag: lave_linge
```

Positions : `top_right`, `top_left`, `bottom_right`, `bottom_left`. Durée : 3 à 60 secondes ; titre : 80 caractères ; message : 600 caractères maximum. Un nouveau bandeau remplace le précédent, sans file d’attente. Limite : un envoi par seconde et par TV. Les textes longs peuvent être tronqués à l’écran. Le `tag` permet le retrait ciblé, mais ce n’est pas une carte permanente ou conditionnelle.

**Veille et confidentialité :** aucun affichage écran éteint, verrouillé ou pendant l’économiseur d’écran ; aucun réveil ni rattrapage à l’allumage, même pour un ancien message encore en transit. Aucun historique dans TV Assistant. Les notifications utilisent le jeton protégé et la connexion HTTPS/WSS existants, sans service cloud ni port entrant ajouté sur la TV. Les traces d’automatisation de HA peuvent toutefois conserver le texte de l’action.

Seuls le propriétaire de la TV approuvée, un administrateur et les automatisations HA autorisées peuvent envoyer un message. Le compte de réception TV peut rester non administrateur. Une réponse `sent` indique une remise au flux connecté, **pas une garantie d’affichage Android** ; `skipped` compte les TV non reliées ou dont l’affichage est suspendu.

**Limites :** première version texte uniquement, sans image ni bouton. La réception utilise un service Android facultatif avec un indicateur discret. Après un redémarrage Android ou un arrêt forcé, ouvrir le launcher une fois. Certains fabricants, réglages d’économie d’énergie et applications protégées peuvent masquer les superpositions ou arrêter le service : ne pas l’utiliser comme unique canal d’alerte critique. Une future distribution Google Play nécessitera aussi la déclaration et l’examen du service `specialUse`.

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

À partir de l’APK **2.13.8**, la version GitHub du launcher propose aussi **Paramètres → Mises à jour et diagnostic** :

1. Choisissez **Vérifier les mises à jour**, puis **Télécharger la mise à jour depuis GitHub**.
2. Attendez le téléchargement et les contrôles du fichier. Le téléchargement peut être annulé.
3. Si Android le demande, autorisez l’installation pour **TV Assistant** uniquement, puis revenez dans le launcher.
4. Choisissez **Installer la mise à jour téléchargée** et confirmez dans l’écran Android. Vous pouvez ensuite retirer l’autorisation d’installation.

La première installation de cette fonctionnalité nécessite encore l’APK ou ADB. Les mises à jour suivantes ne nécessitent plus de PC ni de navigateur sur la TV. Aucune désinstallation et aucune installation silencieuse ne sont réalisées ; vos réglages sont conservés. HACS met à jour l’intégration HA séparément. Ce mécanisme ne concerne pas une éventuelle version Play Store.

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
