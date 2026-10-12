# Aytdlp implementation plan

Native Kotlin app: `con.themralex.aydlp`, minSdk 26, compileSdk 37. No telemetry, accounts or DRM circumvention.

## Architecture
`app` assembles ui/designsystem, ui/screens, domain, data and core packages. Domain contains only Kotlin queue transitions, profiles, URL/argument validation, failure classification and file naming. `ExtractionEngine` isolates native execution. Hilt provides a single process registry, Room database, DataStore settings and engine. Compose observes StateFlow; background workers never depend on a screen.

## Persistence
Room Download rows: UUID, source URL, title/thumbnail, format/profile snapshot, state, progress/bytes/rate/ETA, private staging directory, destination URI, priority, schedule, attempt and failure. Profiles and archive records are persisted. State updates use compare-and-set SQL under transactions. Unique active-source keys prevent duplicate jobs. Settings use DataStore; cookies use Android Keystore AES-GCM in noBackupFilesDir.

## State machine
QUEUED -> SCHEDULED/ANALYZING/CANCELED; SCHEDULED -> QUEUED/PAUSED/CANCELED; ANALYZING -> DOWNLOADING/FAILED/PAUSED/CANCELED; DOWNLOADING -> POST_PROCESSING/MOVING/PAUSED/FAILED/CANCELED; POST_PROCESSING -> MOVING/PAUSED/FAILED/CANCELED; MOVING -> COMPLETED/PAUSED/FAILED/CANCELED; PAUSED -> QUEUED/CANCELED; FAILED -> QUEUED/CANCELED. Terminal completion cannot be rewritten by a late process callback. Recovery requeues interrupted transfers only after process reconciliation. Pausing retains .part/.ytdl files. Cancel deletes only that job staging area.

## Risk gate before UI expansion
1. Inspect upstream native artifacts, ELF LOAD alignment including packed Python/FFmpeg libraries.
2. Initialize Python/FFmpeg/QuickJS on Android; test arithmetic runtime and import bundled EJS.
3. Extract a public YouTube test video on Android. Host tests cannot replace this.
4. Run a 16 KB emulator/device test and SDK zipalign on each release APK.
5. Test pause/restart with a deterministic fake transfer and process death during merge.
If environment prevents a gate, record the failure and continue independent work; do not label unexecuted tests as passed.

## Milestones
1. Build scaffold and custom design tokens/components.
2. Native engine, health report, cancellable analysis and profile selection.
3. Persistent queue, process control, UIDT/legacy FGS, constraints and retries.
4. Verified destination publishing and Media3 library/player.
5. Settings, encrypted cookies, verified atomic updates and diagnostic report.
6. Unit/UI/fault tests, accessibility, optional conveniences and performance measurement.
7. Real release build, APK/native alignment report, CI, signing backup and maintenance documentation.

## Acceptance evidence
Each claim must reference an executed test or a clearly marked device-validation task. Superior performance to other apps is a measurement goal, not a delivered claim without comparative benchmarks. Android force-stop forbids automatic restart until user interaction; explain this platform limit in the runbook.
