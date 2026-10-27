# Contributing

Keep changes focused, preserve existing user data and retain offline operation.

## Build and test

Install JDK 17 and the Android SDK with platform 36, build-tools 36.0.0 and
platform-tools. Set `JAVA_HOME` to JDK 17. Configure the SDK through
`ANDROID_HOME` or an untracked `local.properties` file.

Use the included Gradle wrapper. From the repository root on Windows:

```powershell
.\gradlew.bat :interchange:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Run device tests on a dedicated Android 36 emulator with synthetic data. The test
tasks install and remove Fitt:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

On macOS or Linux, use `./gradlew` in place of `.\gradlew.bat`. If several
devices are connected, select one with `ANDROID_SERIAL`. Do not run competing
Gradle builds against the same checkout.

The `app` module contains the Android UI, local storage and integrations.
`interchange` contains portable models, validation and import/export logic;
keep it independent of Android, Room and Compose. Pin dependencies in
`gradle/libs.versions.toml`.

## Preserve data and update compatibility

- Keep the application ID `io.github.nottarun7.fitt` and each release channel's
  signing certificate stable. Every published version needs a higher
  `versionCode`. See [RELEASE.md](RELEASE.md) for signed builds and publication.
- Never reset user storage, seed example user records at startup or use a
  destructive Room migration fallback. Database changes require explicit,
  tested migrations that retain records, IDs and relationships.
- Treat unfinished workout input, completed history, nutrition records, settings
  and private photo files as user data. Preserve photo metadata and the files
  referenced by it. An interface change must not rewrite these stores.
- Maintain the supported import and backup contracts. Data-only restores must
  preserve existing photos; destructive exact-restore choices require a clear
  preview and confirmation.
- Before a release, test a real signed APK update over the matching previous APK
  without uninstalling. Compare a populated dataset and exact photo bytes before
  and after the update.

Add behavioral tests for changed flows, validation, imports and migrations.
For UI changes, check light and dark mode, larger text, keyboard visibility,
focus, back navigation and touch targets. Use existing test tags consistently.

## Privacy and review

Use synthetic test fixtures. Do not commit personal health records, photos,
private backups, signing keys, passwords or tokens. Keep local SDK and signing
configuration out of Git.

Core tracking must work without an account or network access. The only automatic
network request is the user-controllable GitHub update check; never attach
workouts, meals, photos, backup contents or device identifiers to it. External
links and optional Health Connect sharing remain user-initiated.

Describe the behavior changed, why it changed and how it was verified in each
pull request. Include relevant screenshots for visual changes and call out any
storage or compatibility implications.

Source licensing has not yet been selected. Preserve existing third-party
license notices, including the bundled font licenses.
