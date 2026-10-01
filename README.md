# 📱 NextBridge-Android

> Native Android wrapper and hardware runtime bridge for NextBridge built with Capacitor, Android SDK 36, and Gradle.

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Java-ED8B00?logo=openjdk&logoColor=white)](https://www.java.com/)
[![Gradle](https://img.shields.io/badge/Gradle-8.14.5-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![Android SDK](https://img.shields.io/badge/Target_SDK-36-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/16)
[![Capacitor](https://img.shields.io/badge/Capacitor_Android-8.5-119EFF?logo=capacitor&logoColor=white)](https://capacitorjs.com/)
[![Status](https://img.shields.io/badge/Status-Active-brightgreen)]()

---

## Description

NextBridge-Android is the production native Android wrapper repository for NextBridge, providing a hardened, hardware-accelerated runtime bridge powered by Capacitor and Gradle. Built against Android SDK 36 (target/compile) with a minimum requirement of Android 7.0 (API 24), it wraps the NextBridge educational web application within a high-performance native WebView container. The wrapper embeds custom native Java plugins for foreground download synchronization with persistent notification controls, automatic Picture-in-Picture (PiP) multitasking, system-bar immersive controls, and secure in-app APK update installation via Android FileProvider.

---

## Key Features

- **Hardware-Accelerated Web Runtime**: High-performance Android WebView environment configured with 32-bit ARGB window buffers (`PixelFormat.RGBA_8888`), hardware acceleration (`LAYER_TYPE_HARDWARE`), and unlocked screen capture capabilities (`FLAG_SECURE` cleared for educational notes and study capture).
- **Native Foreground Download Service**: Dedicated Android background service (`DownloadForegroundService`) running under `foregroundServiceType="dataSync"` with multi-threaded chunk downloading, pause/resume/cancel actions, real-time transfer speed calculation, ETA tracking, and persistent notification channel controls (`nextbridge_downloads_channel`).
- **Automated Picture-in-Picture (PiP)**: Seamless multitasking powered by Android 8.0+ / 12+ PiP APIs with 16:9 aspect ratio enforcement, automatic entry on user home gesture (`onUserLeaveHint`), and bidirectional JavaScript event bridges (`app:pip-mode-change`).
- **Immersive Mode Plugin**: Custom native plugin (`ImmersiveModePlugin`) handling full-screen sticky immersive viewing with modern Android 11+ `WindowInsetsControllerCompat` and legacy fallback flags for distraction-free video playback.
- **Document Sharing & FileProvider Integration**: Native file sharing via Android Intent chooser (`Intent.ACTION_SEND`), direct external PDF reader launching (`Intent.ACTION_VIEW`), and modern scoped storage exports via Android MediaStore (`MediaStore.Downloads`).
- **In-App Package Installer Bridge**: Base64 APK decoding and automated package installation (`installApk`) via Android FileProvider and `application/vnd.android.package-archive` intents with `REQUEST_INSTALL_PACKAGES` permission.
- **Capacitor Android Plugin Suite**: Pre-integrated Capacitor 8 plugin bridges including `@capacitor/app`, `@capacitor/device`, `@capacitor/filesystem`, `@capacitor/local-notifications`, `@capacitor/network`, `@capacitor/screen-orientation`, `@capawesome/capacitor-badge`, and `@capgo/capacitor-updater`.

---

## Tech Stack

| Category | Technologies |
| :--- | :--- |
| **Core Runtime & Language** | Android Native SDK, Java 17 / OpenJDK |
| **Build System** | Gradle 8.14.5, Android Gradle Plugin (AGP) 8.13.0 |
| **Target Specifications** | Compile SDK 36, Target SDK 36, Min SDK 24 (Android 7.0+) |
| **AndroidX Architecture** | AppCompat 1.7.1, Activity 1.11.0, CoordinatorLayout 1.3.0, Core-SplashScreen 1.2.0, Webkit 1.14.0 |
| **Mobile Bridge Framework** | `@capacitor/android:8.5.2`, `@capacitor/core:8.5.2` |
| **Custom Native Plugins** | `DownloadServicePlugin`, `ImmersiveModePlugin`, `DownloadForegroundService` |
| **Testing Harness** | JUnit 4.13.2, AndroidX Test Runner 1.3.0, Espresso Core 3.7.0 |

---

## Getting Started

### Prerequisites

- **Android Studio**: Ladybug | 2024.2.1 or newer
- **JDK**: Java Development Kit 17 or higher
- **Android SDK**: Platforms and Build-Tools for API Level 36 installed
- **Sibling Project**: Web assets compiled from the sibling `NextBridge` repository

### Installation & Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/AryansDevStudios/NextBridge-Android.git
   cd NextBridge-Android
   ```

2. Link compiled web assets from the sibling `NextBridge` project:
   ```bash
   # From your local NextBridge web project:
   npm run build:all
   npx cap sync android
   ```

3. (Optional) Place your `google-services.json` inside the `app/` directory if configuring Firebase Cloud Messaging / push notifications.

### Gradle Build Commands

| Command | Description |
| :--- | :--- |
| `./gradlew clean` | Cleans root and module build artifacts. |
| `./gradlew assembleDebug` | Compiles and packages the debug APK (`app/build/outputs/apk/debug/app-debug.apk`). |
| `./gradlew assembleRelease` | Compiles the release APK ready for signing and deployment. |
| `./gradlew test` | Executes local unit tests using JUnit 4. |
| `./gradlew connectedAndroidTest` | Runs instrumented UI and integration tests on an active device or emulator via Espresso. |

*(On Windows PowerShell or Command Prompt, use `.\gradlew.bat <command>` instead).*

---

## Usage

### Running via Android Studio
1. Open Android Studio and select **Open**, navigating to the `NextBridge-Android` directory.
2. Allow Gradle sync to complete.
3. Select an emulator or connected physical Android device running Android 7.0 (API 24) or higher.
4. Click **Run 'app'** (`Shift + F10`).

### Deploying via ADB
To install the compiled debug build onto a connected device via Android Debug Bridge:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Inspecting WebView Console
To debug web runtime errors and inspect real-time Capacitor bridge events:
1. Open Google Chrome on your development machine.
2. Navigate to `chrome://inspect/#devices`.
3. Locate your connected device running `NextBridge` and click **inspect**.

---

## Project Structure

```text
NextBridge-Android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/nextbridgedev/app/
│   │   │   │   ├── DownloadForegroundService.java  # Persistent notification foreground service
│   │   │   │   ├── DownloadServicePlugin.java      # Capacitor bridge for downloads, PDF share & APK install
│   │   │   │   ├── ImmersiveModePlugin.java        # Fullscreen system bars & PiP controller
│   │   │   │   └── MainActivity.java               # BridgeActivity entry point, WebView config & PiP listener
│   │   │   ├── res/                                # Android app icons, splash screens, XML layouts, file paths
│   │   │   └── AndroidManifest.xml                 # App permissions, foreground services, FileProvider metadata
│   │   ├── test/                                   # Unit test suites
│   │   └── androidTest/                            # Android instrumented tests (Espresso)
│   ├── build.gradle                                # App-level Android build script (applicationId, SDK targets)
│   └── proguard-rules.pro                          # ProGuard obfuscation and keep rules
├── gradle/wrapper/                                 # Gradle wrapper binaries and gradle-wrapper.properties (8.14.5)
├── build.gradle                                    # Project-level Gradle build configuration (AGP 8.13.0)
├── capacitor.settings.gradle                       # Auto-generated Capacitor plugin module mappings
├── gradle.properties                               # JVM memory allocation and AndroidX settings
├── gradlew / gradlew.bat                           # Gradle wrapper execution scripts for Unix / Windows
├── settings.gradle                                 # Project and module definitions
└── variables.gradle                                # Global Android SDK versions and dependency specifications
```

---

## Contributing

Contributions and bug reports are welcome. When proposing changes to native Java plugins or Gradle dependencies, please ensure compatibility across Android SDK levels 24 through 36 and verify that build tasks pass via `./gradlew test`.

---

## License

Proprietary — All rights reserved by [AryansDevStudios](https://github.com/AryansDevStudios).
