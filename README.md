# Downly

Downly is an Android media downloader UI built with Jetpack Compose and the published YouTubeDL Kotlin Android Maven artifacts.

## Features

- Paste an HTTP/HTTPS media URL or paste from the clipboard.
- Fetch media title, uploader, and duration before downloading.
- Download video in Best, 1080p, 720p, or 480p quality.
- Extract audio as MP3.
- View active progress, completion status, and errors in Activity.
- Persist download history locally and retry downloader initialization from Settings.
- Keep downloaded files in the app-specific `Downly` folder.

## Requirements

- Android Studio with Android SDK 37 installed.
- JDK 17.
- The `youtubedl-kotlin` modules must be published to your local Maven repository before building.
- Android 11 or newer (minSdk 30).
- Internet access for resolving Maven Central dependencies and downloading media.

## Build

Before building, clone the `youtubedl-kotlin` library repository and publish its `library`, `ffmpeg`, and `aria2c` modules to your local Maven repository. Then open this project in Android Studio and sync Gradle, or run the build command below. A fresh clone does not contain those local Maven artifacts, so dependency resolution will fail until they are published locally.

From a terminal with the Android SDK configured:

```bash
./gradlew :app:assembleDebug
```

On Windows, use:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Maven dependencies

The app currently resolves these artifacts from `mavenLocal()` (not Maven Central):

- `io.github.darkstar085.youtubedl-kotlin:library:1.0.0`
- `io.github.darkstar085.youtubedl-kotlin:ffmpeg:1.0.0`
- `io.github.darkstar085.youtubedl-kotlin:aria2c:1.0.0`

Publish these exact coordinates from the `youtubedl-kotlin` project to `~/.m2/repository` before building Downly. Other machines and CI need the same artifacts published locally, or the dependencies must be published to a remote Maven repository and the Gradle repository configuration updated.

## Storage and permissions

Downloads are stored in app-specific storage under `Android/data/com.sipun.downly/files/Downly`. No broad storage permission is requested. Clearing app data or uninstalling the app removes these files.

## Notes

- Availability depends on the source site's support in the bundled yt-dlp runtime and may change as sites change.
- Some media may require authentication or may be unavailable.
- Download only media you own or are authorized to save, and follow the source platform's terms.
- The repository intentionally does not include an automatic GitHub Actions build workflow.

## Runtime limitations

- Downloads currently run in the app process; Android may stop them if the process is killed. Persistent background downloads require a foreground service or an equivalent durable-work implementation.
- The app-specific download folder may not be directly browsable by every file manager. Use Android's app storage tools or an appropriate file-sharing flow to access saved files.
- A fresh-clone Gradle build and test run has not yet been verified for this repository.

## Local release signing and optimized builds

Release signing credentials are loaded from a local `keystore.properties` file at the repository root, with environment-variable fallback. This file and local keystore files are ignored by Git; never commit real passwords or a private JKS.

1. Copy `keystore.properties.example` to `keystore.properties`.
2. Fill in the real `storeFile`, `storePassword`, `keyAlias`, and `keyPassword` values.
3. Put the JKS at the local path set by `storeFile` (relative paths are resolved from the repository root).
4. Build a signed release with `./gradlew :app:assembleRelease`; use `./gradlew :app:bundleRelease` for an AAB.

A release build requires valid local signing credentials; no real keystore or secret is checked into the repository. Environment-variable alternatives are `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.

The release variant enables R8 minification/obfuscation and resource shrinking using the optimized Android default rules plus `app/proguard-rules.pro`. Test the release build on a device before distribution, especially the native yt-dlp/FFmpeg runtime paths.
