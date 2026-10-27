# Fitt

Fitt is an Android app for workout routines, set logging, nutrition and progress
photos. Core tracking works offline without an account.

## Download and update

**[Download Fitt for Android](https://github.com/nottarun7/fitt/releases/latest/download/fitt.apk)**
| [Release notes and all downloads](https://github.com/nottarun7/fitt/releases/latest)

Requires Android 8.0 or newer. Open the downloaded APK on your phone and approve
Android's installer. Allow installation from your browser or file manager if asked.

Use the same APK family when updating:

- **Standard install:** use `fitt.apk`.
- **Early development or compatibility install:** use the
  [compatibility update](https://github.com/nottarun7/fitt/releases/latest/download/fitt-legacy.apk).

Open the matching newer APK and choose **Update**. **Do not uninstall Fitt to
update:** a matching signed update keeps your saved data. The in-app updater
selects the compatible APK automatically.

Fitt checks for updates once a day when opened. Settings includes a manual check
and an automatic-check toggle. Update prompts wait during workouts and text entry.
Release checksums are available in `SHA256SUMS.txt` on the release page.

## What you can do

- **Train:** create routines, choose from 202 exercises or add your own, and log
  reps, timed sets, load and assistance. Correct sets, resume unfinished workouts
  and review completed sessions.
- **Track nutrition:** save custom foods, log meals and servings, review calories
  and macros, and create weekly diet schedules with optional daily reminders.
- **See progress:** view workout and nutrition charts over 7, 30 or 90 days.
  Add dated progress photos, write captions and compare two photos.
- **Keep your data:** export and import routines, workout history and nutrition
  through JSON, CSV or ZIP, with validation and duplicate choices. ZIP backups
  include private photos by default.
- **Choose your setup:** use system, light or dark appearance. Health Connect is
  optional for manually sharing completed workouts and logged meals, or viewing
  today's steps.

## Storage and privacy

Routines, unfinished input, history, nutrition and private photos are stored on
your device. There are no ads, analytics or mandatory accounts. Update checks
contact GitHub using normal connection metadata and the app version; no workouts,
meals or photos are uploaded. Health Connect sharing requires your action and
permission.

Progress photos are private to the app by default. Optional copies in
`Pictures/Fitt Progress` are visible to phone-gallery apps and may follow those
apps' cloud-backup settings. Private entries, original images and gallery copies
are independent; edits or deletions do not sync between them. Private app storage
is not a locked album.

Export a **ZIP with photos** before clearing app data, uninstalling or moving to
another phone. Android cloud backup is disabled. Data-only exports, including
Fitt's JSON/CSV exports, preserve existing photos when restored. A full ZIP exact
restore can replace them, so review its preview. Restored photos remain private and do not create gallery
copies automatically. Exported backups are not password-protected.

## Guides

- [Import workout and diet plans](app/src/main/assets/import-guide.md), with a
  [JSON example](app/src/main/assets/fitt-plan-example.json) and
  [JSON Schema](app/src/main/assets/fitt-plan-v1.schema.json). PDF and document plans must first
  be converted into the documented format.
- [Build, test and contribute](CONTRIBUTING.md).
- [Publish releases and manage signing](RELEASE.md).
- [Portable backup format](interchange/README.md).

Source licensing has not yet been selected. Bundled fonts retain their
[Barlow](app/src/main/assets/third-party/barlow-OFL.txt) and
[Barlow Condensed](app/src/main/assets/third-party/barlowcondensed-OFL.txt) license notices.
