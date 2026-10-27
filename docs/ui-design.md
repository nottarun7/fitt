# Fitt interface direction

Fitt is a native Android training utility. Its interface should make entering a
set, reviewing a meal or finding a past session quick and predictable. This
redesign favors clear hierarchy and useful feedback over decorative cards,
marketing copy or repeated visual effects.

## Research and references

- [Skiper UI components](https://skiper-ui.com/components): a reference for
  restrained component detail and interaction polish. Skiper's React components
  are inspiration only; Fitt does not copy TSX, add a React runtime or embed a
  web-based interface.
- [Anthropic frontend-design](https://github.com/anthropics/skills/tree/main/skills/frontend-design):
  deliberate typography, content-specific layout and removal of generic styling.
- [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill):
  guidance for hierarchy, touch interaction, accessibility, forms, motion and
  the detected Jetpack Compose stack.
- [Reddit discussion of design skills](https://www.reddit.com/r/ClaudeDesign/comments/1vpi5mf/top_7_claude_skills_for_design_and_ui_by_install/):
  used to discover candidate skills, not as proof that popularity produces a
  usable design. Skill recommendations are inputs to review, not an automatic
  guarantee of quality.
- [Custom design systems in Compose](https://developer.android.com/develop/ui/compose/designsystems/custom):
  the implementation approach for shared semantic colors, typography and shapes.

The components are original native Compose implementations. No account, remote
design service or additional network request is needed to render the interface.

## Visual system

Use the shared [FittTheme](../app/src/main/java/io/github/nottarun7/fitt/ui/FittTheme.kt)
as the source of truth rather than scattering local color or type choices across
screens. Light mode combines a cool neutral background with ink text and a blue
accent. Dark mode uses slate surfaces and a lighter blue accent. Primary actions,
secondary controls, destructive actions and supporting text retain distinct
semantic roles in both themes.

Barlow is used for body text, numbers and controls; Barlow Condensed gives headings
a compact athletic character. Font files are bundled in `app/src/main/res/font/`
and require no online font service. Their SIL Open Font License notices are
included in this repository and the app's bundled documentation:

- [Barlow license](third-party/barlow-OFL.txt)
- [Barlow Condensed license](third-party/barlowcondensed-OFL.txt)

Prefer a small number of surface treatments and clear section boundaries. Flat
rows suit routine, food and history lists; grouped surfaces are used when their
contents form one task. Show important values with their units, keep helper text
quiet and place labels near the values or fields they describe.

## Interaction and accessibility

- Keep **Log set** as the main workout-entry action. Place **Finish** away from
  reps/load controls, hide it during typing, and require confirmation before
  completing a session. **Done** only dismisses typing.
- Preserve visible field labels, input validation, keyboard behavior and saved
  drafts. A more compact layout must not make a destructive action easier to tap.
- Keep interactive targets at least 48 dp even when their visual treatment is
  compact. Allow text to scale and wrap rather than shrinking it to fit.
- Use semantic colors with readable contrast, named actions and screen-reader
  descriptions. Do not communicate state only through color or an icon.
- Use motion to explain a user-triggered change such as selection or expansion.
  Avoid looped decoration and respect Android's motion settings.
- Defer automatic update prompts during workout logging and text entry.

Review the result on-device in light and dark mode, at larger font sizes, with
the keyboard visible, and with empty and populated data. Screenshots alone do
not establish complete accessibility compliance.

## Existing data is outside the redesign

Version 0.1.1 uses versionCode 3 but retains application ID
`io.github.nottarun7.fitt`, Room database v3 and backup schema 5. Private photo
blobs stay at `filesDir/progress_photos/<sha256>.jpg`; the optional phone-gallery
album remains `Pictures/Fitt Progress`. Photo captions, dates and device-only
gallery references retain their existing meaning. Neither the storage layout
nor the two release-signing keys changes for this interface update.

Updating with the APK that matches the installed signing certificate keeps
existing routines, logged and unfinished workouts, nutrition data and photos.
Verification should compare the same populated dataset and exact photo bytes
before and after a real Android APK update. Interface code must not seed example
user records, rewrite existing records or reset preferences merely to demonstrate
the new design.

## Version 0.1.1 verification

A signed v0.1.0 installation was updated in place to v0.1.1 on Android 16, without
uninstalling or clearing its data. Normal ZIP exports before and after matched
exactly after excluding only the export timestamp, including both photo files
byte for byte. The synthetic fixture included two routines, 19 completed
workouts, an active workout with raw unfinished input, custom foods, meals, a
weekly diet schedule and reminder time. Dark-mode selection also survived.

The released Room schema files remain byte-identical. A separate regression test
checks all 13 user tables, private JPEG bytes and the device-only gallery reference.
Manual layout checks covered both themes, a roughly 376 dp width with 1.5× text,
landscape and Android's disabled-animation setting. Measured text/background
contrast pairs range from 4.86:1 upwards; these checks do not establish complete
accessibility compliance or device coverage.
