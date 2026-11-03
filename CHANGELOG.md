# Versions

## Intégration 0.7.1 / Android 2.13.1

- Nouvelle identité d’installation : télévision blanche avec branche connectée sur fond bleu, accompagnée du nom « TV Assistant ».
- Icône du package Android, bannière Android TV et visuels HA/HACS en SVG et PNG, avec variantes haute résolution.
- Logo de la barre, interface du launcher et interface de l’éditeur HA inchangés ; aucun réglage utilisateur modifié.
- Tests de cohérence des visuels et de rendu Android. Même identifiant d’application et même clé de signature pour les mises à jour.

La publication n’installe rien sur les TV existantes et ne redémarre pas Home Assistant. L’admission au catalogue HACS reste soumise à la validation de ses mainteneurs.

## Intégration 0.7.0 / Android 2.13.0

- Langue automatique dans le launcher et l’éditeur HA : français, anglais et repli anglais pour les langues non traduites.
- Ressources Android natives, suivi de la langue du compte HA, conservation des brouillons et des noms personnalisés lors du changement de langue.
- Dates et nombres régionaux, format d’heure 12/24 heures et reconnaissance vocale `auto` suivant Android, sans modifier les choix explicites existants.
- Largeur des onglets calculée avec les polices normale et sélectionnée pour éviter les libellés traduits tronqués.
- Documentation française et anglaise, captures autorisées avec métadonnées supprimées, guides de contribution et de traduction.
- Workflows HACS et Hassfest sans contrôles ignorés, tests navigateur, documentation, traductions et matrice Android API 30/36.
- Préparation de candidature au catalogue HACS par défaut, sans annoncer une admission.

L’APK conserve le même identifiant et la même clé de signature de publication. Aucun redémarrage ou changement de votre installation HA n’est effectué par la publication du dépôt.

## Intégration 0.6.0 / Android 2.12.0

Première publication du projet TV Assistant sous GPL-3.0-only, avec un historique neuf et des exemples génériques.

- Compatibilité déclarée Android 11–16 (API 30–36), tests d’exécution aux deux bornes.
- Jeton chiffré avec Android Keystore, AES-GCM et liaison à l’origine HTTPS ; migration de l’ancien stockage local.
- Connexions HA HTTPS/WSS obligatoires, validation TLS et limites de taille réseau.
- Sauvegardes Android désactivées, écran du jeton protégé, nettoyage explicite de la session locale.
- Approbation administrateur obligatoire pour les nouvelles TV avant réception d’un profil.
- Structure de dépôt HACS, scripts de validation et modèle CI à activer, documentation d’installation et procédure réversible de remplacement de l’accueil.
- Compilation non signée par défaut, signature privée séparée, outil de protection d’un coffre existant sans changer le certificat.

Les identifiants techniques Android et HA restent stables pour les mises à jour. Une configuration HTTP doit passer à HTTPS. Les anciens appareils déjà associés restent associés ; revoyez leurs comptes propriétaires dans votre installation.
