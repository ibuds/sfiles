<div align="center">

# SFiles

**A fast, clean file manager for Android.**
Kotlin · Jetpack Compose · zero ads · zero uploads

[![Release](https://img.shields.io/github/v/release/ibuds/sfiles?style=for-the-badge&color=3DDC84&logo=android&logoColor=white)](https://github.com/ibuds/sfiles/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/ibuds/sfiles/total?style=for-the-badge&color=0A84FF)](https://github.com/ibuds/sfiles/releases)
[![Android 8+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#build)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)

[**Download**](https://github.com/ibuds/sfiles/releases/latest) ·
[**Features**](#features) ·
[**Build**](#build) ·
[**Architecture**](#architecture) ·
[**Support**](#support)

</div>

---

## Download

Grab the latest signed APK from the [Releases page](https://github.com/ibuds/sfiles/releases/latest) and open it on your phone. Android will ask you to allow installs from your browser or file app the first time.

Each release ships a `.sha256` file so you can verify the download:

```bash
shasum -a 256 -c SFiles-1.0.2.apk.sha256
```

## Screenshots

Captured on Android 8.0 (the minimum supported version) and Android 17.

| Home (Android 17) | Home, dark (Android 17) | Images (Android 17) |
| --- | --- | --- |
| <img src="docs/screenshots/android17-home.png" width="240"> | <img src="docs/screenshots/android17-home-dark.png" width="240"> | <img src="docs/screenshots/android17-images.png" width="240"> |

| Home (Android 8) | Folders (Android 8) | Selection (Android 8) |
| --- | --- | --- |
| <img src="docs/screenshots/android8-home.png" width="240"> | <img src="docs/screenshots/android8-folders.png" width="240"> | <img src="docs/screenshots/android8-selection.png" width="240"> |

## Features

### Browse

- Home screen with category shortcuts (Downloads, Docs, Images, Audio, Videos, Install files), recent files and storage usage
- Folder browser with breadcrumb, filter, sorting and list view
- Search across storage, with voice input
- Favorites on Home, plus Hide / Unhide for any file or folder
- Hidden files toggle (Hide renames an item to start with `.`) and a Hide file extensions toggle
- Light, dark and follow-device themes

### Operate

- Multi-select: long-press, then drag across rows to select a range
- Move, copy, share, delete, rename, compress (zip) and file details
- Several operations at once, each with its own card and notification, remaining time and item count
- Progress for copy, move, compress, delete and restore, in the app and in a notification, with a Cancel button
- Trash with 30-day retention (internal storage); permanent delete on removable drives

### Storage

- USB drives and SD cards shown with their filesystem (FAT32, exFAT, NTFS)
- Unmount and Format shortcuts for removable drives (long-press the drive on Home, or use the menu inside it). Android only allows these from system Settings, so SFiles opens the right screen
- Network storage over **FTP**, **SFTP** and **SMB (v2/v3)**: browse, open, download, upload, rename, delete

> SFiles never creates folders on removable drives on its own.

## Build

**Requirements:** JDK 17 and the Android SDK (platform 35, build-tools 35).

```bash
# point Gradle at your SDK
echo "sdk.dir=/path/to/android/sdk" > local.properties

./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK (needs signing config, see below)
```

The debug APK is written to `app/build/outputs/apk/debug/`.

### Release signing

Release builds read `signing/keystore.properties`, which is git-ignored:

```properties
storeFile=../signing/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file, `assembleRelease` produces an unsigned APK.

## Architecture

Sources live in `app/src/main/java/io/github/ibuds/sfiles/`.

```
sfiles/
├── MainActivity.kt        navigation, permission gate, voice search
├── HomeScreen.kt          home screen
├── BrowserScreen.kt       folder and category browser, selection mode, dialogs
├── SearchScreen.kt        storage-wide search
├── Fs.kt                  local file operations, volumes, trash, formatting
├── Remote.kt              FTP / SFTP / SMB layer
├── RemoteScreens.kt       network storage screens
├── OpService.kt           foreground service + task list behind progress cards
├── Common.kt · Ui.kt      shared header, menu, action bar, progress card
├── Theme.kt               palette and typography
└── Extra.kt · Picker.kt   trash, settings, folder picker
```

## Privacy & permissions

| | |
| --- | --- |
| **Permission** | "All files access" (`MANAGE_EXTERNAL_STORAGE`), because browsing and managing every folder is the core purpose of a file manager |
| **Network** | Nothing is uploaded anywhere. Network storage connects only to servers you add yourself |
| **Credentials** | Saved passwords are stored encrypted on the device |

## Support

SFiles is free and open source. If it saves you some time, you can say thanks:

<a href="https://trakteer.id/ibudsss"><img src="https://img.shields.io/badge/Buy%20me%20a%20cendol-Trakteer-C8102E?style=for-the-badge&logo=buymeacoffee&logoColor=white" alt="Buy me a cendol on Trakteer"></a>

Starring the repo and reporting bugs help just as much.

## License

[MIT](LICENSE). The optional "Standard Android font" is Roboto, also Apache 2.0.
