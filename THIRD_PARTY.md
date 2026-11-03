# Composants tiers et marques

Le code original du projet est sous GPL-3.0-only. Les dépendances conservent leurs licences et notices :

| Composant | Version | Licence / source |
| --- | --- | --- |
| Java-WebSocket | 1.6.0 | MIT, [source](https://github.com/TooTallNate/Java-WebSocket), notice dans `app/src/main/res/raw/java_websocket_license.txt` |
| SnakeYAML | 2.7 | Apache-2.0, [source](https://bitbucket.org/snakeyaml/snakeyaml), notice dans `app/src/main/res/raw/snakeyaml_license.txt` |
| SLF4J API | 2.0.17 | MIT, [source](https://github.com/qos-ch/slf4j), notice dans `app/src/main/res/raw/slf4j_license.txt` |
| AndroidX WebKit | 1.18.0 | Apache-2.0, [source](https://android.googlesource.com/platform/frameworks/support/), notices dans `libs/` |
| AndroidX Core / Annotation | 1.1.0 | Apache-2.0, même source et notices |
| Material Design Icons / Pictogrammers | fichiers inclus | Pictogrammers Free License (icônes/police Apache-2.0, code MIT), notice dans `app/src/main/res/raw/mdi_license.txt` |

`libs/SHA256SUMS` permet de vérifier les JAR inclus. Les fichiers AndroidX proviennent des classes de leurs artefacts AAR lorsque nécessaire.

Le logomark Home Assistant affiché pour la connexion et la barre est issu de [OpenHomeFoundation/brand-assets](https://github.com/OpenHomeFoundation/brand-assets). Il appartient à l’Open Home Foundation et reste soumis à ses [directives de marque](https://brands.openhomefoundation.org/). Aucun droit de marque n’est accordé par la GPL ; ce projet n’est ni officiel ni approuvé par la fondation. Le nom Google TV et les icônes des applications installées appartiennent à leurs détenteurs.

Le fond « Salon zen » inclus est une illustration d’ambiance, pas une photographie de l’installation d’un utilisateur.

L’identité d’installation TV Assistant représente une télévision blanche et une branche connectée, inspirée du motif de connexion Home Assistant. Ses fichiers vectoriels et PNG sont dans `custom_components/home_assistant_tv/brand/` ; les variantes Android sont `installation_icon.xml` et `tv_banner.xml`. Cette identité ne remplace pas le logomark utilisé à l’intérieur du launcher et n’implique aucune affiliation officielle.

Le texte « TV Assistant » utilise les contours de Noto Sans Regular, police du [projet Noto](https://github.com/notofonts/latin-greek-cyrillic) sous SIL Open Font License 1.1. Les visuels contiennent uniquement les contours du texte ; aucun fichier de police Noto n’est distribué dans l’APK.
