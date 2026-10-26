# Interchange

Pure Kotlin/JVM module for portable document models, validation, serialization,
archive verification, and duplicate planning.
Must not depend on Android, Room, or Compose.

## Workout document v1

`WorkoutDocument` uses the format marker `fitt.workouts` and explicit
`schemaVersion: 1`. It contains routines and independent workout snapshots.
Times are UTC epoch milliseconds. IDs remain stable across export/import and must
be nonblank and unique within their containing collection. Exercise IDs are
scoped to their parent routine/session. A workout can reference a deleted routine;
its exercise names and logged sets remain intact.

Sets record positive reps and an optional finite, non-negative load with an
explicit `KG` or `LB` unit. Null load means bodyweight/unrecorded; zero is distinct
and valid. Draft routines and unfinished sessions may be empty. No unit conversion
or personal default routine is applied.

`WorkoutJson.encode` validates before export. `WorkoutJson.decode` returns a typed
success, JSON error, validation errors with field paths, or unsupported version.
It checks the header before decoding version-specific fields. Unknown fields and
units are rejected to avoid silently losing data. Missing collections do not
default to empty. Decode has no storage effects.

Run `gradlew.bat :interchange:test` from the repository root. Tests use only
synthetic records, including a fixed v1 JSON fixture for compatibility checks.

The classes above remain as the legacy v1 contract. The Android app exports v5.

## Complete workout, nutrition and photo backup v5

`BackupDocument` retains the `fitt.workouts` format marker with schema version 5.
Required fields are explicit; unknown fields/enum values are rejected. It contains:

- Routines: ID, name, update timestamp, and ordered exercise definitions.
- Exercise definitions: stable ID, name, position, `REPS`/`SECONDS` metric, targets,
  optional catalogue ID, per-side flag, and assistance flag.
- Workouts: ID, snapshot name, optional source routine ID, UTC epoch start/end,
  and ordered exercise snapshots. Null completion means the single active session.
- Logged sets: stable ID, position, positive amount, optional finite non-negative
  load, explicit `KG`/`LB` unit, and assistance flag.
- Input drafts: raw value/load strings, unit, and assistance. Draft strings can be
  unfinished or invalid for logging; drafts belong only to active workouts.
- Required `nutrition` object: `foods`, `meals`, `schedules`, and `reminderMinute`.
  Every nested field is explicit. Foods have ID, name, serving label, calories,
  protein/carbs/fat per serving, and update time. Meals have ID, ISO local date,
  name, notes, update time, and portions. Schedules have ID, name, weekday bitmask
  (Monday bit 0 through Sunday bit 6), minute of day, enabled flag, notes, update
  time, and portions. A portion stores ID, position, nullable source food ID, name,
  serving label, quantity, and per-serving nutrients. Meal/schedule item IDs are
  unique within their respective tables. Deleted food references are permitted.
  Quantities must be finite, positive, and ≤10000; nutrients finite and 0..100000.
  A logged meal must contain a portion. A diet plan may instead contain nonblank
  text instructions with an empty portion list; it cannot be logged until foods
  are supplied. Weekdays must be 1..127; plan times are 0..1439 or -1 for unknown.
  Reminder time remains 0..1439. These relaxed plan semantics are the v4 revision;
  the Room tables and stored field types do not change.
  Notification permission and opt-in are device consent, outside the portable data.
- Required `photos` array and `photoScope` boolean. Each photo contains `id`, ISO
  `date`, `caption`, epoch-ms `addedAt`, lowercase `sha256`, `byteCount`, `width`
  and `height`. Up to 200 entries, 5 MiB per JPEG and 2048 px per edge. Descriptors
  sharing a hash must agree on size/dimensions. Device gallery URIs are excluded.
  `photoScope=false` requires an empty photo array and preserves local photos under
  every import mode. Full photo backups use `true`, including when empty.

IDs are unique per table, including exercise/set IDs across parents. Positions are
unique within each parent and may contain gaps. Source routine IDs may reference
deleted routines. Empty routines and empty legacy history remain representable.
Canonical serialization sorts roots by ID and child records by position.

`BackupJson.decode` supports v5 and explicitly migrates valid v1/v2/v3/v4 JSON. V1 migration
uses deterministic, length-prefixed IDs for nested records, preserves the original
fields, and reports defaults for metadata absent from v1. It never silently treats
a future schema as v5. V2 conversion adds an empty nutrition object and reports
the absence of nutrition. V2/v3 CSV and ZIP remain readable. V3 nutrition values,
dates, and IDs are preserved. All v1–v4 files upgrade with `photoScope=false`.
The old `WorkoutJson`
API still reads/writes v1 only.

## CSV contract

`BackupCsv` uses UTF-8 (an input BOM is allowed), a header, one metadata row, and
typed rows: `routine`, `routine_exercise`, `workout`, `workout_exercise`, `set`, and
`draft`, plus `nutrition_settings`, `food`, `meal`, `meal_item`, `diet_schedule`,
and `schedule_item`, plus `photo`. Exactly one nutrition settings row stores the reminder time.
Empty workout parents get their own rows. Headers may be reordered but cannot be
missing, duplicated, or extended. All unrelated cells on a typed row must be empty.

Columns, in the exported order:

```text
record_type,id,parent_id,position,name,catalogue_id,metric,target_sets,target_value,per_side,assistance,updated_at,started_at,completed_at,routine_id,value,weight,unit,draft_value,draft_weight,exported_at,schema_version,format,text_encoding,record_count,serving_label,calories,protein,carbs,fat,quantity,date,time_minute,weekdays,enabled,notes,food_id,photo_scope,sha256,byte_count,pixel_width,pixel_height
```

Metadata declares format, schema, export timestamp, `text_encoding=apostrophe-v1`,
and the count of non-metadata rows. This catches truncation even at a complete-row
boundary. Children reference parent IDs; draft ID and parent ID both equal the
workout-exercise ID. Missing parents and duplicate IDs are errors. Numbers use
invariant dot decimals and booleans are `true`/`false`; blank nullable cells mean
null, while a zero load remains explicit.

The v5 header has 42 columns. Metadata stores `photo_scope`; photo rows store ID,
date, caption in `notes`, addedAt in `updated_at`, and SHA/byte/dimension columns.
The exact legacy 25-column v2 and 37-column v3/v4 contracts remain readable.

CSV follows quoted-field rules with doubled quotes, embedded line breaks, and
CRLF output. Input accepts CRLF/LF/CR row boundaries. For text columns, strings
starting with an apostrophe or potential spreadsheet formula/control prefix receive
one leading apostrophe. Import reverses only this declared encoding and rejects
unsafe unescaped cells. Preserve metadata and record counts when editing; generic
CSV files are not accepted. Spreadsheet resaves can change types, so JSON/ZIP are
the preferred archival formats.

## ZIP and limits

`BackupFiles` detects plain JSON/CSV and legacy three-entry ZIPs. The Android app
uses `PhotoBackupArchive` for all new ZIPs. Manifest format `fitt.backup.archive`,
version 2, declares `jsonSha256`, `csvSha256`, and `photos`, a map from
`photos/<sha256>.jpg` to its SHA. Core entries are `manifest.json`, `backup.json`,
and `backup.csv`; each distinct photo hash has exactly one image entry. Manifest
version 1 remains readable. The ZIP directory, CRCs, SHA-256 hashes, and matching canonical
JSON/CSV content are checked. Checksums detect corruption, not authorship.
Directories, duplicate or unexpected entries, encrypted/damaged/truncated archives,
and traversal names are rejected. Callbacks stream validated images into private
staging; archive paths are never used as filesystem extraction targets. The Android
layer also verifies decodability and dimensions before a restore can commit.

Limits live in `BackupLimits` and `PhotoLimits`: 256 MiB streamed ZIP (compressed
and expanded), 200 photos at 5 MiB each, 12 MiB plain JSON/CSV, 10 MiB documents,
24 MiB legacy archive expansion, 100,000 data records, 16,384-character text values,
200-character IDs, and depth 32 JSON. JSON writing and archive reads are bounded.
JSON duplicate keys (including equivalent escaped keys) are rejected before decode.

## Duplicate planning

`BackupPlanner` is pure and has no storage effects. Merge matches whole routines
and workouts, foods, meals, diet schedules and photos by ID. Identical data is skipped; changed duplicates use keep-existing,
use-imported, or keep-both. Nested IDs alone do not make otherwise identical records
different. Same titles with distinct root IDs remain separate records. Copies get
fresh IDs, copied source-routine references are rewired, and child-ID collisions
cannot overwrite other parents. Copies of food references are rewired in copied or
new incoming meals/plans; existing snapshots stay intact. Reminder time uses the
current device value in keep-existing/keep-both merges and the incoming value with
use-imported. Exact restore uses the incoming value. None of these choices enable
notifications or copy notification permission.

Two distinct active sessions require keeping the current one or replacing it with
the imported one. Exact restore uses only the imported snapshot and preserves its
IDs. Plans expose counts and changed duplicate details plus a fingerprint of the
current snapshot (excluding its export timestamp). The Android `BackupStore` checks
that fingerprint inside the same transaction used to apply all changes, and rejects
stale plans. A failed transaction rolls back every parent, set, and draft.

Photo inclusion is explicit. `dataOnly()` removes photo records and sets their
scope false; the app uses it for JSON/CSV and optional data-only ZIP exports.
Exact restore of full photo scope can remove private photos; false scope always
preserves them. Metadata fingerprints exclude export time and photoScope (a file
inclusion marker), but include every photo descriptor. File repair from an identical
ZIP is handled by the Android layer even when no metadata change is needed.
Do not relabel an incompatible document as v4.

## LLM-friendly plan import v1

`PlanJson` reads the separate `fitt.plan` marker and schema version 1. The bundled
[guide](../docs/import-guide.md), [template](../docs/fitt-plan-example.json), and
[JSON Schema](../docs/fitt-plan-v1.schema.json) are the public conversion contract.
The same files are packaged as app assets and loaded by tests, avoiding divergent
guide/template copies. The importer is strict about object shapes, numeric versus
string types, duplicate keys, IDs, source food references, and supported units.

Source IDs are readable ASCII keys. A length-prefixed sourceId/type/item key is
converted to a deterministic UUID, preserving duplicate identity on re-imports.
Exercise positions and food-portion positions come from array order. Missing plan
times become -1; missing nutrition stays in text diet instructions, not zero-valued
foods. Internal update/export times are 0 for stable plan identity; the UI identifies
these as plans instead of displaying a fake export date. Completed workouts and
logged meals are always empty. Plan-only data is carried through `DecodedBackup.isPlan`.

The Android import flow only offers merge for a plan and enforces this again in
the ViewModel and storage preview. Plan previews preserve the current reminder
time even with use-imported, since a plan has no reminder-setting field. Unknown
root fields such as workout history are rejected. A plan
cannot be smuggled through a backup ZIP. Exporting the whole saved database later
produces a normal v4 backup, including any plans and genuine logged history.
