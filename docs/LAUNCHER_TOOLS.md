# Outils du launcher — intégration 0.9.0 / APK 2.15.0

Ces fonctions nécessitent l’APK **2.15.0** et l’intégration **0.9.0**, ou des versions ultérieures. Les deux composants se mettent à jour séparément. La disposition et les réglages existants sont conservés ; les modèles, le PIN, le réseau et le mode cinéma ne sont pas activés automatiquement.

## Réseau et diagnostic

Dans **Barre → Informations**, deux options indépendantes, désactivées par défaut :

- Icône de la connexion active : Ethernet, Wi-Fi, VPN ou état indisponible.
- Débit reçu ↓ et envoyé ↑, en kb/s, Mb/s ou Gb/s, échantillonné toutes les deux secondes sur l’accueil.

Le débit vient des compteurs Android de toutes les interfaces de la TV. Il inclut le réseau local et peut compter certains échanges plusieurs fois avec un VPN. C’est une indication de trafic, pas une mesure certifiée ni la capacité maximale de l’accès Internet. Le premier échantillon et les compteurs indisponibles affichent un tiret. Aucun téléchargement de test, accès au SSID ou collecte d’adresse IP/MAC. La mesure s’arrête hors du launcher.

**Paramètres → Diagnostic des connexions** distingue réseau, validation Internet par Android, dernier état des données HA et dernier état de l’éditeur. Le bouton de test fait uniquement une lecture de l’API HA configurée. Il ne commande aucun appareil et ne confirme pas à lui seul la synchronisation de l’éditeur. HA local peut fonctionner sans Internet ; l’aperçu peut être hors ligne lorsque le launcher n’est pas au premier plan.

## Dispositions et annulation

**HA → Écran → Dispositions prêtes à l’emploi** propose Rangées, Cinéma et Dock, inspirés respectivement d’Android TV, Google TV et Apple TV. Ce sont des dispositions de TV Assistant, pas des ports des systèmes ni une affiliation avec leurs éditeurs.

Les modèles déplacent les blocs classiques et la barre, sans remplacer les applications, les connexions ou les cartes libres. Vérifiez les chevauchements avec vos propres cartes avant l’envoi. Sur une TV non pilotée par HA, les modèles se trouvent dans **Accueil et disposition**.

**Annuler la dernière modification** conserve jusqu’à 20 étapes pendant la session de l’éditeur HA : placement, taille, contenu ou réglage. Un déplacement à la souris compte pour une étape. L’annulation reste un brouillon, y compris après un envoi précédent : envoyez à nouveau pour restaurer la TV. Changer de profil ou fermer la page efface cet historique. Ce n’est pas une sauvegarde persistante. Le bouton d’annulation du dernier modèle existe également sur la TV en mode local.

## Forme des icônes

Dans **HA → Apparence → Icônes des applications**, ou **TV → Paramètres → Applications et favoris → Icônes, favoris et grille des applications → Icônes des applications**, les formes existantes sont complétées par **Rectangle 16:9** et **Personnalisée**.

La forme personnalisée offre trois réglages : proportions largeur/hauteur de 50 à 250 % (100 = carré, 160 = rectangle), arrondi de 0 à 50 % du petit côté, et taille du logo de 50 à 100 %. Son aperçu de forme se met à jour pendant le réglage. Dans HA, ces curseurs ne sont actifs que lorsque « Personnalisée » est sélectionnée. La capture TV reste celle de la dernière configuration envoyée.

Le même choix s’applique aux favoris, à la grille Applications et aux raccourcis d’applications placés. Les logos conservent leurs proportions et les espaces restants utilisent la couleur de fond de l’icône. La forme tient dans l’emplacement existant : un rectangle ne déborde pas sur l’application voisine. Cela ne remplace pas les images par des bannières TV et ne modifie ni les icônes HA ni la barre. Les anciens choix sont conservés ; aucun changement automatique de profil.

## Mode invité avec PIN facultatif

Le mode invité est **désactivé par défaut**. Aucun PIN n’est demandé à l’installation. Dans **Paramètres → Mode invité**, créer et confirmer un PIN de 4 à 8 chiffres active explicitement le verrouillage des réglages locaux du launcher et des menus de modification des applications. Les applications restent utilisables.

Le PIN est dérivé avec PBKDF2-HMAC-SHA256 et un sel aléatoire, stocké dans des préférences privées séparées, jamais envoyé à HA ou inclus dans l’export des profils. Après cinq échecs, un délai de 30 secondes s’applique. Aucun code maître ni récupération distante. Effacer les données de l’application retire aussi la configuration et la connexion HA.

Ce verrouillage n’est **pas un contrôle parental Android ni un mode kiosque** : les réglages Android, les autres applications, les commandes HA autorisées au compte TV et l’administration HA restent indépendants. Un appareil rooté ou compromis peut contourner ce verrou local.

## Cartes conditionnelles

Dans **Cartes → Modifier → Conditions d’affichage**, ajouter jusqu’à huit conditions combinées avec « et ». Elles s’appliquent aux cartes natives, aux applications placées et aux enveloppes de vraies cartes HA, dans Maison comme sur Pour vous. Aucun rechargement périodique de page : les états HA reçus mettent à jour la visibilité.

```yaml
type: button
name: Équipement actif
entity: switch.exemple
conditions:
  - entity: switch.exemple
    operator: state
    value: 'on'
```

Opérateurs : `state`, `not_state`, `above`, `below`. Les états sont exacts, non traduits ; les seuils sont des nombres, sans unité. Une entité absente, `unknown`, `unavailable` ou une liaison HA hors ligne masque la carte. La carte reste enregistrée et réapparaît quand toutes les conditions redeviennent vraies. Une carte libre ne provoque pas le déplacement automatique de ses voisines.

## Mode cinéma discret

Dans **Paramètres → Mode cinéma**, ou **HA → Apparence → Mode cinéma** : masque la salutation native, le sous-titre, l’état HA et l’indication réseau, sans supprimer leurs réglages. L’heure et la navigation restent accessibles. Les rotations natives et le diaporama s’arrêtent. À la sortie, les réglages habituels sont rétablis.

Les animations CSS des cartes HA sont mises en pause dans leurs racines DOM accessibles. Les animations JavaScript, canvas, vidéos et composants à racine fermée ne sont pas garantis : le launcher ne modifie pas le code privé d’une carte ni les autres applications. Une salutation créée comme carte libre reste une carte indépendante.

Pour les notifications : toutes, aucune, ou uniquement une liste de `tag` séparés par des virgules. Utiliser les mêmes tags dans les actions HA `home_assistant_tv.notify`. Sans tag correspondant, une notification filtrée est abandonnée, jamais mise en attente. Les protections de veille et l’absence de rattrapage au réveil restent actives.

## Applications, reprise et HDR

Un appui long propose **Renommer** et le retour au nom original, sans changer l’identifiant Android, les favoris ou les liens de lecture. **Sources de reprise de lecture** permet de masquer une source localement et distingue une application absente, un flux inaccessible et l’absence de contenu partagé.

La reprise utilise le protocole Android TV Watch Next. Les liens explicites sans URI publiés par Jellyfin sont acceptés uniquement si leur activité appartient à l’application émettrice. HBO/Max, Apple TV et IPTVnaptor TV doivent publier des entrées compatibles : aucun historique privé, compte de streaming ou résultat fictif n’est récupéré. Une application installée n’est pas une garantie de publication.

**Écran et HDR** indique les formats déclarés par Android et propose deux mires HDR10 locales : noirs et hautes lumières. Vidéo originale HEVC Main 10, BT.2020/PQ, silencieuse, 12 secondes, avec sortie immédiate via Retour. Android doit déclarer HDR10 ; le décodage peut encore être refusé par le matériel. Les formats déclarés ne prouvent ni le mode HDR actif, ni les droits DRM d’un service. Pas de mire Dolby Vision/HDR10+, de réglage constructeur forcé ou de calibration certifiée. Une capture SDR ne valide pas le rendu HDR.

## Root optionnel

**Accès root optionnel** reste désactivé par défaut. Sur un appareil déjà rooté, après activation et appui explicite sur le test, seule la commande fixe `su -c 'id -u'` est demandée au gestionnaire root, avec un délai maximal de 20 secondes. Aucun root, déverrouillage, téléchargement, changement système ou accès aux données privées d’autres applications. C’est un diagnostic d’accès, pas une console root ni une garantie de compatibilité de tous les gestionnaires root.
