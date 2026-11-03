# Versions

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
