# Xperia Fingerprint Reset

Restore fingerprint functionality without rebooting by restarting the fingerprint HAL on a rooted Sony Xperia 1 III. Built with Kotlin and Jetpack Compose, with an app button, home-screen widget, and Quick Settings tile.

**User-tested:** the project owner confirmed on November 3, 2026 that revision 002 works as intended on their Xperia. Exact firmware, root-manager version, and individual feature test results have not been recorded. Wider Sony compatibility has not been established.

## Compatibility

| Device / configuration | Status |
| --- | --- |
| Xperia 1 III (`pdx215`), rooted | Owner reports revision 002 works |
| Android 11–13 | Implemented version range; every release/firmware combination has not been tested |
| Other Sony models | Unsupported by the current reset action; diagnostic reports and contributions welcome |
| Unrooted devices | Reset unavailable |

The model guard includes XQ-BC52, XQ-BC62, XQ-BC72, SO-51B and SOG03; this list is not a claim that every regional model has been tested.

**Root is required. This implementation restarts the fingerprint HAL; it does not perform a hardware bus power-cycle.** A successful restart proves the process changed, not that the fingerprint sensor recovered. Test actual authentication afterward.

## Install or build

For personal sideload testing, install `APK/XperiaFingerprintReset_rev_002.apk`. It is debug-signed. This revision adopts the requested package name **com.xperia.fpreset**, so it installs alongside revision 001 rather than updating it. Remove the older app if you do not want duplicate widgets/tiles. No private signing key is included.

For building, open this folder in Android Studio, use JDK 17, and install SDK Platform 34 / Build Tools 34.0.0. Allow Gradle to download dependencies from Google Maven and Maven Central. No JitPack is used.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows: use `gradlew.bat` instead of `./gradlew`. Output: `app/build/outputs/apk/debug/app-debug.apk`.

Pinned build versions: Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24, Compose compiler 1.5.14. Minimum API 30; compile/target API 34. Target 34 is a build configuration, not a claim of tested Android 14 sensor support. The reset action remains restricted to the Xperia 1 III on API 30–33. Android 14 uses the declared shortService foreground-service type with a timeout handler.

## What changed

- Root status, HAL process state and PID snapshot on the main screen.
- Refresh status / kernel logs action and filtered last 15 kernel-log lines when permitted.
- Structured old/new PID reset result; completion toast for every entry point.
- Streaming reset-shell console output, bounded output capture, scrollable report.
- System light/dark theme, dynamic colors on Android 12+, confirmation haptic while the UI is present.
- Compact 1×1-requested widget (actual cell sizing depends on launcher); Quick Settings tile.
- Requested package name and core file names.

## Use

1. Phone must already be rooted with working `su` (Magisk, KernelSU or APatch). The app does not root it.
2. Unlock using PIN/password. Open the app, tap **Refresh status / kernel logs**, and grant the root manager request.
3. Tap **Reset fingerprint sensor**. The operation discovers the installed init service and verifies a new stable PID. It does not promise a 1.5-second completion time.
4. Tap **Test fingerprint**, then separately test lock-screen unlock.
5. Add **Reset FP** from the home screen widget picker or add the Quick Settings tile. A locked tile requests unlock; a locked widget request is refused.
6. If recovery fails, use **Collect diagnostics (read-only)** and **Save report…**. Each operation starts a fresh report, so export the reset report first if needed.

Status is a snapshot, not continuous monitoring. A running PID does not prove responsiveness. No automatic wake-reset service is included: a PID-only test cannot identify every hung sensor, and repeated resets can interfere with authentication. Root authorization and SELinux behavior vary by firmware; other configurations still need real-phone testing.

## Deliberate corrections to the attached specification

The complete attachment was reviewed. Its commands are proposals, not measured device data. See `SPEC_REVIEW.md` for detailed findings.

- No wildcard unbind/rebind or direct GPIO writes: exact driver/device and remove/probe behavior are unverified.
- No unconditional `pkill -9 -f` after restarting the service: that could kill the new instance and can match command lines containing the search text.
- No guessed service name: parse init declarations and require one eligible fingerprint HAL.
- No success from merely finding a PID: require a changed PID stable for three polls.
- No claimed framework reset from a `pgrep` check. Framework reconnection is left to Android; authentication is the end-to-end test.
- No unverified Sony diagnostic activity or assertion that opening settings reprobes hardware. **Open security settings** is navigation only.
- No Shizuku reset transport: no supported non-root reset command has been established for this firmware.

The fingerprint service declaration must use a supported HIDL executable in `/vendor/bin/hw` or `/odm/bin/hw`. The parser rejects ambiguous candidates and restart-sensitive init options (`critical`, `reboot_on_failure`, `onrestart`, `override`, `oneshot`). It is deliberately conservative, not a complete init interpreter. Custom layouts may need review.

## Privacy and behavior

No Internet permission or telemetry. Root processes run off the UI thread. One foreground worker serializes entry points, keeps a short wake lock, and logs failures without silently escalating. It does not restart system_server, change SELinux mode, clear enrollments or bypass biometric lockout. Android backup and device-transfer exclusions are configured.

The latest report is private app data until explicitly exported with Android's file picker. Reports can contain firmware and enrollment metadata; review before sharing. Root-command timeout does not undo a request already sent to init; avoid immediately retrying an uncertain result. A later APK signed with a different key may require uninstalling this revision, deleting its private report.

## Files

- `MainActivity.kt`: UI, status, system biometric prompt, report export.
- `FingerprintResetManager.kt`: discovery, status, kernel logs, restart pipeline, old/new PID result.
- `ResetService.kt`: serialized foreground lifecycle and completion notifications/toast.
- `FpResetTileService.kt`, `FpResetWidget.kt`: quick entry points.
- `HalPolicy.kt`, `RootShell.kt`, `ResetStore.kt`: validation, execution, observable state.

`VALIDATION.md` records automated checks and the owner-reported phone test. See `CONTRIBUTING.md` for testing and compatibility contributions.

## Sources

- Android init control/service behavior: https://android.googlesource.com/platform/system/core/+/master/init/README.md
- HAL death/reconnection example: https://android.googlesource.com/platform/frameworks/base/+/ebddcea829cac9d5975aa52f22b002f2180b432e/services/core/java/com/android/server/biometrics/sensors/fingerprint/hidl/Fingerprint21.java
- Driver binding and callbacks: https://www.kernel.org/doc/html/latest/driver-api/driver-model/binding.html
- Android foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Compose/Kotlin compatibility: https://developer.android.com/jetpack/androidx/releases/compose-kotlin

## Download

The owner-tested APK is available at [`APK/XperiaFingerprintReset_rev_002.apk`](APK/XperiaFingerprintReset_rev_002.apk). Open the file on GitHub and use **Download raw file**, then install it on your phone. It is a debug-signed build for sideloading. The SHA-256 checksum is in [`SHA256SUMS.txt`](SHA256SUMS.txt).

## Development and license

Pushes and pull requests run the Android build, tests and lint through GitHub Actions. Its downloadable artifacts are debug-signed development builds. Workflow builds may use a different debug key from the owner-tested APK, so switching between them can require uninstalling first.

Source code is licensed under [Apache License 2.0](LICENSE). See [THIRD_PARTY.md](THIRD_PARTY.md) for dependencies. This is an independent community project, unaffiliated with Sony.
