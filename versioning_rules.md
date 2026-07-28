# Conduit Project Rules & Context

## Versioning Rules
- **Naming Convention:** All compiled APKs must be named using the format: `conduit.alpha.vX.X.apk`
- **Incrementing:** Increment the minor version (e.g., v1.1 -> v1.2) with each new build/feature update.
- **Major Versions:** Version `v2.0` is strictly reserved for the milestone when the app is considered stable and ready for "daily personal use."
- **Output Directory:** After a successful build, the versioned APK must be moved/copied from the default `app\build\outputs\...` directory to the `\Antigravity\App.Outputs\` directory at the root of the project.

## Documentation Rules
- **Artifact Versioning:** Never overwrite old implementation plans, walkthroughs, or readmes. Instead, create new versions of them suffixed with the relevant app version (e.g., `implementation_plan_v1.2.md`, `walkthrough_v1.2.md`). This preserves a historical record of iterations for future agents.

## Logging Protocol (Caveman)
- **Log Location:** `\build.log\caveman.log`
- **Update Frequency:** Must be updated every time a coding action or significant project change occurs.
- **Format:** "Caveman" style. Extremely token-efficient, barely human-readable, highly compressed syntax (e.g., shorthand, abbreviations, omitting filler words). Focus solely on: What (action), Why (reason), When (version). Must prefix each entry with an ISO date/time stamp (e.g., `[YYYY-MM-DDTHH:MM]`).

## Build Process
A custom Gradle task has been added to automate this. Always build the app using:
`.\gradlew.bat copyApkToOutputs`
This task will compile the debug APK, rename it according to the rules above, and place it in the `App.Outputs` folder.
