# TV Assistant

[English](docs/README.en.md) · [Installation](docs/INSTALLATION.md) · [Télécharger l’APK](https://github.com/Philiphall6/tv-assistant/releases) · [Sécurité](SECURITY.md)

[![Validation](https://github.com/Philiphall6/tv-assistant/actions/workflows/validate.yml/badge.svg)](https://github.com/Philiphall6/tv-assistant/actions/workflows/validate.yml)
[![Android](https://github.com/Philiphall6/tv-assistant/actions/workflows/android.yml/badge.svg)](https://github.com/Philiphall6/tv-assistant/actions/workflows/android.yml)

Un launcher Android TV personnalisable et un éditeur multi-TV dans Home Assistant. Projet communautaire indépendant, sans affiliation officielle avec Home Assistant ou Google.

## Aperçu

### Accueil sur la TV

![Accueil TV avec fond d’écran, carte PAC, favoris et commandes de la maison](docs/images/tv-home.jpg)

### Éditeur dans Home Assistant

![Éditeur Home Assistant avec aperçu de la TV et placement des éléments](docs/images/ha-editor.jpg)

Captures fournies et autorisées pour publication par le propriétaire du projet. Elles illustrent une version antérieure au renommage en **TV Assistant** ; les libellés, applications et cartes visibles dépendent de votre configuration. Les métadonnées des fichiers ont été retirées.

## Compatibilité

- Android **11 à 16** (API 30 à 36), télécommande directionnelle et écran paysage. Android 9 et 10 ne sont pas pris en charge.
- Home Assistant **2026.10.4 ou supérieur** pour l’intégration. Android System WebView doit être à jour pour les vraies cartes Lovelace.
- Connexion Home Assistant **HTTPS avec certificat valide** obligatoire. Les certificats non valides et les connexions HTTP sont refusés.
- Le choix du launcher par défaut dépend du fabricant. L’application ne désactive jamais automatiquement le launcher système.

## Fonctions

- Applications favorites, formes d’icônes, appui long, reprise de lecture lorsqu’une application fournit ces données.
- Barre configurable : position, dimensions, onglets, couleurs, horloge, statut et recherche vocale.
- Cartes natives, placement libre, éditeur visuel et YAML dans HA, profils partagés ou différents par TV.
- Cartes Lovelace réelles dans une surface WebView persistante, ressources fournies par votre HA, sans rechargement périodique de la page. Pointeur télécommande pour les cartes interactives.
- Fonds unis, image locale, album Google Photos partagé avec cache et synchronisation. Cette dernière fonction dépend du format des albums publics Google et peut nécessiter des adaptations.

L’aperçu réel nécessite que le launcher soit ouvert sur la TV. Un schéma de placement reste disponible pour organiser la disposition ; ce n’est pas une fausse capture en direct. Les cartes personnalisées nécessitent leurs extensions installées dans HA et ne sont pas toutes garanties compatibles avec une télécommande.

## Installation

1. Dans HACS → menu → **Dépôts personnalisés**, ajouter `https://github.com/Philiphall6/tv-assistant`, type **Intégration**. Ce dépôt personnalisé n’est pas une annonce d’inclusion dans le catalogue HACS par défaut.
2. Télécharger l’intégration, redémarrer HA à un moment approprié, puis ajouter **TV Assistant** dans **Paramètres → Appareils et services**.
3. Installer l’APK signé depuis les [versions publiées](https://github.com/Philiphall6/tv-assistant/releases). HACS installe l’intégration, **pas l’APK**.
4. Créer un utilisateur HA dédié à la TV, **non administrateur**, et son jeton longue durée. Le saisir uniquement dans les réglages locaux de la TV avec l’adresse HTTPS de HA.
5. Activer l’édition depuis HA sur la TV. Depuis votre compte administrateur habituel, ouvrir **TV Assistant**, créer un profil et l’attribuer explicitement à cette TV après vérification de son identifiant.

L’administrateur peut modifier toutes les TV ; leur utilisateur dédié reste non administrateur. Les nouvelles TV n’obtiennent aucun profil tant qu’elles ne sont pas approuvées par cette attribution.

Voir [installation et retour arrière](docs/INSTALLATION.md), [compilation et signature](docs/BUILD.md), [sécurité](SECURITY.md) et [validation](docs/TESTING.md).

## Langues

La TV utilise la langue d’Android ; le panneau utilise la langue du compte Home Assistant, même si le navigateur est dans une autre langue. Français et anglais sont fournis, avec l’anglais comme langue de repli. Les noms personnalisés, les profils, les entités et le YAML ne sont pas traduits ni réécrits.

Les dates et nombres suivent les paramètres régionaux. L’horloge suit le format 12/24 heures d’Android. La langue de reconnaissance vocale vaut `auto` par défaut ; un choix explicite comme `fr` ou `en` reste prioritaire. Les anciennes configurations avec une langue explicite sont conservées. Les cartes Lovelace utilisent les traductions fournies par HA et leurs propres extensions ; certains diagnostics techniques peuvent conserver leur langue d’origine.

## Mise à jour et données

L’APK utilise le même identifiant technique `fr.maison.tvlauncher`, pour préserver les mises à jour des installations précédentes lorsque la signature correspond. Le domaine HA reste `home_assistant_tv` pour la même raison. Ces noms techniques ne sont pas des données personnelles.

Les anciens jetons sont migrés vers Android Keystore lors de leur lecture avec une adresse HTTPS valide. Les réglages locaux et jetons ne sont pas exportés dans les profils HA. Une nouvelle TV ou une réinstallation après désinstallation nécessite de ressaisir son jeton ; aucune sauvegarde Android des données de l’application n’est autorisée.

Le dépôt ne contient ni jeton, clé de signature, fichier de configuration domestique, lien d’album personnel, ni ancien historique privé. Les exemples et tests utilisent des données fictives ; seules les deux captures de démonstration ci-dessus ont été expressément autorisées pour publication.

## Licence

Code original sous **GPL-3.0-only** : [LICENSE](LICENSE). Les bibliothèques et marques conservent leurs licences et droits respectifs : [THIRD_PARTY.md](THIRD_PARTY.md). Les noms et logos Home Assistant et Google ne sont pas couverts par la licence GPL de ce projet.

Pour contribuer : [guide de contribution](CONTRIBUTING.md), [traductions](docs/LOCALIZATION.md) et [préparation au catalogue HACS](docs/HACS_DEFAULT.md).
