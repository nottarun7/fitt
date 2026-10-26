# Windows setup

## Prerequisites
Install Android Studio: https://developer.android.com/studio
Install JDK 17 (Java 8 is insufficient): https://adoptium.net/temurin/releases/?version=17

In Android Studio SDK Manager, install:
- Android SDK Platform 36
- Android SDK Build-Tools 35.0.0
- Android SDK Platform-Tools
- An emulator image, or use a phone with USB debugging.

Set JAVA_HOME to your JDK 17 folder and add its bin directory to PATH.
Set ANDROID_HOME to the SDK folder, or let Android Studio create local.properties.
Do not commit local.properties.

For an existing installation, run this from PowerShell in the project folder:

```powershell
.\scripts\setup-env.ps1
```

The script checks JDK 17 and the Android SDK, saves user-level `JAVA_HOME` and
`ANDROID_HOME`, adds Java/ADB to the user PATH, and writes ignored
`local.properties`. It can also bootstrap a missing wrapper. For nonstandard
locations, pass `-JavaHome 'C:\path\to\jdk-17' -AndroidHome 'C:\path\to\Sdk'`.
Reopen existing terminals (and Android Studio/Codex if needed) after configuration
to pick up saved environment variables. The script reports missing SDK packages;
the build can download them when the corresponding SDK licenses are accepted.

## Check and build
Run in CMD:
```bat
cd /d C:\Projects\fitt
java -version
git --version
gh --version
gradlew.bat --version
gradlew.bat :interchange:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Baseline: AGP 8.13.2, Gradle 8.13, Kotlin 2.3.0, JDK 17.
No global Gradle installation needed. The first build downloads dependencies.
The repository includes the official Gradle wrapper and its distribution checksum.
`interchange:test` verifies the portable workout schema and JSON codec;
`app:testDebugUnitTest` verifies the catalogue and set-input validation. HTML results
are in each module's `build/reports/tests/` directory, and Android lint results
are in `app/build/reports/lint-results-debug.html`. The developer APK is at
`app/build/outputs/apk/debug/app-debug.apk`.

For a device smoke test, start an installed emulator in Android Studio (or attach
a phone with USB debugging), then run:

```bat
adb devices
gradlew.bat :app:installDebug
adb shell am start -n io.github.nottarun7.fitt/.MainActivity
```

A successful APK build does not verify installation or behavior on a device.

Run the real Room and Compose workflow tests with one emulator/phone connected:

```bat
gradlew.bat :app:connectedDebugAndroidTest
```

Set `ANDROID_SERIAL` if multiple devices are connected. The test runner uses an
isolated in-memory database for UI tests and temporary files for Room tests; it
does not open the normal `fitt.db`. Results are at
`app/build/reports/androidTests/connected/debug/index.html`.

## Environment baseline verified on 2026-10-24

- JDK 17.0.12, Gradle 8.13, Android platform 36, and Build-Tools 35.0.0 configured.
- The portable workout module has 26 behavioral tests. The Android app adds
  catalogue/input unit tests and Room/Compose instrumented tests; run the commands
  above after changes rather than treating the original scaffold smoke test as
  workout-flow verification.
- The dedicated `FittApi36` AVD uses Android 36, Google APIs, and x86_64. Device
  verification to date targets this emulator; other Android versions need release QA.

The Android SDK command-line tools and API 36 emulator image are installed.
`FittApi36` is available in Android Studio's Device Manager for subsequent runs.

## Workout MVP verification on 2026-10-24

- 35 JVM tests passed: 26 portable-document tests and 9 catalogue/input tests.
- 12 instrumented tests passed on Android 36: 10 real Room tests and 2 Compose
  workflows. These cover database reopen, routine-independent history, concurrent
  starts, set correction/deletion, discarding, the exported v1 schema, routine
  creation, activity recreation, finish-to-history, and progression browsing.
- The debug APK assembled and installed. Lint reported no errors; advisory
  dependency/target updates remain visible in the report.
- Independent manual testing force-stopped and reopened the normal app, recovering
  a logged set plus unfinished reps/load, lb units, and assistance selection.
  A timed 20-second set also survived a full stop and appeared in history.
- Light/dark rendering and 130% system text size were inspected on the emulator.
  APK permissions were inspected: there is no Internet permission.

The Room schema starts at version 1. The schema compatibility test verifies this
baseline with existing rows; no upgrade from a previous Room version exists yet.
The portability module's schema version is a separate contract.

## Portability milestone verification on 2026-10-25

Run all JVM, lint, packaging, Room, and UI checks with the emulator connected:

```bat
gradlew.bat :interchange:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest
```

The expanded suite covers JSON/CSV/ZIP round trips, v1 conversion, invalid and
oversized inputs, CSV completeness/escaping, ZIP directory/CRC/hash verification,
duplicate policies, ID remapping, active-session conflicts, exact restore, stale
previews, and transactional rollback after an injected database write failure.
Room's on-disk schema remains version 1; portable files use version 2.

The final run passed 58 JVM tests (49 interchange and 9 app tests) and 26 Android
36 instrumented tests, with no failures or skips. Debug assembly passed. Lint
reported 0 errors and 21 advisory warnings. Imported IDs with path characters or
reserved words were exercised through routine editing, activity recreation, and
history navigation.

The normal app's Android document picker was also exercised on `FittApi36` with
synthetic routines, timed history, and an active input draft. JSON/CSV/ZIP files
were saved to Downloads, copied off the emulator, and checked independently for
their fields, CSV row count, ZIP CRCs, and SHA-256 hashes. Exact restore removed
the synthetic records after confirmation, and the saved ZIP restored the routine,
finished workout, and unfinished workout with its draft. Re-importing the CSV
reported three identical records and no changes. These temporary QA files and
screenshots stay under ignored `.tools/`; they are not app defaults or committed
personal backups.

To check manually, open Train → Backups. Select a saved Fitt file, review its
counts, choose merge/duplicate behavior or exact restore, then inspect the separate
confirmation before applying. Export a backup first if testing an exact replacement
on a device containing data you want to keep.

## Nutrition milestone verification on 2026-10-25

The environment remains configured; no additional service, API key, or paid
dependency is required. The app now has a Nutrition tab with a daily meal diary,
custom foods, weekly diet schedules, and opt-in daily local notifications.

- Room version 2 adds foods, meals, meal items, diet schedules, schedule items,
  and nutrition settings. The explicit 1 → 2 migration validates against the
  exported schema and preserves existing routines, sessions, sets, and input drafts.
  No destructive fallback is configured; version 1's schema remains checked in.
- Portable version 3 includes nutrition in JSON/CSV/ZIP, duplicate previews,
  ID remapping, exact restore, stale-preview detection, and atomic rollback across
  both domains. Fixed v1 and v2 files remain readable. Exact restore from a
  workout-only file removes nutrition; merge preserves unrelated nutrition.
- 68 JVM tests passed (56 interchange and 12 app tests), plus 37 Android 36 tests
  with no failures/skips. The UI/reminder tests were repeated after adding accessible
  switch labels. Debug assembly passed; lint reported 0 errors and 23 advisory
  warnings (dependency/target/resource updates and SharedPreferences style).
- Coverage includes nutrient totals and finite-value/date/time validation,
  historical food snapshots, database reopen, plan-to-meal copying, paused plans,
  food/meal editing, unsaved editor recovery, weekly planning, migration, all
  three backup formats, stale plans, and injected write-failure rollback.
- Reminder tests grant notification permission only to the isolated test app,
  check that an import cannot opt in, verify delivery/one notification per day,
  and verify cancellation. Local-time/DST scheduling has JVM tests. There is no
  exact-alarm or Internet permission. Reminders use a device-only opt-in and
  Android's POST_NOTIFICATIONS permission on Android 13+.

The food library starts empty; enter your own label or recipe values. Synthetic
food fixtures/screenshots used for manual document-picker checks remain in ignored
`.tools/`, never bundled as verified nutrition data. See README for the flows and
the distinction between portable reminder time and device notification consent.

Native manual checks denied and then granted notification permission, verified a
20:00 local inexact alarm, and disabled the reminder afterward. A nutrition ZIP
saved through Downloads was independently checked for hashes/CRCs, all 37 CSV
columns, and all nine synthetic nutrition records. Exact restore from a v2 empty
file removed two foods, one meal, and one plan after confirmation. Restoring the
saved ZIP brought those records back with the expected 285 kcal daily total;
notification opt-in remained off.

## Appearance, Health Connect, and plan imports verified on 2026-10-26

- Settings now provides persistent System/Light/Dark appearance. Theme applies
  throughout the app and the health-data explanation; appearance stays device-local.
- Health Connect 1.1.0 is enabled with only READ_STEPS, WRITE_EXERCISE, and
  WRITE_NUTRITION. Availability checks, partial/denied/revoked permissions, an
  Android permission rationale activity/alias, manual sharing confirmations,
  versioned client IDs, and a separate removal confirmation are implemented.
  There are no background health reads or automatic shares.
- Android 36's real Health Connect provider accepted synthetic exercise/nutrition
  writes. Repeating writes updated the same records; reading only Fitt's data
  origin verified one record per client ID, updated kcal, and version metadata.
  Tests remove only their synthetic client IDs. Foreground step aggregation and
  version persistence through backward clock values were also checked.
- `docs/import-guide.md`, `fitt-plan-example.json`, and `fitt-plan-v1.schema.json`
  are bundled directly as assets and test resources. The app can copy the LLM
  conversion guide/example and save all three files through Android's picker.
  Python JSON Schema validation checked the template and both guide examples;
  the Kotlin importer also validates every example.
- Plan imports use `fitt.plan` v1, always merge after preview, and never fabricate
  completed workouts or eaten meals. Stable source/item IDs handle re-imports.
  Missing nutrition is preserved in text diet instructions; missing meal times
  remain unset. Text plans need actual food values before diary logging.
- Portable backups now export v4 to represent text-only plans and unspecified
  plan times. Existing v1/v2/v3 files remain readable, with fixed v2/v3 archive
  regression fixtures. Room stays at version 2; no table change was needed.
- 79 JVM tests passed (63 interchange, 16 app), along with all 44 Android 36 tests
  in the final full run, with no failures or skips. Debug assembly passed;
  lint reported 0 errors and 16 advisory warnings. The same full validation
  command above remains applicable.
- The final plan-preview regression also checks that Use imported cannot change
  the current reminder time or perform exact restore for a plan. Nutrition editor
  Save is in the fixed header so long forms remain usable with the keyboard open.

Native checks saved the bundled JSON template through Android's picker and verified
its bytes against the standard example. It imported as one routine and one text
diet plan with no completed activity. Dark-mode settings, plan previews, and diet
instructions were inspected at the emulator's enlarged text size. Dark mode also
survived a force-stop and cold restart. The Health Connect system permission screen
was tested with denial and approval, followed by successful manual meal sharing
and confirmed removal of Fitt's shared copies. Local records stayed saved. Merged
APK permissions were inspected: no Internet permission and only the three declared
Health Connect permissions. Screenshots/temporary synthetic files remain ignored
under `.tools/`.

## Progress milestone — October 26, 2026

- Progress has 7/30/90 day workout and nutrition charts with exact daily values,
  per-exercise progression, normalized kg volume, and gaps for unlogged nutrition.
- A private photo timeline supports Android's photo picker, delegated camera,
  date/caption editing, and two-photo comparison. The first-use guide explains
  private storage, independent optional copies in Pictures/Fitt Progress, and
  backup/restore. New images are oriented/resized/re-encoded without original EXIF.
- Private blobs live in app files, outside the phone gallery. Shared copies are
  explicit opt-in and visible to other gallery apps; those apps may upload them
  according to their settings. No Internet or broad photo-read permission was added.
  WRITE_EXTERNAL_STORAGE is limited to API 28 and below, solely for optional copies.
- Room version 3 adds progress_photos through explicit migration 2 → 3. The original
  v1/v2 schema exports remain byte-for-byte unchanged. There is no destructive fallback.
- Portable schema 5 adds photos and photoScope. ZIP manifest v2 streams JPEGs with
  SHA-256/CRC/size checks and validates decoded dimensions before restore. Plain
  JSON/CSV and optional data-only ZIPs preserve existing photos in every mode;
  full photo ZIP exact restore also replaces private photos after preview/confirmation.
  Missing blobs can be repaired from an identical backup. Restored photos never
  auto-publish to the phone gallery. Older v1–v4 backups and plan v1 remain supported.
- Limits: 200 entries, 20 MiB/100 MP source image, 2048 px prepared edge, 5 MiB
  per JPEG, 256 MiB compressed/expanded photo ZIP. Backups remain unencrypted.
- Tests cover chart calculations, archive tampering/layout/legacy compatibility,
  every photo duplicate policy, migration, private persistence/reopen, EXIF removal,
  gallery-copy independence, file repair, rollback/garbage collection, and UI guidance.

Use the full validation command from the earlier milestones with JAVA_HOME pointing
to JDK 17 and ANDROID_SERIAL set to the dedicated test emulator. Native validation
uses synthetic chart records and test pattern images only; temporary fixtures and
screenshots stay ignored under .tools/.

Verification: 95 JVM tests (74 interchange, 21 app) and all 57 Android 36 tests
passed, with no failures or skips. The 7 progress/backup UI tests also passed again
after the final wording/accessibility cleanup. Debug assembly passed; Android lint
reported 0 errors and 23 advisory warnings. Native dark-mode checks used 19
synthetic workouts, 22 meal logs and two test pattern images. Android's picker,
photo date/caption save, explicit gallery-copy consent, the named album, two-photo
comparison, camera launch/cancel and ZIP export/restore were exercised. The saved
ZIP's CRCs, document/image SHA-256 hashes, 42 CSV columns and exact private JPEG
bytes were checked independently. Deleting/restoring a private photo left shared
copies unchanged, even with future gallery copies enabled. Android 8–9's optional
legacy copy path still needs physical-device verification before release.
The saved ZIP also restored both private images, all 19 workouts and all 22 meal
logs into a fresh app installation; their bytes remained identical after a cold
restart. Gallery-copy opt-in remained off on the fresh install and the existing
two shared copies were left untouched.

## GitHub
Authenticate interactively; never share or commit tokens:
```bat
gh auth login --hostname github.com --web --git-protocol https
gh auth status
gh repo view nottarun7/fitt
```

The origin is https://github.com/nottarun7/fitt.git. The user authorized public
source and APK releases. See RELEASE.md for signed releases and updater behavior.
If this folder is not a checkout:
```bat
git init -b main
git remote add origin https://github.com/nottarun7/fitt.git
git fetch origin
git branch -r
```

If origin has commits, reconcile its history/files before committing this scaffold.
Never force-push over existing history. For an empty origin, review, commit, and push main.

## Before public release
Choose an open-source license, finalize the application ID, implement schemas and
tests, prepare a privacy policy, and configure release signing.
Debug APKs from CI are developer artifacts, not signed stable releases.
Never commit signing keys, passwords, health records, or progress photos.

To regenerate the wrapper if it is missing, run `python scripts\bootstrap-wrapper.py`.
Bootstrap needs Python 3 and network access, verifies the wrapper JAR, and pins
the distribution checksum. Keep the generated wrapper files in version control.
