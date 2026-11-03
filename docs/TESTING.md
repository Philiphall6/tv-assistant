# Validation

Les tests n’utilisent aucun compte HA réel ni commande domotique. Les données d’authentification sont des chaînes fictives et les pages web sont interceptées sur des domaines `.invalid`.

- Tests Python : validation YAML/JSON, profils, approbation d’une nouvelle TV, isolation et captures éphémères.
- Tests dans le runtime HA 2026.10.4 : décorateurs d’autorisation, abonnements, refus des écritures non administrateur, stockage et panneau.
- Tests Chromium : éditeur, navigation, placement, vraies surfaces de cartes simulées et dimensionnement.
- Instrumentation Android : disposition, icônes, cartes, protocole temps réel, pont WebView, AES-GCM, migration, mauvais serveur et données chiffrées altérées.
- Régressions Android : fenêtres et vues de l’accueil et du dashboard sans maintien forcé de l’écran, conservation des réglages de veille, reprises synthétiques, images bornées et apprentissage des touches sans lancement d’application réelle.
- Maintenance : métadonnées de versions synthétiques, refus des URL et préversions non fiables, vérification automatique désactivée par défaut, rapport à liste fermée testé avec des données privées fictives, et écran de diagnostic réel sans appel réseau.
- Fond intégré et grille Applications : conservation des choix existants, défaut des nouvelles installations, géométrie des grandes icônes avec toutes les dimensions de grille et validation du schéma HA.

Le manifeste accepte Android 11–16. Le workflow [Android](https://github.com/Philiphall6/tv-assistant/actions/workflows/android.yml) exécute les bornes **API 30 et API 36** sur des paquets isolés. Le workflow [Validate](https://github.com/Philiphall6/tv-assistant/actions/workflows/validate.yml) vérifie HACS, Hassfest, le runtime HA, les navigateurs, les traductions et la documentation. Aucun contrôle HACS ou Hassfest n’est ignoré. Consultez le résultat du commit concerné : la présence d’un workflow n’atteste pas sa réussite. Les variantes de firmware, les touches constructeur et toutes les cartes HACS ne sont pas couvertes.

## Vérification de la première publication — 3 novembre 2026

| Contrôle | Résultat local |
| --- | --- |
| Android 11 / API 30, émulateur Google APIs | Suite d’instrumentation complète réussie |
| Android TV 16 / API 36, émulateur TV | Même suite réussie et ouverture de l’accueil vérifiée |
| Android 12, 13, 14 et 15 | Plage couverte par le manifeste, pas de test d’exécution distinct effectué pour cette publication |
| Validation Python | 22 tests réussis |
| Home Assistant 2026.10.4 | 7 tests runtime réussis |
| Éditeur et cartes Chromium | Deux suites réussies |
| Validateur officiel hassfest | 1 intégration valide, aucune erreur |
| Validateur HACS sur le dépôt public | 10 contrôles réussis ; dépôt personnalisé, pas une admission au catalogue par défaut |
| Gitleaks 8.30.1 | Aucun secret détecté dans l’arbre public et son historique neuf |

Le contrôle de confidentialité de l’APK et la conservation de l’empreinte du certificat de signature ont également été vérifiés. L’installation de l’ancienne version 2.11.0 puis sa mise à jour par l’APK de production 2.12.0, sans désinstallation, ont réussi sur l’émulateur Android 11. Ces résultats ne constituent ni un audit indépendant ni une garantie de sécurité absolue.

```sh
python3 tests/test_ha_panel.py
pip install playwright
playwright install chromium
python3 tests/test_panel_browser.py
python3 tests/test_cards_browser.py
python3 tests/test_localization_browser.py
python3 tests/test_localization_catalog.py
python3 tests/test_docs.py
python3 scripts/generate-localization.py --check
docker run --rm --network none -v "$PWD:/work" -w /work ghcr.io/home-assistant/home-assistant:2026.10.4 python tests/ha_component_runtime.py
```

`CHROME_BIN` permet d’utiliser un Chromium déjà installé. Le mode audit interdit le remplacement du launcher personnel et la suite refuse un package cible qui ne se termine pas par `.audit`.

Les tests de langue vérifient la priorité du choix HA sur le navigateur, les variantes régionales, le repli anglais, la conservation du brouillon et l’absence de traduction des noms personnalisés. L’instrumentation Android vérifie les ressources, les salutations, les états, les nombres et la langue vocale automatique.
