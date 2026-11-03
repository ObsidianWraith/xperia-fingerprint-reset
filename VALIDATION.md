# Revision 002 validation

Build date: November 3, 2026.

- Debug APK assembly: passed.
- Unit tests: 11 passed, no failures/errors/skips.
- Android lint: 0 errors, 3 warnings. Warnings concern newer dependency versions.
- APK signing verification: passed (debug signing).
- Package: com.xperia.fpreset. Version: 1.1-rev002 (code 2). Minimum API 30, target API 34.

Tests execute the generated restart shell against fake init/PID tools, not actual hardware. Cases cover stable changed PID success and old/new output markers, unchanged PID rejection, crash-loop rejection, control-property denial, init candidate parsing, unsafe options, ambiguous candidates, unrelated binaries, and shell quoting.

## Owner-reported phone validation

On November 3, 2026 the project owner reported: "I have confirmed that the app works as intended."

This is evidence of successful use on their Xperia, not a test matrix for every Sony phone. Exact build fingerprint, root-manager version, repeated recovery counts, and separate widget/tile/lock-screen results have not been recorded. No emulator or developer-controlled physical-device test was performed. Hardware bus reset is not implemented.

## Phone checks

1. Unlock using PIN and open the app; refresh status and grant root.
2. Verify the discovered service/PID and save the report.
3. Reset once. Confirm a changed PID is reported; authenticate using Test fingerprint.
4. Separately test lock-screen fingerprint authentication.
5. Test widget and tile, including locked-device behavior, denied root/notification permissions, repeated taps and screen rotation.
6. On failure, export the reset log before starting fresh diagnostics. Collect diagnostics and export those too.

Hardware power-cycle and optional non-root/automatic-wake resets are not implemented; see SPEC_REVIEW.md. Do not infer a working sensor from a passed build or a running PID.

The included APK is signed with a development debug key. No private signing key is included. The package name differs from revision 001 and therefore installs separately. Future builds with different signing keys may require uninstalling this copy; export its report first.
