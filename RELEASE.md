# Releasing Fitt

Use [CONTRIBUTING.md](CONTRIBUTING.md) for SDK setup, builds and tests.

## Prepare and publish

Finish validation, update `.github/release-notes.md` and commit app changes first.
Start from a clean checkout and choose a newer `major.minor.patch` version:

```powershell
python scripts/release.py prepare 0.1.2
git diff -- version.properties
```

`prepare` increments `versionCode` and updates `versionName`. Complete the signed
build and upgrade checks below before publishing, then commit and tag:

```powershell
git add version.properties
git commit -m "Release 0.1.2"
git tag v0.1.2
git push origin main v0.1.2
```

The tag must match `version.properties`. Every release needs a higher versionCode.
Never reuse a tag or replace a published APK; publish a new version for fixes.
The tag workflow tests, lints, builds both signed variants, checks signatures and
package metadata, verifies versionCode against the latest release, and publishes
`fitt.apk`, `fitt-legacy.apk`, `update.json` and `SHA256SUMS.txt`.

## Signing configuration and recovery

Keep the application ID `io.github.nottarun7.fitt` and both existing signing keys.
The standard and compatibility APKs have separate stable certificates. Never
rotate or substitute either key, or switch channels on an existing installation.
Updates require the matching certificate and a higher versionCode.

Gradle reads `~/.fitt/signing/signing.properties`, or the file selected by
`FITT_SIGNING_PROPERTIES`. Its property names match these environment variables;
non-empty environment values take precedence:

| Standard channel | Compatibility channel |
| --- | --- |
| `FITT_RELEASE_STORE_PATH` | `FITT_LEGACY_STORE_PATH` |
| `FITT_RELEASE_STORE_PASSWORD` | `FITT_LEGACY_STORE_PASSWORD` |
| `FITT_RELEASE_KEY_ALIAS` | `FITT_LEGACY_KEY_ALIAS` |
| `FITT_RELEASE_KEY_PASSWORD` | `FITT_LEGACY_KEY_PASSWORD` |

Keep an encrypted backup of both keystores, aliases, passwords and configuration
outside the repository. To recover another build environment, restore that backup
and configure the paths above. A new key cannot update existing installations.
Never commit signing material, print secret values or upload them as release assets.

GitHub Actions needs secrets `FITT_RELEASE_KEYSTORE` and `FITT_LEGACY_KEYSTORE`
containing base64 PKCS12 bytes, plus the six password/alias names in the table.
The workflow supplies temporary store paths. Signing secrets are available only
to tag builds, never pull requests. GitHub cannot return saved secret values;
keep a separate recoverable backup.

## Validate and package locally

Configure `ANDROID_HOME` and signing, then run from the repository root:

```powershell
.\gradlew.bat :interchange:test :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:assembleLegacy
python scripts/release.py package
```

On macOS/Linux use `./gradlew`. The package command verifies APK signatures,
package/version and debug flags, then writes the same four release assets to
ignored `dist/`. Verify each certificate matches its previous published channel.

Before tagging, update each channel over its prior APK without uninstalling on a
dedicated test device. Compare routines, drafts, history, nutrition, settings,
photo metadata and exact photo bytes before/after. Test any storage migrations.
