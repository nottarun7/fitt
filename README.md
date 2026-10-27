# Fitt

An offline-first Android workout and nutrition tracker in development.
No mandatory accounts, subscriptions, paid APIs, ads, or analytics.

## Download Fitt for Android

**[Download the latest APK](https://github.com/nottarun7/fitt/releases/latest/download/fitt.apk)**
· [Release notes and all downloads](https://github.com/nottarun7/fitt/releases/latest)

Android 8 or newer. Download `fitt.apk`, open the file on your phone, and approve
Android's installer. If asked, allow installation from your browser/file manager.
Already installed from a previous GitHub release? Opening the newer APK updates
the existing app and keeps your data. **Do not uninstall to update.**

**Using an early development APK from this project?** Download
**[the compatibility update](https://github.com/nottarun7/fitt/releases/latest/download/fitt-legacy.apk)**
instead. It preserves that build's signing key and your local data. The app's
future update prompts select the correct APK automatically. Both APKs are release
builds with debugging disabled. New users should choose the standard `fitt.apk`.

Fitt checks GitHub once per day when opened and prompts when a compatible newer
version exists. **Update** downloads the matching APK in your browser; open it to
confirm Android's update. Settings offers **Check for updates** and an automatic
check toggle. Core tracking works offline. For checksums, see `SHA256SUMS.txt` on
the release page. Keep ZIP backups, including photos, for device migration.

**Status: offline workouts, nutrition, and portable backups implemented.** Create and
edit routines, log sets, resume unfinished sessions, and browse workout history.
Export/import JSON, CSV, or ZIP with validation, duplicate choices, and exact
restore. Log food and meals, build weekly diet schedules, and optionally enable a
daily reminder. Choose System/Light/Dark appearance, optionally connect Health
Connect, and import plans converted by an external LLM. Progress includes workout
and nutrition charts, a private photo timeline, optional phone-gallery copies,
and photo-inclusive ZIP backups.

Version 0.1.1 refreshes the native light/dark interface and keeps **Finish** out of
the set-entry area. While typing, **Done** dismisses the keyboard; **Log set**
records the set. Finishing still needs confirmation and warns about unlogged
entries. See the [interface design notes and references](docs/ui-design.md).

See [SETUP.md](SETUP.md) for Windows/CMD setup.

- `app`: Kotlin and Jetpack Compose Android application (Android 8+).
- `interchange`: portable data boundary, independent of Android and Room.
- `gradle/libs.versions.toml`: pinned dependencies.

The `interchange` module contains versioned workout documents, validation,
JSON/CSV/ZIP codecs, legacy migration, and duplicate planning with behavioral tests.
Room/KSP power the local database. Health Connect supports optional, manual sharing
of completed workouts and logged meals, plus a foreground display of today's steps.
The Internet permission is used only for GitHub update checks; no workout,
nutrition or photo data is sent. Android cloud backup is disabled; portable files
are created only in a location selected by the user. Personal PDFs are examples,
never product defaults.

No backend or automatic Instagram photo retrieval.

Source and signed APK releases are public on GitHub. An open-source license has
not yet been selected; public visibility alone does not grant an open-source license.
Release maintenance and signing recovery are documented in [RELEASE.md](RELEASE.md).

## Project plan

1. **Reproducible build and portable workout foundation (implemented).** Check the
   Windows toolchain, include the verified Gradle wrapper, define routines and
   workout snapshots, and test validation plus versioned JSON round trips.
   Exit: unit tests, Android lint, and debug APK assembly pass locally.
2. **Offline workout MVP (implemented).** Add Room entities, exported database schemas, a
   repository, and ViewModels. Build editable routines, start/resume a workout,
   log sets/reps/weight, finish sessions, and browse history. Persist immediately
   so interrupted sessions survive app restarts. Test persistence and migrations
   without destructive fallback.
3. **User-controlled data portability (implemented).** Connect JSON export/import through
   Android's document picker. Preview validation and duplicate conflicts before
   a transactional import; add CSV and ZIP backups with restore tests. Keep
   schema migrations explicit and reject unsupported versions without changes.
4. **Nutrition and daily planning (implemented).** Add optional local food/meal logging, custom
   foods, diet schedules, and opt-in reminders. No account or paid food API.
5. **Progress (implemented).** Workout/nutrition charts over 7/30/90 days, private
   photos with dates/captions and two-photo comparison, guided optional copies in
   Pictures/Fitt Progress, and verified photo-inclusive ZIP backup/restore.
6. **Release readiness.** Check accessibility, Android 8+ and current Android
   behavior, restore paths, and privacy settings; select a license, finalize the
   app ID, prepare a privacy policy, and configure release signing.

The first usable milestone is step 2. Each step should be verified before moving
on; nutrition and health integrations do not block workout logging.

## Using the workout MVP

- **Train:** create a named routine, add catalogue or custom exercises, change
  target sets/reps/seconds, reorder or remove exercises, then save. Empty routines
  can be saved as drafts. Start a saved routine or a freestyle workout.
- **In progress:** add exercises, log reps or seconds, and optionally record a
  kg/lb load. Mark assistance separately from added weight. Correct or delete
  logged sets before finishing. Targets are editable planning values, not limits.
- **Recovery:** sets and unfinished input save to Room. Reopen the app and tap
  Resume workout. One active session is allowed at a time; backing out keeps it.
  Finish saves to history; discarding requires confirmation and removes only the
  unfinished session.
- **History:** inspect completed sessions, dates, duration, and each recorded set.
  Sessions keep copies of exercise names, order, targets, and tracking types, so
  later routine edits/deletion do not rewrite history. Finished records are read-only.

## Exercise catalogue and references

The offline library contains **202 movements**: 62 machine/Smith movements, 30
cable movements, 24 free-weight movements, and 86 calisthenics/bodyweight movements.
Search by name, muscle, or equipment and filter by muscle, equipment group, or
calisthenics progression family. Common spellings such as `pushups`, `legpress`,
and `abs` are supported. Custom exercises cover equipment and brand variants.
This is broad strength-training coverage, not an exhaustive manufacturer inventory.

There are **13 progression families**: push-up, pull-up, squat, dip, handstand,
handstand push-up, L-sit, leg raise, front lever, back lever, muscle-up, planche,
and bridge. Timed skills use seconds; unilateral movements label values per side.
These are suggested variation sequences with overlapping difficulty, not an
automatic advancement program.

Twenty unique external guide pages from [ACE](https://www.acefitness.org/resources/everyone/exercise-library/),
[Hybrid Calisthenics](https://www.hybridcalisthenics.com/routine), and
[GMB Fitness](https://gmb.io/rings/) are linked where relevant. Exercise metadata
and notes are authored for Fitt; third-party articles/images are not bundled.
References open in the user's browser only after a tap. Unavailable references
and **all image fields remain empty**; there are no automatic media requests.

## Using nutrition

Open the **Nutrition** tab; all features work offline with no food API or account.

- **Foods:** create/search/edit custom foods. Specify a serving label (such as
  `100 g` or `1 bowl`) and calories, protein, carbs, and fat per serving, using your
  label or recipe values. Zero is valid; missing values are rejected. The library
  starts empty so no estimated food values are presented as verified facts.
- **Diary:** choose a local date, log named meals with one or more foods, set decimal
  servings, and add notes. Daily calories/macros update from the logged portions.
  Edit or delete meals on any date. Unsaved editor input survives activity recreation.
  Each meal stores its food/nutrient snapshots, so editing/deleting a food preserves
  historical totals. Display rounds to one decimal; stored values keep precision.
- **Plans:** build weekly meal schedules with weekdays, local time, portions, notes,
  and an active/paused switch. Matching plans appear in the date's diary. Logging a
  planned meal creates an independent entry; each tap adds a meal. Editing a plan
  does not rewrite meals already logged. Plans never log meals automatically.
  Text-only plans can preserve diet instructions without inventing nutrition.
  Their time can remain unset; add foods with known nutrition before logging them.
- **Reminders:** set a daily local time and explicitly enable the reminder. Android
  13+ notification permission is requested only from this action. Denying permission
  leaves it off; Android notification settings are linked for later changes. A
  blocked notification channel pauses delivery. Disabling cancels the alarm and
  notification. Notifications open Nutrition. One-shot inexact alarms are recalculated
  after delivery, app start, reboot, time-zone/clock changes, and package updates;
  delivery may be delayed by Android battery restrictions. Force-stopping the app
  stops reminders until the app is opened again. No exact-alarm permission is used.

Reminder time is portable. Device notification consent is never imported or enabled
by a backup. Meals are personal records, not nutrition prescriptions or generated goals.

## Appearance

Open Train → Settings (gear icon) → Appearance and choose System, Light, or Dark.
The choice applies across the app, including editors, import previews, dialogs,
and the Health Connect explanation. It survives app restarts and stays local to
this device; importing a plan or backup does not change it.

## Health Connect

Open Train → Settings → Health Connect. Availability is checked before connecting.
Android 9+ is needed for Health Connect; older supported Android devices can keep
using the offline app. On Android 14+ the provider is integrated with Android;
earlier compatible versions may need the separate provider app. See the
[Android setup guide](https://developer.android.com/health-and-fitness/health-connect/get-started).

Fitt requests only write exercise sessions, write nutrition, and read steps. Each
type is optional and permissions can be changed or revoked in Health Connect.
Fitt checks access before operations and handles partial grants or lost access.
There are no background health reads or automatic shares.

- **Steps:** the Health Connect screen shows today's aggregate in the current
  local zone. Steps are not copied into Fitt storage or backups.
- **Share:** review and explicitly share completed workouts and/or logged meals.
  Workouts publish a strength-training session with title and duration. Detailed
  reps, load, assistance, and targets stay in Fitt. Meals publish their stored
  calorie/macro totals. Because the diary stores dates rather than meal clock
  times, meal records use midnight local time and a one-second interval as a date
  placeholder, not a claimed meal time. Offsets use the current device time zone.
  Active workouts, routine/diet templates, future records, zero-duration workouts,
  and out-of-range provider values are skipped, with counts reported.
- **Repeat sharing:** stable client IDs and increasing per-device versions update
  entries instead of duplicating them. If a batch fails, retry is safe. A restored
  backup keeps local IDs; copying a changed duplicate intentionally creates new IDs.
- **Removal:** deleting a local entry does not automatically delete a shared copy.
  A separately confirmed action removes Fitt's exercise/nutrition copies for
  allowed write types. Health Connect restricts this deletion to Fitt's records;
  other apps' records and local Fitt data stay saved. You can also manage/revoke
  access in Health Connect.

The permission rationale is available from the connection screen and from
Android's health-permission UI. Health permissions, device sharing version counters,
and theme settings are not included in portable files. GitHub update checks send
no health data; sharing with Health Connect happens on the device. Apps the user allows
in Health Connect may read shared data under their own policies.

## Import a PDF or document plan using an external LLM

Open Settings → Import guide & template, or the guide in Train → Backups. Copy the
prompt into an external LLM alongside your PDF/DOC/DOCX/image or source text.
Review the resulting JSON, save it as `my-plan.json`, and choose it in Backups.
The app does not upload the source document or call an LLM service.

The [standard guide](docs/import-guide.md), [working template](docs/fitt-plan-example.json),
and [JSON Schema](docs/fitt-plan-v1.schema.json) are also bundled for saving/copying
inside the app. They use `fitt.plan`, version 1, with readable stable IDs, routine
targets, labelled food definitions, and weekly diet schedules. Unknown food values
stay in text notes, never fake zeros; unspecified plan times remain unset.

Plan files are validated and always merged after preview/confirmation. They cannot
perform exact restore and cannot create logged meals or completed workout history.
They also preserve the current daily reminder time for every duplicate policy.
Reuse `sourceId` and object IDs for revisions to detect duplicates. Existing backups
continue to use the full backup contract for history and device replacement.

## Storage and tests

The initial Room database schema is exported to `app/schemas/`. Multi-row writes
are transactional, session creation is serialized, and a unique index also
enforces one active session. No destructive migration fallback is configured.
Every future database version must retain existing data through explicit tested
migrations. The baseline schema is checked against existing rows in device tests.

The portable backup format is version 5, including workouts, nutrition and photo
metadata with an explicit photo scope. Room database version 3 adds private photo
metadata through a tested 2 → 3 migration. Version 2 added six nutrition tables
through 1 → 2; both migrations preserve existing data. Portable v1 JSON and v2/v3/v4 JSON/CSV/ZIP backups
are converted explicitly; v1/v2 contain no nutrition. Plan imports use the separate
`fitt.plan` version 1 format described below.

Local JVM checks cover serialization, catalogue integrity/search, and input
validation. Instrumented tests cover real Room persistence/reopen, snapshots,
concurrent starts, corrections, cascading deletes, baseline schema compatibility,
and the Compose create/log/recreate/finish flow. UI tests use a separate in-memory
application database, and database tests use isolated temporary files.

## Import, export, and restore

ZIP includes private photo files by default. Uncheck **Include private photos in
ZIP** for a data-only export. JSON/CSV always export data only. Data-only files and
all older backups preserve existing private photos even during exact restore.
Full ZIP exact restore also replaces private photos; review the removal count.
Restored photos stay private and never create phone-gallery copies automatically.

Open **Train → Backups**. JSON, CSV, and ZIP each contain saved routines, every
finished workout, the current workout with its raw unfinished input, custom foods,
logged meals, weekly diet schedules, and reminder time. Stable
IDs, dates, order/position gaps, catalogue references, custom names, targets,
reps/seconds, per-side flags, kg/lb loads, assistance, and null versus zero loads
are preserved. Nutrition includes saved serving labels, decimal quantities,
nutrient snapshots, local meal dates, notes, weekdays, and plan times. Save editor
changes before exporting. Notification permission and reminder opt-in stay local.

Android's document picker chooses the destination or source. No storage permission
or account is required. Files are unencrypted; the selected document provider may
be local or a cloud provider chosen by the user. There are no automatic uploads.

Import first validates the complete file and displays counts and changed duplicate
records. **Merge** leaves unrelated records intact. Duplicate matching uses IDs,
not names; identical records are skipped. Changed IDs have three choices:

- **Keep existing:** skip the entire matching incoming routine, workout, food, meal, plan, or photo.
- **Use imported:** replace that entire record and its children; also use the imported reminder time.
- **Keep both:** copy the incoming record with new IDs and rewire copied routine
  and food references. Repeatedly copying a changed duplicate deliberately creates more copies.

If both sources have different unfinished workouts, explicitly keep the current
one (skip the imported unfinished session) or use the imported one (remove the
current unfinished session). Finished history is still handled by the selected
merge rules. Child-ID collisions cannot overwrite another parent's records.

**Restore backup exactly** replaces all saved workout and nutrition data with the
selected file, including its unfinished session and reminder time. Its preview lists additions, changes, and
removals; a separate confirmation is required. An empty exact restore can remove
all routines/history/nutrition. Exact restore from an older workout-only backup
also removes nutrition. Merging an older backup keeps unrelated nutrition intact.

All changes commit in one transaction. Validation errors, write failures, and
cancelled confirmations leave existing data intact. If workout or nutrition data changes after
preview, importing is blocked and a new preview must be confirmed. Export validates
the complete payload before writing and reports success only after the stream closes.

ZIP contains `manifest.json`, `backup.json`, `backup.csv`, and selected photo JPEGs
under `photos/<sha256>.jpg`. The manifest checks
SHA-256 hashes, ZIP CRCs are verified, and the JSON/CSV documents must agree.
Unexpected paths/entries, repeated JSON keys, unknown fields/versions, malformed
UTF-8, orphan CSV records, inconsistent CSV counts, invalid values, and oversized
files are rejected. Image files are streamed to private staging, validated by hash,
size and decoded dimensions, then installed before the metadata transaction.
Stale previews or failed writes keep existing metadata and referenced images.
Missing images can be repaired from an otherwise identical ZIP. Unreferenced
private image files are collected after commit or rollback.

The CSV is a documented Fitt layout with metadata and typed record rows, not an
arbitrary third-party spreadsheet format. Quotes, commas, line breaks, Unicode,
and formula-like text round-trip; potentially executable spreadsheet text is
escaped reversibly. Use JSON or ZIP for long-term backups rather than relying on
spreadsheet software to preserve all data types. See `interchange/README.md`.

Limits: 256 MiB per photo ZIP (compressed and expanded), 200 photos, 5 MiB per
prepared JPEG, 12 MiB per plain JSON/CSV file, 10 MiB per JSON/CSV document,
100,000 stored records, 16,384 characters per text field, and 32 JSON nesting
levels. Over-limit files fail before any import, with no partial restore. The
built-in exercise catalogue is bundled with the app; it is not duplicated in backups.

## Progress charts and private photos

Open **Progress** in the bottom navigation. Workouts and Nutrition offer 7, 30
and 90 day ranges; **View values** provides exact daily readings and an accessible
alternative to each chart. Completed workouts count on their local completion date.
Workout charts show sessions, sets, added-load volume and per-exercise best reps/time
and highest added load. Pounds convert to kilograms. Volume counts reps × recorded
added load, excludes assistance/timed/bodyweight sets, and counts per-side sets once
as logged. It does not estimate total effort. Exercise daily maxima can come from
different sets. Assistance and timed variants remain separate.

Nutrition charts show calories, protein, carbs or fat from logged meal snapshots,
scaled by actual servings. Schedules are excluded until logged. Unlogged days are
gaps, not zero intake; averages use logged days only. Partially logged days may
understate intake. No default personal history is bundled.

In **Progress → Photos**, the first-use guide explains:

1. **Choose photo** uses Android's picker without broad access to the photo library.
   **Take photo** delegates to an installed camera app. Fitt stores a separate private
   copy; originals stay untouched. Newly added images are oriented, resized to a
   maximum 2048 px edge, and re-encoded as JPEG without source EXIF/location metadata.
   Source images must be ≤20 MiB and ≤100 MP.
2. Open a photo to edit its date/caption, save from the fixed header, or remove it.
   Select two entries for a side-by-side comparison. Identical prepared images are
   deduplicated; backup copies with different IDs can share one private blob.
3. **Also save new photos to phone gallery** is off by default and requires explicit
   opt-in. It creates **Pictures/Fitt Progress** on the next new addition. An existing
   entry can be copied individually. Gallery apps can see these copies and their own
   cloud-backup settings may apply. Android 8–9 asks for storage permission only for
   this optional copy; Android 10+ uses MediaStore without broad storage permission.
4. Private entries, original images and shared copies are independent. Edits/deletions
   never sync between them. Turning copies off leaves existing shared copies intact.
   Deleting a Fitt entry removes its private file only when no entry references it.
5. Export a **ZIP with photos** before uninstalling or clearing app data. Restores
   preserve backed-up JPEG bytes and keep photos private. Phone-gallery copies are
   not part of the backup contract and are never created/deleted by restore.

Private app storage is **not a locked album**: someone using an unlocked phone can
open Fitt. Android app backup is disabled, update checks send no health data, and photos
are never sent to Health Connect. Shared gallery copies can outlive app uninstall.
Exported ZIPs are unencrypted; choose a private destination.

Platform references: [Android photo picker](https://developer.android.com/training/data-storage/shared/photo-picker)
and [shared media storage](https://developer.android.com/training/data-storage/shared/media).
