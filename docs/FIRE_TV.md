# Amazon Fire TV — préparation expérimentale

[English](FIRE_TV.en.md) · [Installation générale](INSTALLATION.md)

**Aucun appareil Fire TV réel n’a été testé par le projet.** Cette adaptation reste expérimentale et ne constitue ni une certification Amazon ni une disponibilité dans l’Amazon Appstore. Un test sur émulateur Android ne valide pas Fire OS.

Le même code et le même APK servent à Android TV et à Fire TV sous Fire OS. Aucun compte Amazon ni service Google n’est ajouté. Les profils et l’éditeur Home Assistant restent communs.

## Appareils concernés

| Système de l’appareil | Statut |
| --- | --- |
| Fire OS avec API Android 30 à 36 | Cible des essais communautaires, pas encore validée sur matériel |
| Fire OS avec API inférieure à 30 | Non pris en charge ; la limite Android 11 minimum est conservée |
| Vega OS | Non compatible avec cet APK ; nécessite un portage distinct |

Ne vous fiez pas uniquement au nom commercial « Fire TV » ou au numéro Fire OS. Vérifiez le modèle, sa génération et son niveau d’API dans les [spécifications Amazon](https://developer.amazon.com/docs/device-specs/identify-fire-tv-devices.html). La gamme comprend aussi des appareils Vega OS, qui ne sont pas des appareils Android. Voir [Fire OS](https://developer.amazon.com/docs/fire-tv/fire-os-overview.html) et [portage vers Vega](https://developer.amazon.com/docs/adbt/port-fire-os-app-to-vega).

## Ce que prépare cette adaptation

- Une entrée dans la liste des applications Android standard, en plus de l’entrée Android TV.
- La détection de la fonctionnalité officielle `amazon.hardware.fire_tv`, sans collecter de nom, de modèle ou de numéro de série.
- Une aide de première installation spécifique : usage comme application, limites de Home, aucune instruction de désactivation du launcher Google sur Fire TV.
- Un libellé de recherche système sans promesse de service Google ni d’intégration Alexa. La saisie au clavier reste disponible si la transcription Android manque.
- Aucun encouragement à demander l’accès à la reprise lorsque le fournisseur Android TV est absent.
- Un diagnostic local indiquant seulement `FIRE_TV` ou `ANDROID`, le niveau d’API et la disponibilité du fournisseur de reprise, sans identifiant d’appareil.

Ces changements doivent être inclus dans l’APK testé. **La version publique 2.13.8 ne contient pas encore cette préparation Fire TV.** Les contributeurs peuvent compiler le code suivant le [guide de compilation](BUILD.md) ; ne distribuez jamais un APK non signé ou une clé privée. Une future publication devra indiquer explicitement cette adaptation dans ses notes.

## Installation pour les testeurs

1. Vérifier que l’appareil utilise **Fire OS, API 30 minimum**, et non Vega OS.
2. Utiliser uniquement un APK signé du projet dont les notes mentionnent les essais Fire TV. Pour une mise à jour, garder la même signature et ne pas désinstaller l’application existante.
3. Suivre le [guide Amazon de connexion ADB](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html), sur un réseau local de confiance, et accepter l’ordinateur sur l’écran de la TV. Ne jamais exposer ADB sur Internet.
4. Installer puis ouvrir TV Assistant depuis les applications. Les commandes ci-dessous ciblent explicitement l’appareil ; remplacer `ADRESSE_FIRE_TV` et `VERSION` localement.

```sh
adb connect ADRESSE_FIRE_TV:5555
adb -s ADRESSE_FIRE_TV:5555 shell getprop ro.build.version.sdk
adb -s ADRESSE_FIRE_TV:5555 install -r ./TVAssistant-VERSION.apk
adb -s ADRESSE_FIRE_TV:5555 shell am start -n fr.maison.tvlauncher/.MainActivity
```

5. Configurer HTTPS et le jeton d’un utilisateur HA non administrateur sur l’appareil, puis approuver la liaison depuis votre compte administrateur dans l’éditeur HA, comme pour Android TV.
6. Déconnecter ADB et désactiver le débogage une fois les essais terminés. Ne pas partager son adresse dans un ticket public.

## Limites à connaître

- **Home :** cette expérimentation vise d’abord le mode application/dashboard. Le bouton Home peut retourner à l’accueil Amazon. Ni son remplacement ni le démarrage automatique ne sont garantis. Aucun composant Amazon n’est désactivé, aucune accessibilité n’est utilisée pour intercepter Home.
- **Télécommande :** flèches, OK, Retour et appui long sont à vérifier sur l’appareil. Alexa, alimentation, volume et raccourcis réservés au système ne sont pas capturés. Amazon précise que [Home et le microphone sont des événements système non interceptables](https://developer.amazon.com/docs/fire-tv/remote-input.html).
- **Voix :** Assist peut recevoir du texte saisi au clavier. La transcription dépend d’un service compatible avec les intents Android ; le bouton Alexa n’est pas une intégration Assist.
- **Cartes HA :** les cartes natives et les vraies cartes Lovelace sont deux mécanismes différents. Les cartes Lovelace exigent un moteur WebView compatible avec le pont de messages sécurisé. Si ce moteur manque, la sécurité n’est pas abaissée pour contourner le problème.
- **Reprise :** les contenus de l’accueil Amazon ne sont pas récupérés. Seules les données qu’un fournisseur Android TV accessible partage avec l’application peuvent apparaître ; une rangée vide n’est pas remplacée par du contenu fictif.
- **Photos, installation de mises à jour et veille :** dépendent également des sélecteurs, permissions et comportements du firmware. Ils doivent être testés, pas supposés identiques à Google TV.

## Aider à valider la compatibilité

Utiliser le [formulaire de test Fire TV](https://github.com/Philiphall6/tv-assistant/issues/new?template=fire_tv_test.yml). Un résultat négatif est aussi utile qu’un résultat positif.

Vérifier : installation et ouverture, navigation et appui long, lancement d’applications, HTTPS/jeton, liaison à un profil HA, cartes natives, cartes Lovelace, retour après veille, conservation des réglages après mise à jour. Tester une commande domotique uniquement si vous souhaitez réellement l’exécuter et avez choisi un appareil sans risque.

Indiquer uniquement le **modèle commercial et sa génération**, les versions Fire OS/API/APK/HA/WebView, les étapes et le résultat. Ne pas joindre de jeton, adresse, numéro de série, identifiant de TV, profil complet, album personnel ou journal brut. Le diagnostic intégré peut être relu avant partage ; les captures doivent être vérifiées et masquées si nécessaire.

Tant qu’aucun test matériel n’est documenté, le statut reste **expérimental, non validé sur Fire TV**.
