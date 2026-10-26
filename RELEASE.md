# GitHub downloads and updates

Repository: https://github.com/nottarun7/fitt

- Standard APK: https://github.com/nottarun7/fitt/releases/latest/download/fitt.apk
- Compatibility APK: https://github.com/nottarun7/fitt/releases/latest/download/fitt-legacy.apk
- Release page: https://github.com/nottarun7/fitt/releases/latest

The standard APK is for new users. The compatibility APK updates the early
development builds previously shared from this project without losing local data.
Both have the same application ID, independent stable signing certificates, and
debugging disabled. Never alternate between the two APKs on an existing install.
The app chooses updates by its **installed signing certificate**, not filenames
or version names. Unknown development certificates are not offered an incompatible
update. Do not uninstall to update; device migration uses ZIP backup/restore.

Android controls the final installer wording. Matching application ID and signing
key plus a higher versionCode let Android update the existing app and retain its
data. We cannot bypass Android's confirmation or the phone's unknown-source setting.
See [Android signing](https://developer.android.com/studio/publish/app-signing)
and [versioning](https://developer.android.com/studio/publish/versioning).

## Publish the next version

From a clean checkout, with your normal GitHub authentication:

```powershell
python scripts/release.py prepare 0.1.1
git diff -- version.properties
git add version.properties
git commit -m "Release 0.1.1"
git tag v0.1.1
git push origin main v0.1.1
```

The prepare command raises versionCode automatically and requires a newer
major.minor.patch version. Review and commit app changes before running it. The
tag must match version.properties. GitHub Actions runs JVM tests/lint, builds both
signed APKs, independently checks their certificates/package/version/debug flags,
creates update.json and SHA256SUMS.txt, checks that versionCode increases, then
publishes the release. Failed validation publishes nothing. Never reuse a tag or
replace an existing release's APK. Advance the version for corrections.

The main/PR workflow runs checks without signing secrets. Only the tag-release
workflow gets signing material; pull requests do not receive it. Repository
administrators can view secret names under Settings → Secrets and variables →
Actions, but cannot retrieve their values from GitHub.

## Signing keys: keep a private backup

On the maintainer's original Windows computer, keys and signing.properties are
stored outside this checkout under **%USERPROFILE%\.fitt\signing**. They are also
stored as encrypted GitHub Actions secrets. These are required for future updates;
GitHub secrets are not a recoverable key backup. Keep a separate encrypted backup
of that directory. Do not commit it, upload it as a release asset, or share passwords.
The compatibility key was imported from the original Android development key and
reprotected with a random password; the standard release key is separate.

For a local signed build, Gradle reads that private configuration or a file selected
with FITT_SIGNING_PROPERTIES. CI uses these environment variables (RELEASE and
LEGACY each): FITT_<channel>_STORE_PATH, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
The encrypted GitHub secrets additionally include FITT_RELEASE_KEYSTORE and
FITT_LEGACY_KEYSTORE containing base64 PKCS12 bytes. Never print these values.

```powershell
.\gradlew.bat :interchange:test :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:assembleLegacy
python scripts/release.py package
```

The ignored dist/ folder contains the exact four assets published by CI. The
package script verifies signatures and metadata instead of trusting filenames.

## In-app update behavior and privacy

On foreground launch, Fitt checks the fixed HTTPS GitHub update feed at most once
per day. Failed/offline checks stay quiet and all local features keep working.
Settings provides manual check, installed version, release notes and an automatic
check toggle. The prompt offers Update, Later (a day), and Skip version. Manual
checks can show a skipped version again. Only a higher versionCode, compatible
Android version and matching signing certificate produce a download prompt.

The check sends an ordinary request to GitHub with the app version in User-Agent.
GitHub/CDN providers can observe normal connection metadata such as IP address.
No workouts, meals, photos, backup contents, account IDs or device identifiers are
sent. Downloads open in the user's browser; Android verifies the signed APK and
asks the user to confirm installation/update. There is no silent installer,
background APK download, user account, app server or embedded GitHub credential.

Update preferences, dismissal choices and check timestamps remain device-local
and are excluded from portable health-data backups. Android cloud backup remains
disabled. This change adds INTERNET permission; earlier no-network statements in
historical milestone logs describe older builds.
