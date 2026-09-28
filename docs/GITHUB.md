# Publishing FlashForge source on GitHub

## Before the first push

1. Install Git for Windows and create an empty GitHub repository. Do not initialize it with a README if you already have this project locally.
2. Keep the full Android project folder. Important source files include `settings.gradle.kts`, root `build.gradle.kts`, the `app/` module, Gradle wrapper files, `gradle.properties`, the logo resources, and documentation.
3. Ensure the project root includes the Gradle wrapper scripts and files. Commit `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle/wrapper/gradle-wrapper.properties` so another developer and GitHub Actions can run the same Gradle version. The wrapper JAR is not a secret and should not be omitted.
4. `.gitignore` excludes generated build folders, APK/AAB artifacts, SDK-local paths, and signing files. Keep the ignore rules.
5. Use `git status` to inspect what will be uploaded. Do not commit `local.properties`, `.jks`/`.keystore` files, `key.properties`, passwords, or personal access tokens.

## PowerShell commands

Run in Android Studio Terminal or PowerShell from the project root:

```powershell
git init
git add .
git status
git commit -m "Initial FlashForge Android project"
git branch -M main
git remote add origin https://github.com/YOUR-NAME/FlashForge.git
git push -u origin main
```

Replace `YOUR-NAME` with your GitHub username or organization. GitHub will prompt for browser authentication if needed. For later code changes:

```powershell
git add .
git commit -m "Describe the change"
git push
```

## APKs and releases

Do not put APKs in normal source commits: they are generated, can make Git history large, and `.gitignore` intentionally excludes them. Build a signed release APK/AAB, then create a GitHub Release and upload the APK as a release asset. A debug APK is for testing and should not be represented as a production release. Never upload your signing keystore or passwords; keep encrypted backups somewhere private.
