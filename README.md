# FlashForge

A native Android flashlight utility built with Kotlin, Jetpack Compose, Material 3, CameraManager/Camera2, coroutines, StateFlow and Preferences DataStore. The project includes the FlashForge launcher logo supplied by the project owner.

> This is an actively developed prototype. Verify hardware-dependent behavior on a physical device before relying on it. Do not use it as a replacement for emergency equipment.

## Current functionality

- Responsive Compose navigation, dark/light theme, home torch control and stop controls.
- Runtime camera permission request when a torch action is used; flash-capability detection and graceful errors.
- Coroutine-driven SOS, strobe, rapid/slow beacon patterns, with flashing-light safety confirmation.
- Morse text preview/conversion and adjustable timing.
- Custom ON/OFF timeline: add, duplicate, remove, set durations, generate random patterns, preview, and save/load/rename/delete via DataStore.
- Timer presets and custom timer.
- Full-screen screen lamp with color presets, brightness, and pulse control.
- Beat mode performs local microphone amplitude analysis only while active; audio is not saved.
- Optional vibration synchronized to ON segments in custom pattern preview.
- Device summary and saved appearance/haptics preferences.

## Platform limits and features not included yet

This prototype does not include shake-to-toggle, home-screen widgets, a Quick Settings tile, a system notification listener, clock/countdown patterns, emergency audio, or a complete hardware diagnostics/torch-strength UI. Android restricts system notification interception; FlashForge does not claim to detect all system notifications. Beat mode is a simple local amplitude threshold, not full music analysis. Hardware availability varies by device.

## Open and run

1. Install Android Studio and JDK 17.
2. Open the project root (the directory containing `settings.gradle.kts`).
3. Keep the Gradle wrapper files in the repository (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle-wrapper.properties`) so builds are reproducible.
4. Sync Gradle, create an emulator or connect an Android device, select it, and click **Run**.
5. A real device with a rear LED flash is required to test the flashlight. An emulator is useful for UI testing but may report no flash.

The Gradle memory and AndroidX settings are in the root `gradle.properties`. Do not commit machine-specific `local.properties`.

## Build an APK

For a local debug APK in Android Studio use **Build → Build Bundle(s) / APK(s) → Build APK(s)**. The output is normally `app/build/outputs/apk/debug/app-debug.apk`.

Do not commit generated APK/AAB binaries to the source repository. To distribute an APK, create a GitHub Release and attach the APK as a release asset (see [`docs/RELEASING.md`](docs/RELEASING.md)). For public/production distribution, generate a signed release APK or AAB and protect the signing key.

## GitHub quick start (Windows PowerShell)

From the project root:

```powershell
git init
git add .
git status
git commit -m "Initial FlashForge Android project"
git branch -M main
git remote add origin https://github.com/YOUR-NAME/FlashForge.git
git push -u origin main
```

Create the empty GitHub repository first. Review `git status` before committing: the `.gitignore` excludes build output, local SDK paths, APK/AAB files, and signing secrets. Never commit passwords, a keystore, or private credentials. Read [`docs/GITHUB.md`](docs/GITHUB.md) for a detailed checklist.

## Project map

- `app/src/main/java/com/flashlightstudio/MainActivity.kt` — Compose screens and navigation
- `app/src/main/java/com/flashlightstudio/viewmodel/StudioViewModel.kt` — feature state, coroutine effects, Morse, timers, beat processing
- `app/src/main/java/com/flashlightstudio/hardware/TorchController.kt` — CameraManager flashlight access/capability checks
- `app/src/main/java/com/flashlightstudio/data/` — pattern models and DataStore preferences
- `app/src/main/res/mipmap-*` — density-specific launcher logo assets
- `app/src/main/res/values/strings.xml` — app label (`FlashForge`)
