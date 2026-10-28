# NoMedia Sentinel: Real-Time Storage Watchdog & Culprit Identification Engine

## 1. Problem Statement
In Android OS, any directory containing a file or folder named `.nomedia` instructs the operating system MediaScanner to ignore that entire directory tree. When an unwanted app, background service, or rogue cleaner script writes this file into standard shared photo directories (such as `DCIM/Camera`, `Pictures`, or `Download`), the following critical issues occur:
* The system media provider database immediately drops all photos and videos within that tree from its index.
* The default gallery applications show zero photos, leading users to believe their images have been permanently wiped.
* Android process sandboxing hides which specific app created the file, preventing the user from identifying and uninstalling the source app.

## 2. Robust Solution Proposed
NoMedia Sentinel operates as a low-overhead, real-time background watchdog:
1. **Linux inotify Monitoring:** Uses Android FileObserver hooked into the Linux filesystem kernel to detect file creations (`CREATE` and `MOVED_TO`) in target media folders.
2. **Sub-100ms Autonomous Purge:** The millisecond a `.nomedia` file or folder appears, Sentinel recursively deletes it before the MediaStore service can process the exclusion.
3. **Forensic Culprit Identification:** Audits `UsageStatsManager` events within a 5-second temporal window (`[-4000ms, +1000ms]`) relative to file creation to identify the foreground or background package.
4. **All Files Access Cross-Audit:** Inspects the system package registry for third-party apps holding `MANAGE_EXTERNAL_STORAGE` to highlight suspicious broadcasters.
5. **Autonomous Re-indexing:** Dispatches `MediaScannerConnection.scanFile()` to restore gallery visibility immediately without a phone reboot.

## 3. Implementation Logic
* **`WatchdogService`:** A foreground service that maintains file observers on `/sdcard/DCIM/Camera`, `/sdcard/Pictures`, and `/sdcard/Download`.
* **`SentinelObserver`:** Subclasses Android `FileObserver`. Intercepts events and executes: `Match -> Trace Culprit -> Recursive Purge -> Trigger MediaScanner`.
* **`CulpritTracer`:** Correlates file creation timestamps against `UsageEvents.Event.ACTIVITY_RESUMED` and `FOREGROUND_SERVICE_START` transitions.
* **`BootReceiver`:** Automatically starts the watchdog service when the phone boots via `ACTION_BOOT_COMPLETED`.

## 4. Dependencies
Defined via Gradle Version Catalog (`gradle/libs.versions.toml`):
* AndroidX Core KTX (v1.15.0)
* AndroidX Lifecycle Service (v2.8.7)
* AndroidX AppCompat & Google Material Components (v1.7.0 / v1.12.0)
* Kotlinx Coroutines Android (v1.9.0)
* JUnit4 (v4.13.2) & Google Truth (v1.4.4)

## 5. Possible Challenges and Workarounds
* **Scoped Storage (Android 11+):** Standard apps cannot modify dot-files in shared storage. *Workaround:* The app declares `MANAGE_EXTERNAL_STORAGE` with a direct one-tap shortcut to the system authorization screen.
* **Aggressive OEM Task Killers (ColorOS, OxygenOS, HyperOS):** Background services get killed after screen timeout. *Workaround:* Configured as a foreground service with persistent low-importance notification and `dataSync` service type.
* **Silent Background Workers:** Background services might not trigger an activity resume. *Workaround:* CulpritTracer falls back to listing all non-system apps holding broad storage write permissions.

## 6. Verification Process
1. Build and install the APK.
2. Open the app and grant **All Files Access** and **Usage Access**.
3. Tap **Start Watchdog Daemon**.
4. Test via ADB terminal: `adb shell touch /sdcard/DCIM/Camera/.nomedia`
5. Verify that Sentinel deletes the file within 100ms and displays the culprit package name in the audit log.

## 7. Future Scope
* **Filesystem Immutability Lock:** Pre-creating an unmodifiable read-only directory named `.nomedia` to force filesystem I/O rejection (`EEXIST`).
* **Package Quarantine:** Integration with Shizuku or Device Owner APIs to freeze offending applications automatically.
