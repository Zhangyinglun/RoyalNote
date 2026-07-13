# Import Success Dialog Implementation Plan


**Goal:** Show a success dialog after old records import completes, then return to the main screen after the user confirms.

**Architecture:** Keep import completion state in `ImportViewModel`. `ImportScreen` renders a Material3 `AlertDialog` when the import is complete and calls the existing `onBack` callback only from the dialog confirm button.

**Tech Stack:** Kotlin, Jetpack Compose Material3, StateFlow, JVM unit tests, Compose instrumentation tests.

## Global Constraints

- App id/namespace remains `com.example.royalnote`.
- `minSdk = 33`, `targetSdk = 36`, `compileSdk` Android `36.1`.
- Keep the existing 墨香 visual style, serif typography, and muted Material3 palette.
- Do not add new dependencies.
- Do not change mood labels or existing timeline copy.

---

### Task 1: ViewModel Success Dialog State

**Files:**
- Modify: `app/src/test/java/com/example/royalnote/ui/ImportViewModelTest.kt`
- Modify: `app/src/main/java/com/example/royalnote/ui/ImportViewModel.kt`

**Interfaces:**
- Produces: `ImportUiState.successDialogMessage: String?`
- Produces: `ImportViewModel.dismissSuccessDialog()`

- [ ] **Step 1: Write the failing test**

Add a test that expects successful import to set `successDialogMessage` and not require immediate navigation through `isSuccess` alone.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.ImportViewModelTest.importSuccessShowsDialogMessage"`

Expected: FAIL because `successDialogMessage` does not exist.

- [ ] **Step 3: Write minimal implementation**

Add nullable dialog text to `ImportUiState`, set it to `已入录 X 则` on success, and clear it from `dismissSuccessDialog()`/`resetState()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.ImportViewModelTest.importSuccessShowsDialogMessage"`

Expected: PASS.

### Task 2: Import Screen Dialog

**Files:**
- Modify: `app/src/androidTest/java/com/example/royalnote/RoyalNoteAppTest.kt`
- Modify: `app/src/main/java/com/example/royalnote/ui/ImportScreen.kt`
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt`

**Interfaces:**
- Consumes: `ImportUiState.successDialogMessage`
- Consumes: `ImportViewModel.dismissSuccessDialog()`
- Produces: `ImportScreen(..., onSuccessConfirmed: () -> Unit)`

- [ ] **Step 1: Write the failing UI test**

Add a Compose test for `ImportScreen` that renders a success message, verifies `已入录 X 则` and `回到首页`, taps the button, and asserts the callback ran once.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat connectedDebugAndroidTest --tests "com.example.royalnote.RoyalNoteAppTest.importSuccessDialogConfirmsReturn"`

Expected: FAIL because no success dialog is rendered.

- [ ] **Step 3: Write minimal implementation**

Render `AlertDialog` in `ImportScreen` when `successDialogMessage != null`. Remove automatic `LaunchedEffect(uiState.isSuccess) { onBack() }`. In `MainActivity`, wire `onSuccessConfirmed` to `dismissSuccessDialog()`, `resetState()`, and `navController.popBackStack()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat connectedDebugAndroidTest --tests "com.example.royalnote.RoyalNoteAppTest.importSuccessDialogConfirmsReturn"`

Expected: PASS.

### Task 3: Verification On Emulator

**Files:**
- No source changes expected.

- [ ] **Step 1: Build and unit test**

Run: `./gradlew.bat testDebugUnitTest assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Install on emulator**

Run: `./gradlew.bat installDebug`

Expected: APK installs on `emulator-5554`.

- [ ] **Step 3: Manual emulator check**

Open app, import sample text, verify the success dialog appears, tap `回到首页`, and verify the main screen is visible.

## Self-Review

- Spec coverage: Covers success popup and returning to main screen after import.
- Placeholder scan: No placeholders.
- Type consistency: `successDialogMessage` and `dismissSuccessDialog()` are used consistently across test, UI, and wiring.
