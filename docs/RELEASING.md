# Building and sharing an APK

## Debug APK for testing

1. Sync and run the project successfully in Android Studio.
2. Choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. Android Studio's notification offers **Locate**. The common output path is `app/build/outputs/apk/debug/app-debug.apk`.
4. This debug APK is signed with a development key and is intended for personal testing. Share it directly for quick tests, or attach it to a clearly labeled prerelease if appropriate. The app ID must remain the same to install an update over the previous copy.

## Signed release APK/AAB

1. Choose **Build → Generate Signed Bundle / APK**.
2. Select **APK** for direct installation/sharing, or **Android App Bundle** for Google Play.
3. Create or select a release keystore. Keep the keystore and its passwords private and backed up. Do not put them in Git, email, or this project folder unless locally protected.
4. Choose the release build variant and finish the signing wizard.
5. Test the signed artifact on a device before distributing it. Increase `versionCode` for each published update.

## GitHub Release

1. Push the source code to GitHub without generated APKs or signing secrets.
2. Open the repository's **Releases → Draft a new release**.
3. Create a version tag such as `v1.0.0`, write release notes, and attach the signed APK as a release asset.
4. Publish the release. Users download the APK from the release page; the file is not stored in the Git source tree.
