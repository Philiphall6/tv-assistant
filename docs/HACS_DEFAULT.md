# HACS default catalog preparation

TV Assistant is currently installable as a **custom integration repository**. Preparing and passing validation is not the same as being accepted into the default catalog.

The process follows the [official HACS inclusion requirements](https://www.hacs.xyz/docs/publish/include/). The repository provides the integration manifest, HACS manifest, config flow, translations, brand icon, GPL license, installation documentation and dedicated HACS/Hassfest validation jobs.

## Before submitting

1. Open the [Validate workflow](https://github.com/Philiphall6/tv-assistant/actions/workflows/validate.yml). Confirm that both **HACS** and **Hassfest** passed on the candidate commit, without ignored checks.
2. Confirm the integration/browser tests and the [Android tests](https://github.com/Philiphall6/tv-assistant/actions/workflows/android.yml) passed. Review public files, release assets and signing-certificate continuity.
3. Create a normal, non-draft release **after** the successful validation runs. Link the exact release and successful runs in the submission.
4. As the repository owner or a major contributor, fork `hacs/default`, create a new branch from `master`, and add `Philiphall6/tv-assistant` to the alphabetically sorted `integration` list.
5. Complete the upstream checklist truthfully and submit the pull request with maintainer edits enabled. Do not request reviews or claim acceptance before it is merged.

The repository must remain public. Inclusion and review timing are decided by HACS maintainers. No upstream pull request is created merely by adding these files.

## Submission checklist

Use the current template from `hacs/default`, not a stale copy. It asks for the publishing documentation to be read, HACS and Hassfest Actions to be present and passing, links to their successful runs, and a release created after those runs.

The stable integration domain is `home_assistant_tv`. Check that it does not conflict with a Home Assistant core integration when submitting. The Android APK is distributed separately in GitHub Releases; HACS does not install it on TVs.
