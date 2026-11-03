# Contributing

Use JDK 17, Android SDK Platform 34, and Build Tools 34.0.0. Run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Please describe the failure being fixed and the phone/ROM on which it was tested. Existing host tests exercise policy and restart-result handling with fake init/PID commands; they do not prove sensor recovery.

## Device compatibility reports

Include model/codename, Android version, firmware/build identifier, stock/custom ROM, root-manager name/version, and whether the sensor failed before testing. State separately whether the app button, widget, tile, biometric prompt, and lock-screen unlock work. Report whether the fix lasts and how often it was repeated.

Use the app's **Collect diagnostics (read-only)** and **Save report…** actions. Review logs before posting publicly; remove identifiers and enrollment metadata you do not want to share. Never upload private signing keys, root-manager databases, fingerprint templates or an unfiltered personal bugreport.

The reset guard should only be expanded with supporting service-discovery data and on-device tests. Driver names alone are not enough to authorize sysfs rebinds. Hardware-reset contributions require review of the exact driver and device plus probe/remove, IRQ, regulator and open-handle behavior. Avoid broad process-name kills and shared-controller unbinds.

Submit contributions under Apache License 2.0. Open an issue for support or a pull request for a specific change.
