# RoyalNote Agent Notes

## Project

- Single-module Android app (`:app`), namespace/app id `com.example.royalnote`; user-facing name is `起居注`.
- AGP `9.2.1`, Gradle `9.4.1`, Kotlin `2.2.10`, KSP `2.2.10-2.0.2`, Compose BOM `2026.02.01`.
- Android `compileSdk 36.1`, `minSdk 33`, `targetSdk 36`.
- Versions live in `gradle/libs.versions.toml`; Room uses KSP. Add repositories in `settings.gradle.kts`, which enforces `FAIL_ON_PROJECT_REPOS`.
- Configuration cache is enabled. The `android.disallowKotlinSourceSets=false` experimental warning is currently expected.

## Build, Test, and Deploy

Run from the repository root. On macOS/Linux use `bash ./gradlew` when the wrapper is not executable; on Windows use `.\gradlew.bat`.

```text
assembleDebug
testDebugUnitTest
app:testDebugUnitTest --tests "com.example.royalnote.ui.RecordTimelineViewModelTest.saveAddsRecordAndClearsForm"
lintDebug
connectedDebugAndroidTest   # Android emulator only; never run on a physical phone
installDebug                # device/emulator required
```

### Device data safety (mandatory)

#### Feature completion gate (mandatory)

- After any feature implementation, start a clean disposable Android emulator and run the relevant emulator-based UI/instrumentation tests before declaring the work complete.
- A successful build, JVM unit tests, lint, or compilation of Android test sources is not sufficient to declare feature work complete.
- Declare completion only after the relevant emulator tests pass. If no clean disposable emulator can be started, or if emulator tests fail, report the work as incomplete or blocked and state exactly what remains.
- This completion gate never permits using a physical phone for tests. All device-test safety, serial verification, backup, and data-preservation rules below remain mandatory.

#### Local disposable emulator

- A reusable test AVD is installed as `RoyalNote_API_36_1_Clean` with `system-images;android-36.1;google_apis;arm64-v8a` revision 4 (Google APIs, ARM64). The installed image occupies about 4.3 GB.
- Android command-line tools are installed at `/Users/yinglun/Library/Android/sdk/cmdline-tools/latest`; the emulator is `/Users/yinglun/Library/Android/sdk/emulator/emulator`, and ADB is `/Users/yinglun/Library/Android/sdk/platform-tools/adb`.
- Start a clean headless test instance with:
  `/Users/yinglun/Library/Android/sdk/emulator/emulator -avd RoyalNote_API_36_1_Clean -no-snapshot -wipe-data -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`
- `-wipe-data` is allowed only for this disposable AVD. It must never be applied to a physical phone or a data-bearing emulator.
- After startup, run `adb devices -l`, select only a serial beginning with `emulator-`, and verify boot completion with `adb -s <emulator-serial> shell getprop sys.boot_completed` returning `1`. The first observed serial was `emulator-5554`, but the assigned serial/port may change and must never be assumed or hard-coded.
- Scope Gradle device tests with `ANDROID_SERIAL=<emulator-serial>`. Example:
  `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ANDROID_HOME='/Users/yinglun/Library/Android/sdk' ANDROID_SDK_ROOT='/Users/yinglun/Library/Android/sdk' ANDROID_SERIAL='<emulator-serial>' bash ./gradlew connectedDebugAndroidTest`
- The model/effort UI was verified on this AVD with `RoyalNoteAppTest.settingsScreenFiltersEffortsAndReportsSelections` and `MainActivitySettingsSmokeTest.modelOptionsExposeOneSelectableGroup` passing on 2026-07-15.
- Stop the emulator after testing with `adb -s <emulator-serial> emu kill`; keep the AVD and system image for the next feature-completion test run.

- Treat `connectedDebugAndroidTest` and similar instrumentation workflows as destructive: Gradle may uninstall the target app after testing and thereby delete its Room database, files, preferences, and other app data.
- All connected/instrumentation/UI tests must run on a clean disposable Android emulator (AVD) only. Never run them on any physical phone, even if the phone appears empty or a dedicated test `applicationId` is available.
- Before running `connectedDebugAndroidTest` or any ADB-driven test command, verify that the selected serial starts with `emulator-`. If no emulator is available, stop and report that device tests were not run; do not fall back to a physical device.
- Never run connected/instrumentation tests against `com.example.royalnote` on any data-bearing environment. In particular, the current OPPO `CPH2655` is installation/visual-review only and must never be selected as a test target.
- Before any command or workflow that may uninstall, reinstall incompatibly, clear, replace, restore, migrate, or otherwise risk app data, first explain the exact risk and obtain explicit user approval. Approval alone is not a substitute for a verified backup when real records are present.
- Before an approved risky operation, create and verify a recoverable backup of at least `databases/royal_note.db` (including current WAL state), `files/reflection/MEMORY.md`, and relevant preferences. Do not claim a backup exists merely because `android:allowBackup="true"`; verify an actual restorable copy.
- Safe in-place installation is `adb -s <serial> install -r ...`, but still confirm the package/signature is compatible. Never use `adb uninstall`, `pm uninstall`, `pm clear`, `adb install -r -d` with incompatible data, or a test workflow that uninstalls the app unless the preceding approval-and-backup requirements are satisfied.
- After any device test or deployment, verify that `com.example.royalnote` remains installed and that its existing record count/data is intact before performing another install or launch. If the package disappears or data changes unexpectedly, stop immediately; do not reinstall or seed a new database before assessing recovery options.

### Physical-phone installation safety (mandatory)

- Any APK installed on a physical phone must preserve all historical records, reflection memory, settings, and other app data. If preservation cannot be demonstrated before installation, do not install.
- Physical-phone deployment is an in-place upgrade only: verify the target serial explicitly, confirm the installed package is `com.example.royalnote`, verify APK/package signing compatibility, and use only `adb -s <serial> install -r ...`. Never use an install/test workflow that uninstalls the target package.
- Before installing to a physical phone, force Room to a consistent state or preserve the database together with its `-wal` and `-shm` files, then create and verify a restorable off-device backup of `royal_note.db`, `reflection/MEMORY.md`, and relevant preferences. A copy is not considered verified until its files are present, non-empty where expected, and can be associated with the current package/data snapshot.
- Database schema changes must provide explicit Room migrations and passing migration tests. Never enable destructive migration fallback, delete/recreate the database, clear tables, or reseed over an existing database to make an upgrade succeed.
- Record a pre-install data check (at minimum database presence, record count, and backup timestamp) and repeat the same check after installation before declaring success. Historical record count/content must not decrease or change unexpectedly.
- If `adb install -r` reports signature incompatibility, downgrade incompatibility, migration failure, package disappearance, or any other condition that could risk data, stop. Do not uninstall, clear data, reinstall, or retry with destructive flags.
- Do not run `installDebug`, `connectedDebugAndroidTest`, or an unscoped ADB command when both an emulator and a physical phone are connected. Always pass the explicit serial, using the emulator for tests and the phone only for approved safe installation/visual review.

#### Preferred physical-phone deployment workflow

- After the user explicitly approves installing to the phone, prefer `scripts/safe-install-cph2655.sh` over assembling manual ADB commands. The script enforces the backup, identity, signing, record-count, data-fingerprint, migration, and post-launch checks required above.
- Always run `adb devices -l` first. Wireless ADB pairing and connection ports can change; never reuse an old IP/port without verifying it. Pass the exact currently connected serial explicitly:
  `bash scripts/safe-install-cph2655.sh --serial <adb-serial>`
- The accepted targets are the OPPO `CPH2655` with hardware serial `d0caddca`, reached either as USB serial `d0caddca` or a currently verified wireless ADB serial such as `<ip>:<port>`. The script must reject `emulator-*`, another model, or another hardware serial.
- Let the script build the debug APK unless an already-built APK is intentionally supplied with `--apk <path>`. Before installation it verifies that the APK application id is `com.example.royalnote` and that its signing certificate matches the currently installed APK.
- The script stops before installation until the operator types `INSTALL`. This confirmation must not be bypassed. It then uses only `adb install -r`, verifies the untouched data snapshot immediately after installation, launches once to exercise Room migrations, stops and verifies the post-launch database/history/settings/memory snapshot, and only then reopens the app.
- Verified backups are written under `.device-backups/<timestamp>-d0caddca/`, which is Git-ignored. They contain private records, preferences, and potentially an API key; never commit, upload, print, or share their contents. Do not delete the newest verified backup until the user no longer needs rollback protection.
- If the script stops or reports any mismatch, do not bypass it with manual installation flags. Report the exact failed safety check. A manual fallback is allowed only when the script itself is unavailable and must reproduce every mandatory verification step above without weakening it.

### Known macOS setup issues

- `./gradlew: permission denied`: run `bash ./gradlew ...`; do not change wrapper permissions just to build.
- `Unable to locate a Java Runtime`: use Android Studio's JBR:
  `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'`.
- `SDK location not found`: set both `ANDROID_HOME` and `ANDROID_SDK_ROOT` to `/Users/yinglun/Library/Android/sdk` (or add an untracked `local.properties` with `sdk.dir=...`).
- A working one-shot build command is:
  `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ANDROID_HOME='/Users/yinglun/Library/Android/sdk' ANDROID_SDK_ROOT='/Users/yinglun/Library/Android/sdk' bash ./gradlew assembleDebug`.
- ADB is `/Users/yinglun/Library/Android/sdk/platform-tools/adb`; the former `/tmp/android-sdk/...` path may not exist.
- If ADB reports `could not install *smartsocket* listener: Operation not permitted`, rerun it with host/device access outside the sandbox.
- Current phone: OPPO `CPH2655`, Android 16, USB serial `d0caddca`. Wireless ADB was last seen at `192.168.68.53:5555`; always run `adb devices` because the IP can change.
- Install without deleting data: `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`; launch with `adb -s <serial> shell am start -W -S -n com.example.royalnote/.MainActivity`.
- On `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, do not uninstall automatically. `adb uninstall -k com.example.royalnote` requires explicit approval because signature replacement can risk app data.

On legacy Windows setups, ADB may be at `C:\Users\zhang\AppData\Local\Android\Sdk\platform-tools\adb.exe`; verify it before assuming no device is connected.

## Architecture and Scope

- `MainActivity.kt` is the UI entrypoint. `RoyalNoteNavigation` owns `主页`、`省察`、`设置` and the nested import route.
- Wiring is manual in `MainActivity`: Room feeds note and reflection repositories/view models; OpenRouter settings are shared by import and reflection services.
- Local-first Room database `royal_note.db` is version 4. Migrations `1 -> 2 -> 3 -> 4` preserve records; schemas export to `app/schemas`.
- `RecordTimelineViewModel` depends on `RecordOperations`; `ReflectionViewModel` depends on `ReflectionOperations` and `ReflectionAiGateway`, keeping JVM tests independent from Room/network implementations.
- Long-term reflection memory is bounded Markdown at `filesDir/reflection/MEMORY.md`, managed by `MemoryFileStore` and edited through grouped cards.
- Scope is local record management, OpenRouter text import/settings, and confirmed `七日省察`. Do not add search, mood filtering, cloud sync, export, bulk clear, review regeneration, regional hotline catalogs, or evidence-detail UI unless requested.

## UI Workflow and 墨香 Design

- For frontend styling changes or new features, also create a self-contained HTML mockup for user review. The confirmed reflection mockup is `docs/seven-day-reflection-preview.html`.
- Style is antique and understated: aged paper, dark ink, muted bronze; no bright/saturated colors. Primary is bronze, gold is sparse decoration, and `dynamicColor = false`.
- Core light/dark colors: primary `#7A5C3E`/`#C49A6C`, background `#F2EDE3`/`#1A1814`, surface `#EAE4D6`/`#262320`, text `#2A2015`/`#EDE6D6`, outline `#D6CDBB`/`#3D3833`.
- Mood colors: 开心 `#3F7659`, 满足 `#3E7270`, 平静 `#496E8A`, 疲惫 `#8D6B2C`, 烦躁 `#A14F3C`, 低落 `#875132`, 焦虑 `#8C3F49`.
- Use `FontFamily.Serif`, relaxed letter spacing, 8dp card corners, bronze title underline/dot and left accent bars. Record cards retain connector lines/dividers; mood chips are filled on cards and outlined-to-filled in editors.
- Color and type sources are `Color.kt`, `Theme.kt`, and `Type.kt`; use Material3 + Compose foundation only.

## Record Behavior to Preserve

- Mood labels are exactly `开心`, `满足`, `平静`, `疲惫`, `烦躁`, `低落`, `焦虑`; absent mood is `未输入`.
- Empty `eventText` cannot save and shows `先写下做了什么`. Editing preserves `createdAt` and updates `updatedAt`.
- Query order is `startedAt DESC, createdAt DESC, id DESC`; group by stored `eventDate` as `今天`, `昨天`, or ISO date.
- UI tests depend on exact Chinese text, including two `未输入` nodes in the sample screen. Update tests if labels intentionally change.

## Reflection Behavior to Preserve

- Exact labels: page `七日省察`, bottom nav `省察`, top actions `往日` and `记忆`.
- Review yesterday plus the six preceding complete local days; never include today's records or chat messages.
- A successful daily review is immutable and generated once with its exact record snapshot. Failures may retry; an older successful review may be shown read-only.
- Zero records: create a local empty review without model use and state chat has no record basis. One or two records: no `另一种可能` or attempt plans.
- Visible order: one summary, at most one `另一种可能`, at most two `尝试计划` cards, then chat.
- Keep full local chat. API context uses the fixed review/snapshot, confirmed memory, rolling summary, and bounded recent messages; compaction never deletes local messages. Failed messages stay as `未送达` and retry in place; replies are structured and non-streaming.
- Only confirmed memory crosses threads. Goals, accepted actions, and progress may auto-save; patterns, pressure sources, preferences, focus, and self-insight require confirmation. Never save diagnoses, personality labels, or crisis content.
- Attempt plans support accept/edit/skip/terminate; accepting writes action memory. Immediate danger pauses analysis, uses the fixed generic safety prompt, creates no memory, and permits continued chat—no regional hotline lists.
- Supplying an OpenRouter API key is consent for required transmission; settings must disclose that import and reflection send necessary content to OpenRouter.

## Git

- macOS Git root: `/Users/yinglun/Projects/RoyalNote`.
- In the legacy Windows workspace the root may be `C:\Users\zhang`; use path-limited status/diff commands to avoid scanning the whole profile.
