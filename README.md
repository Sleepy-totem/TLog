# TLog

Personal time-logging Android app. Clock in/out, view the weekly timecard, and export a pre-filled `Standard_TS_Mon-Sun.xlsx` timesheet to `Documents/TLog/` on the device.

## Stack

- Kotlin + Jetpack Compose (Material 3)
- Room for persistence, DataStore for settings
- Navigation Compose
- Custom lightweight xlsx filler (no Apache POI — just zip + XML)
- `minSdk 26`, `targetSdk 35`

## One-time setup (Windows)

```powershell
.\setup.ps1
```

This downloads/extracts:

- Gradle 8.10.2 → `.gradle-dist/`
- Gradle wrapper → `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Android command-line tools + `platforms;android-35` + `build-tools;35.0.0` → `android-sdk/`
- Writes `local.properties`

Prereq: JDK 17 (Temurin) — you already have it at `C:\Program Files\Eclipse Adoptium\jdk-17.0.14.7-hotspot\`.

## Build the APK

```powershell
.\build.ps1                      # debug APK — fine for personal install
.\build.ps1 -Variant release     # smaller, minified; signed with your own keystore
```

Both paths copy the final APK to the project root as **`TLog.apk`**.

First `release` build generates `tlog-release.keystore` automatically (password `tlogkey`). Keep that file — every future release build must use the same keystore, otherwise Android will refuse to upgrade the installed app over a different signature.

## Install on the Samsung S26 Ultra (no USB tools needed)

1. Transfer `TLog.apk` to the phone — USB drag-and-drop to `Downloads/`, email attachment, Google Drive, whatever is easiest.
2. On the phone open **Files** / **My Files** and tap `TLog.apk`.
3. Android will ask to allow installs from your file manager the first time:
   **Settings → Apps → Special access → Install unknown apps** → pick your file manager → **Allow from this source**. Go back and tap `TLog.apk` again.
4. Tap **Install**. Done — icon appears in the app drawer.

To update later: build again, transfer the new `TLog.apk`, tap install. If you ever switch between `debug` and `release` variants, uninstall the old one first (they have different package IDs / signatures).

## Project layout

```
TLog/
├── Standard_TS_Mon-Sun.xlsx          ← master template (copied to app/src/main/assets/)
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/Standard_TS_Mon-Sun.xlsx
│       ├── kotlin/com/tlog/
│       │   ├── MainActivity.kt
│       │   ├── TLogApp.kt
│       │   ├── data/           ← Room entity/dao/db, DataStore settings
│       │   ├── export/         ← XlsxExporter (fills the template)
│       │   ├── ui/             ← Home, Clock, TimeCard, Settings screens
│       │   ├── util/WeekHelper.kt
│       │   └── viewmodel/TLogViewModel.kt
│       └── res/                ← themes, icon, FileProvider paths
├── gradle/libs.versions.toml
├── settings.gradle.kts
├── build.gradle.kts
├── setup.ps1
├── build.ps1
├── install.ps1
└── README.md
```
