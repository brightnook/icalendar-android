# Google Play Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare the iOSCalendar repository for a future Google Play release by excluding local artifacts and adding accurate product, privacy, and release documentation.

**Architecture:** Keep application behavior unchanged. Repository hygiene is enforced through `.gitignore`; developer-facing release guidance lives under `docs/`; the root `README.md` is the public project entry point. Documentation must describe the implemented read-only iCloud CalDAV synchronization and must not claim event editing or iCloud write support.

**Tech Stack:** Android 9+; Kotlin; Jetpack Compose; Room; DataStore; WorkManager; OkHttp; iCloud CalDAV.

**Spec:** User-approved release-preparation scope in this Codex task on 2026-08-24.

## Global Constraints

- Do not delete tracked source files or change calendar behavior.
- Do not commit device-specific directories, Android Studio state, Gradle caches, screenshots, APKs, keystores, or `local.properties`.
- Use `vf3210552@gmail.com` as the public support contact.
- State that Apple Account app-specific passwords and calendar data are used only for the app's iCloud synchronization function.
- State that the current app is read-only: it does not create, edit, or delete iCloud events.
- Verify the debug build after documentation and repository-hygiene changes.

---

### Task 1: Make local development artifacts non-versioned

**Files:**
- Modify: `.gitignore`
- Delete: untracked `.android/`, `.android-device-test/`, `.android-home/`, `.gradle-test-home/`, `.gradle-user-home/`, `.kotlin/`, `artifacts-*.png`, `gradle/gradle-daemon-jvm.properties`

**Interfaces:**
- Consumes: current local Gradle and ADB test artifacts.
- Produces: a clean `git status --short` with no generated Android test artifacts.

- [x] **Step 1: Add repository-specific ignore rules**

Append these rules to `.gitignore`:

```gitignore
# Local Android and Gradle test homes
.android*/
.gradle-*/
.kotlin/
gradle/gradle-daemon-jvm.properties

# Local verification artifacts
artifacts-*.png
*.apk
*.aab
*.jks
*.keystore
```

- [ ] **Step 2: Remove only the named untracked artifacts**

Use PowerShell `Remove-Item -LiteralPath` after confirming every target is untracked in `git status --short`. Do not remove `.gradle/`, `app/`, `gradle/`, or any tracked file.

- [x] **Step 3: Verify ignore coverage**

Run: `git -c safe.directory=$PWD status --short`

Expected: no `.android*`, `.gradle-*`, `.kotlin`, screenshot artifact, or Gradle daemon state entry; any unrelated tracked local configuration remains visible and unstaged.

### Task 2: Add public project documentation

**Files:**
- Create: `README.md`
- Create: `docs/PRIVACY_POLICY.md`

**Interfaces:**
- Consumes: implemented app behavior, supported Android version, public support contact.
- Produces: public Markdown suitable for the repository and a hosted privacy-policy source.

- [x] **Step 1: Write `README.md`**

Include: app purpose; supported Android version; iCloud login using an Apple Account app-specific password; read-only sync; month and year views; desktop widget; Chinese, English, and Japanese interfaces; theme options; build commands; limitations; and a short roadmap that lists event creation separately as a future feature.

- [x] **Step 2: Write `docs/PRIVACY_POLICY.md`**

Include: effective date; data categories (Apple Account email, app-specific password, selected calendar data, local theme image); device-local storage; iCloud CalDAV connection; no advertising or analytics SDK; user controls for logout/cache clearing; support address `vf3210552@gmail.com`; and the statement that event data is not sold or shared with third parties.

- [x] **Step 3: Verify public copy is accurate**

Run:

```powershell
rg -n "create|edit|delete|app-specific password|vf3210552@gmail.com|Android" README.md docs/PRIVACY_POLICY.md
```

Expected: the files identify the support address and describe creation/editing/deletion only as unsupported or future work.

### Task 3: Add a repeatable Google Play release checklist

**Files:**
- Create: `docs/GOOGLE_PLAY_RELEASE.md`

**Interfaces:**
- Consumes: current Android app configuration and public policy document.
- Produces: a release-owner checklist that does not contain private signing data.

- [x] **Step 1: Document release configuration checks**

Include the exact current application ID `com.victor2319.ioscalendar`, minimum SDK 28, target SDK 36, the need to increment `versionCode` for every upload, and the requirement to produce a signed Android App Bundle.

- [x] **Step 2: Document Play Console assets and declarations**

List store listing text, screenshots for phone and widget, app icon, feature graphic, support email `vf3210552@gmail.com`, privacy-policy URL after hosting `docs/PRIVACY_POLICY.md`, Data safety declaration based on the policy, content rating, testing tracks, and production rollout review.

- [x] **Step 3: Document release validation**

List clean checkout build, unit tests, connected-device tests, account login, manual/background sync modes, light/dark/image themes, widget 2x3 layout, Chinese/English/Japanese interfaces, logout, cache clear, and upgrade-over-existing-install checks.

### Task 4: Validate and publish the repository-preparation change

**Files:**
- Modify: `.gitignore`
- Create: `README.md`, `docs/PRIVACY_POLICY.md`, `docs/GOOGLE_PLAY_RELEASE.md`

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: a buildable, documented, cleanly ignored repository state.

- [x] **Step 1: Check documentation and Git whitespace**

Run:

```powershell
git -c safe.directory=$PWD diff --check
git -c safe.directory=$PWD status --short
```

Expected: no whitespace errors and no generated local artifacts proposed for commit.

- [x] **Step 2: Build the debug application**

Run:

```powershell
$env:JAVA_HOME='D:\FM\work\jbr'
$env:ANDROID_USER_HOME=(Join-Path $PWD '.android-home')
$env:GRADLE_USER_HOME=(Join-Path $PWD '.gradle-test-home')
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit and push only public repository files**

Run:

```powershell
git add .gitignore README.md docs/PRIVACY_POLICY.md docs/GOOGLE_PLAY_RELEASE.md docs/superpowers/plans/2026-08-24-google-play-readiness.md
git commit -m "docs: prepare Google Play release materials"
git push
```

Expected: the current development branch is synchronized with `origin` and local cache directories remain untracked/ignored.
