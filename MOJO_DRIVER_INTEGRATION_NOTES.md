# Mojo-style custom driver support integration notes

This source archive overlays the supplied Mojo-style custom driver support patch onto the supplied ZalithLauncher source tree.

Important: this integration has not been successfully compiled or runtime-tested. It is source code, not an APK. Build environment used to prepare it lacked network access to download Gradle and did not have the required Android SDK/NDK installed.

Expected build setup based on the project's workflow: JDK 17, Android SDK, NDK 25.2.9519653, then run:

```bash
./gradlew ZalithLauncher:assembleDebug -Darch=arm64
```

Review the patch and test the resulting build before installing it.
