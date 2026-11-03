# Review of the complete supplied specification

Revision 002 uses the uploaded specification to extend the app, while correcting unsupported recovery claims. The root cause described in the attachment remains a hypothesis; no logs, electrical measurements or inspected flex cable were supplied.

## Stage 1 — wildcard driver rebind

`/sys/bus/platform/drivers/*fp*` is not an exact sensor identity. Overlapping driver and device globs can select an object more than once. The loop does not verify the device's compatible string, its bound driver, or whether its remove/probe callbacks support rebinding. It attempts unbind before stopping the HAL and checking that open sensor handles are released. It suppresses errors and can leave a device unbound after a failed bind.

Linux binding calls driver-specific callbacks. Whether those callbacks pulse reset, cycle a regulator, preserve state, or fail is specific to the driver and kernel. The supplied script is not sufficient evidence of an electrical power-cycle.

Required before implementation: actual build fingerprint and kernel source/version, exact sensor device and bound-driver symlink, review of remove/probe/IRQ/regulator/file-handle handling, and controlled on-device testing. Never substitute unbinding the shared SPI/I2C bus controller.

## Stage 2 — service names

The supplied service list is a set of candidates. An init service name is distinct from the executable name. Revision 002 reads declarations from the installed vendor/odm init files rather than restarting all names in a guessed list. UID 0 does not guarantee SELinux permits the control-property operation.

## Stage 3 — unconditional force kill

The provided script does not check that a process is still the original stuck instance before killing. After a successful Stage 2 restart, Stage 3 can kill the newly started HAL. Broad `pkill -f` matches full command lines; scripts or wrapper shells containing the literal pattern can match as well. A blocked uninterruptible kernel wait is not guaranteed to be ended immediately by SIGKILL. A zombie is already exited and cannot be repaired by another kill. Init, rather than a universal hwservicemanager respawn guarantee, governs the service's process lifecycle.

Revision 002 therefore uses the targeted init restart and reports failure if its result cannot be verified. It does not add the broad kill step.

## Stage 4 — purported scheduler reset

The supplied commands sleep and find a matching PID. They do not explicitly clear the scheduler, compare old/new PIDs, prove HAL registration, or prove authentication works. A full-command-line PID search can also find the wrapper rather than the HAL.

Revision 002 uses an exact executable PID query, verifies change and stability, and offers a system biometric authentication test. It relies on normal framework HAL-death handling rather than undocumented Binder transactions or restarting system_server. Retail firmware may behave differently from AOSP.

## Stage 5 — non-root fallback

No evidence establishes that `com.sonymobile.devicesecurity.service/.DiagnosticActivity` exists, is exported, or performs a sensor reset on this firmware. Opening security settings is not proof of re-probe. Shizuku grants a shell transport, not unrestricted root permissions or a missing reset command. Revision 002 provides Security settings navigation, explicitly without claiming it resets the sensor.

## Unfulfilled scope

Hardware bus power-cycle, guaranteed framework recovery, a working non-root reset, optional automatic wake resets, and physical-device production validation are not delivered. This is a compiled, tested HAL recovery utility with diagnostics, not a verified cure for the sensor failure.

Next evidence: run **Collect diagnostics (read-only)** on the phone and export the report. That is needed to resolve the actual device/driver and decide whether a hardware-reset revision is feasible.
