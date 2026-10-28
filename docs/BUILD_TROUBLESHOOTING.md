# Build Troubleshooting, Error Remediation & AI Directives

> ========================================================================
> **MANDATORY DIRECTIVE FOR AI SYSTEMS / AUTOMATION AGENTS:**
> If you modify dependencies, resolve compiler errors, or adapt detection
> logic in this project, **YOU MUST IMMEDIATELY UPDATE `README.md`** under
> the corresponding section (Dependencies, Implementation Logic, or Challenges).
> Code and documentation must remain strictly synchronized at all times.
> ========================================================================

---

### 1. AGP / Gradle Version Incompatibility
* **Symptom:** `Unsupported class file major version` or `Plugin [id: "com.android.application"] not found`.
* **Root Cause:** Gradle runtime mismatch or missing JDK 17 configuration.
* **Remediation Steps:**
  1. Ensure JDK 17 is active: run `java -version`.
  2. In Android Studio: `Settings > Build > Build Tools > Gradle > Gradle JDK` set to JDK 17.
  3. Verify `gradle/libs.versions.toml` AGP version matches the Gradle wrapper version.

### 2. Missing Permissions at Runtime
* **Symptom:** SecurityException in logcat or culprit app displays as `Unknown`.
* **Root Cause:** `MANAGE_EXTERNAL_STORAGE` and `PACKAGE_USAGE_STATS` require manual user consent via system settings.
* **Remediation Steps:**
  1. Click **Grant Storage & Usage Permissions** in the app.
  2. Manually verify under: `Settings > Apps > Special app access > All files access`.
  3. Manually verify under: `Settings > Apps > Special app access > Usage access`.

### 3. Missing Subdirectory Events in FileObserver
* **Symptom:** Files inside nested subdirectories (e.g., `/DCIM/Camera/Trip/`) are not detected.
* **Root Cause:** Linux `inotify` is non-recursive by design. Watching a parent folder does not automatically register subfolders created after startup.
* **Remediation Steps:**
  1. In `SentinelObserver.kt`, intercept `IN_CREATE` when the target is a directory.
  2. Dynamically attach a new child `SentinelObserver` instance to the created subdirectory.
  3. **AI Action:** Update `README.md` -> *Implementation Logic* with the recursive observer architecture.

### 4. Background Service Killed by Battery Optimization
* **Symptom:** App stops detecting files after the screen has been off for a long period.
* **Root Cause:** OEM aggressive task killers terminate background services.
* **Remediation Steps:**
  1. Navigate to: `Settings > Apps > NoMedia Sentinel > Battery` and select **Unrestricted**.
  2. Ensure the persistent foreground notification remains enabled.
