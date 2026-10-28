Fitt **0.1.2** (versionCode **4**) makes daily workout, meal and photo logging easier.

### Workouts

- Saved routines take priority on Train. Choose a familiar exercise in one tap; movement details remain available beside it.
- The keyboard moves from reps to load, and Done dismisses it without logging or finishing. The set fields and Log set button scroll into view together.
- Finish still requires confirmation. Its unlogged-entry warning now distinguishes changed input from the previous set's retained values.

### Meals and progress

- Browse diary dates with a compact calendar control. Meal and photo editors also offer a calendar while keeping typed dates available.
- Create a food while building a meal or diet schedule, then return to the same draft. Search explains empty results, and meal totals update as portions change.
- Successful nutrition actions clear their feedback after a short interval; errors remain available to dismiss. The main navigation hides while typing.
- Large system text uses compact navigation with full accessibility labels. Calendars scroll in landscape so dates stay clear of the confirmation buttons.
- Progress help appears beside the relevant metric. The photo gallery has a shorter first-use guide, compact controls and a focused photo editor.
- ZIP is the primary backup action, with JSON and CSV available under Other formats.

### Updating safely

This release keeps the application ID **io.github.nottarun7.fitt**, Room database **v3**, backup schema **5**, private-photo paths and both signing keys unchanged. Existing routines, drafts, workout history, foods, meals, schedules and photos stay in place when updating with the matching APK.

- **fitt.apk**: standard GitHub installations and new users.
- **fitt-legacy.apk**: early development or compatibility installations.

The in-app updater selects the matching certificate. Open the downloaded APK and approve Android's **Update** confirmation. **Do not uninstall Fitt to update.** Save a ZIP backup with photos when moving to another device.

Automatic checks contact GitHub at most once per day when Fitt opens. Your workouts, meals and photos are not uploaded. Downloading and installing remain your choice.

**SHA256SUMS.txt** contains the asset checksums. Source is public; an open-source license has not yet been selected. Bundled fonts retain their own licenses.
