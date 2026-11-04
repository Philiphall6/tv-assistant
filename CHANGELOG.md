# Versions

## Intégration 0.7.4 / Android 2.13.7

- Logo TV Assistant dans le menu latéral de Home Assistant, chargé sur toutes les pages et adapté à la couleur du thème.
- Icône officielle dans l’en-tête de l’éditeur et logo dans les présentations française et anglaise de GitHub/HACS.
- Version de l’éditeur issue de l’intégration chargée, au lieu de l’ancien texte fixe « 0.7.1 ».
- Documentation HACS par défaut et bouton d’installation inclus dans cette version publiée.
- Visuels locaux fournis pour les pages d’intégration HA et les clients compatibles. HACS 2.0.5 ne lit pas encore les logos locaux dans sa liste : cette limitation externe n’est pas contournée en modifiant HACS.
- Suppression confirmée des profils inutilisés et des connexions TV, réservée aux administrateurs. Les profils encore affectés à une TV sont protégés ; une TV supprimée doit être autorisée de nouveau si elle se reconnecte.
- Parcours de connexion en trois étapes et états distincts pour les données HA, la liaison de l’éditeur, l’autorisation et la synchronisation du profil.
- Sur la TV, indication explicite d’une liaison inactive, d’une intégration absente, d’un changement de compte, d’une autorisation en attente ou d’un profil incompatible, avec aide et date du dernier contrôle.

Aucune suppression automatique ni révocation de jeton. Les profils et réglages existants sont conservés lors de la mise à jour.

## Intégration 0.7.3 / Android 2.13.6

- Nouvel écran **Mises à jour et diagnostic** : vérification de la version de l’APK dans les publications stables officielles, option quotidienne désactivée par défaut et indication d’une version disponible. Aucun téléchargement ni installation automatique.
- Diagnostic technique local avec aperçu, partage, copie ou enregistrement explicite. Liste fermée d’informations, sans données personnelles, journaux bruts ni déchiffrement du jeton HA.
- Fond intégré **TV Assistant** sur les nouvelles installations, disponible aussi dans les réglages TV et HA ; aucune substitution des fonds existants.
- Disposition de l’onglet Applications : 3 à 12 applications par rangée, 1 à 6 rangées, taille des icônes et des noms indépendante des favoris. Les grandes icônes sont limitées à la place disponible pour éviter les chevauchements.
- Tests des versions, URL non fiables, confidentialité du diagnostic, migration des fonds et dimensions de grille ; traduction des nouveaux réglages en français et anglais.

## Intégration 0.7.2 / Android 2.13.5

- Veille : l’accueil et le tableau de bord HA ne maintiennent plus l’écran allumé en permanence. L’économiseur système peut démarrer après le délai d’inactivité défini sur la TV ; aucun album Google ni réglage de veille n’est modifié.
- Reprise de lecture : contenus réellement partagés par les applications Android TV, titres, images et progression réelle, avec navigation entre les résultats. Aucun contenu fictif ; icône de l’application en secours si une image est indisponible.
- Demande explicite de l’autorisation Android de lecture des reprises lorsqu’elle est nécessaire. Le refus est respecté ; aucun historique n’est modifié.
- Raccourcis de télécommande TCL reconnus par leurs noms système et association locale d’une touche à une application. Les touches Home, Retour, volume et alimentation restent réservées au système ; les touches interceptées par le fabricant ne sont pas garanties.
- Chargement des images en arrière-plan, cache mémoire limité, validation des adresses et absence de jeton HA dans les requêtes d’illustration.
- Tests de non-régression de veille, reprises, images et télécommande ; traductions françaises et anglaises des nouvelles commandes.

L’intégration HA met à jour son catalogue de traductions sans modifier les profils. La publication ne redémarre pas HA et n’installe pas automatiquement l’APK. La signature Android reste identique pour conserver les réglages lors d’une mise à jour.

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
