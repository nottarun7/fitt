Fitt **0.1.1** (versionCode **3**) improves workout entry and the native interface.

### Safer workout entry

- **Finish** is kept away from the set-entry controls and hidden while you type reps or load. **Log set** remains the main entry action.
- **Done** dismisses the keyboard without finishing the workout. Finishing still requires a separate confirmation, with a warning when unlogged entries remain.
- Update prompts wait while a workout or text entry is in progress, so they do not interrupt logging.

### A clearer interface

- A consistent blue/slate theme for both light and dark mode, with bundled **Barlow** and **Barlow Condensed** fonts that work offline.
- Flatter routine, food and history lists, more deliberate spacing, clearer calorie/macro summaries, and simpler navigation and progress controls.
- Original native Jetpack Compose components, informed by design research and Skiper UI references. No React components or web UI are embedded in the app. See the [design notes](https://github.com/nottarun7/fitt/blob/main/docs/ui-design.md) and included font licenses.

### Your data stays in place

This update keeps the application ID **io.github.nottarun7.fitt**, Room database **v3**, portable backup schema **5**, private-photo paths and both signing keys unchanged. Updating with the matching signed APK preserves routines, unfinished workout input, logged sets, history, foods, meals, schedules, photo records and private image files. This release does not reset or migrate those stores.

- Use **fitt.apk** if you installed the standard GitHub APK, or for a new installation.
- Use **fitt-legacy.apk** if you installed an early development APK or a previous compatibility APK.

The in-app updater selects the matching signing certificate automatically. Open the downloaded APK and approve Android's **Update** confirmation; **do not uninstall Fitt to update**. A ZIP backup with photos remains useful for a separate device migration.

Automatic checks contact GitHub at most once per day when Fitt is opened. No workouts, meals or photos are uploaded. Settings includes manual checks and an automatic-check toggle. Downloads and installation require your action.

**SHA256SUMS.txt** contains the release asset checksums. Source code is visible, but an open-source license has not yet been selected. The bundled fonts retain their own open-source licenses.
