# Compilation et signature

La compilation utilise les outils Android officiels, sans téléchargement de dépendances pendant le script. Les bibliothèques incluses sont inventoriées dans `libs/SHA256SUMS`.

## Prérequis

- Linux, Bash, Python 3, JDK 21 (bytecode Java 17).
- SDK Android : `platforms;android-36`, `build-tools;36.1.0`, platform-tools pour les tests.

```sh
export ANDROID_SDK_ROOT=/chemin/vers/android-sdk
export JAVA_HOME=/chemin/vers/jdk-21
sha256sum -c libs/SHA256SUMS
./build-apk.sh release
```

Le résultat dans `release/` est **non signé**. `minSdkVersion=30`, `targetSdkVersion=36`. Une compilation réussie ne démontre pas à elle seule le fonctionnement sur tous les fabricants.

## Clé privée hors dépôt

Si une application est déjà distribuée, **réutilisez sa clé de signature**. Le script ci-dessous importe un coffre existant vers un nouveau coffre PKCS12 protégé par un mot de passe aléatoire, en conservant la clé et son certificat. Il ne supprime ni ne modifie le coffre source.

```sh
./scripts/protect-signing-key.sh /chemin/prive/ancien.jks alias /chemin/prive/mot-de-passe-coffre /chemin/prive/mot-de-passe-cle /chemin/prive/nouveau-dossier
```

Les fichiers sources de mot de passe doivent être créés de manière privée, jamais dans le dépôt ni dans l’historique du terminal. Le dossier destination doit être nouveau. Protégez et sauvegardez le nouveau coffre ainsi que son mot de passe hors ligne. L’ancienne copie reste sensible et doit être conservée en lieu sûr ; la création du nouveau coffre ne sécurise pas rétroactivement ses copies.

Pour un nouveau fork jamais distribué, créez votre propre clé avec `keytool -genkeypair` et un nom de certificat ne contenant aucune donnée personnelle. Ne réutilisez pas la clé d’un autre projet.

```sh
export TVA_SIGNING_KEYSTORE=/chemin/prive/signing.p12
export TVA_SIGNING_ALIAS=release
export TVA_STORE_PASSWORD_FILE=/chemin/prive/password
export TVA_KEY_PASSWORD_FILE=/chemin/prive/password
./scripts/sign-apk.sh release/TVAssistant-2.13.0-release-unsigned.apk release/TVAssistant-2.13.0.apk
```

Ces fichiers doivent avoir le mode `600` ou `400`, hors de ce dépôt. Le script refuse d’écraser un résultat signé existant. Vérifiez l’empreinte SHA-256 du certificat avec une précédente version avant publication. Ne confondez pas l’empreinte du fichier APK et celle du certificat.

## Validation isolée

`./build-apk.sh audit` produit le package `fr.maison.tvlauncher.audit` sans rôle HOME. `tests/compile-panel-tests.sh CHEMIN_BUILD_AUDIT` compile l’instrumentation. `tests/run-android.sh` construit, signe avec une clé temporaire et exécute la suite sur un émulateur explicitement choisi. N’utilisez pas ces tests sur une TV personnelle.

Le dépôt public est créé avec un historique neuf. Avant toute publication : tests, vérification des fichiers suivis et des artefacts, recherche de secrets, contrôle du certificat et publication **uniquement** de l’APK de production signé, jamais du coffre, de sa clé ou du package de test.
