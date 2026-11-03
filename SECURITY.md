# Sécurité

## Protections de la version publique

- Minimum Android 11, compilation/cible Android 16. APK de production non débogable ; seule l’activité principale est exposée pour remplir le rôle de launcher.
- Jeton HA chiffré en **AES-256-GCM** avec une clé Android Keystore non exportable et un nonce aléatoire. L’adresse HTTPS du serveur est authentifiée avec le ciphertext : changer de serveur ne réutilise pas silencieusement le jeton.
- HTTPS/WSS et validation du certificat et du nom d’hôte. Pas de contournement TLS, pas de certificats utilisateurs ajoutés aux autorités de confiance de l’application, pas de redirection des requêtes authentifiées.
- Sauvegarde cloud et transfert Android des données désactivés. Le champ secret n’est pas prérempli, enregistré dans l’état de l’activité ou proposé à l’autocomplétion ; les captures de l’écran de connexion sont bloquées.
- Limites de taille sur les réponses HTTP, trames WebSocket, YAML et profils. Chargement YAML sûr, refus des clés inconnues et validation des actions.
- Le panneau d’édition et ses écritures exigent un administrateur HA. Une nouvelle TV doit être approuvée par une attribution de profil ; son identifiant seul ne donne pas accès au profil demandé.
- Les cartes WebView refusent le contenu mixte, l’accès aux fichiers et les erreurs TLS. Le pont d’authentification est limité à l’origine, au chemin attendu et à la fenêtre principale ; il refuse les jetons administrateurs.
- La signature de distribution est séparée de la compilation. Aucun secret de signature n’est nécessaire dans les pull requests ou le dépôt.

## Limites importantes

Ce travail est un durcissement accompagné de tests, **pas une certification ni une garantie d’absence de vulnérabilité**. Le chiffrement local ne protège pas contre un système compromis, un appareil rooté ou du code exécuté avec les droits de l’application. Le stockage matériel de la clé dépend de l’appareil ; Android Keystore n’implique pas automatiquement StrongBox.

Le jeton doit être déchiffré en mémoire pour communiquer avec HA. Les ressources Lovelace et cartes personnalisées de votre serveur sont du **code de confiance** et peuvent accéder à cette session. N’installez pas de cartes non fiables. Une carte « affichage seul » est un choix d’interface, pas une restriction des permissions HA. Un utilisateur HA non administrateur n’est pas nécessairement limité aux seules entités montrées sur la TV.

La connexion web manuelle au tableau de bord peut conserver une session WebView. Utilisez le même compte TV non administrateur et le bouton d’effacement de la connexion pour retirer le jeton local, les cookies et le stockage web. Révoquez également le jeton **dans HA** si la TV est perdue ou cédée ; l’effacement local ne révoque pas le jeton serveur.

Les anciennes inscriptions de TV restent associées à leur propriétaire lors de la mise à jour. Revoyez ces appareils existants : l’approbation obligatoire s’applique aux **nouvelles** inscriptions. Un changement de compte propriétaire nécessite une nouvelle identité de TV et une approbation explicite.

Les captures demandées par l’administrateur et les profils restent privés dans votre instance HA, mais peuvent naturellement contenir des informations domestiques. Ne joignez pas ces données, les journaux complets ou les liens d’albums à une issue publique. Un album Google Photos partagé est accessible aux détenteurs de son lien ; ce n’est pas un stockage privé garanti.

## Signature et publication

La vérification facultative des mises à jour interroge uniquement l’API publique du dépôt officiel via HTTPS, sans authentification ni identifiant d’appareil. Les redirections sont refusées, les réponses limitées à 512 Kio, les versions et adresses validées ; les brouillons, préversions et APK de test/non signés sont exclus par leur nom. Cette vérification porte sur les métadonnées de publication, **pas sur les octets ni la signature d’un APK téléchargé**. Le bouton ouvre une page officielle : Android reste responsable de la vérification de signature lors de l’installation. Aucun téléchargement ou remplacement silencieux n’est effectué. Les contrôles automatiques sont désactivés par défaut et ne déclenchent aucun réveil.

Le diagnostic ne filtre pas un journal potentiellement sensible : il construit un nouveau document à partir d’une liste fermée de booléens, états techniques prédéfinis et versions validées. Aucune préférence complète, exception, capture ou information domotique n’est exportée. Le partage et l’enregistrement sont explicites, après aperçu. Les informations de connexion HA sont le dernier état observé, sans lecture ni déchiffrement du jeton. Les tests injectent de fausses données privées pour vérifier leur absence du rapport.

L’APK est **signé**, pas rendu indéchiffrable. Le code reste inspectable conformément à sa licence. Conserver la même clé privée de signature permet des mises à jour sans désinstallation. Modifier le mot de passe du coffre ne change pas cette identité. Perdre la clé peut empêcher les mises à jour des installations existantes.

Conservez le coffre de signature et ses mots de passe hors du dépôt, avec permissions restreintes et une sauvegarde chiffrée hors ligne. N’utilisez jamais une clé de débogage comme clé publique de distribution. Les pipelines de validation utilisent uniquement une clé de test temporaire pour un package distinct.

## Signaler une vulnérabilité

Utilisez l’onglet **Security → Report a vulnerability** du dépôt lorsqu’il est disponible. Ne publiez aucun jeton, adresse personnelle ou preuve contenant les données d’une maison. Si le signalement privé n’est pas proposé, ouvrez seulement une issue demandant un canal privé, sans détail exploitable ni secret.
