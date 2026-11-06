# SFiles

A fast, clean file manager for Android, built with Kotlin and Jetpack Compose.

## Features

- Home screen with category shortcuts (Downloads, Docs, Images, Audio, Videos, Install files), recent files and storage usage
- Folder browser with breadcrumb, filter, sorting and list view
- Multi-select: long-press, then drag across rows to select a range
- Move, copy, share, delete, rename, compress (zip) and file details
- Trash with 30-day retention (internal storage); permanent delete on removable drives
- USB drives and SD cards shown with their filesystem (FAT32, exFAT, NTFS)
- Network storage: FTP, SFTP and SMB (v2/v3) with browse, open, download, upload, rename, delete
- Hidden files toggle (rename anything to start with `.` to hide it)
- Search across storage, with voice input
- Light, dark and follow-device themes

SFiles never creates folders on removable drives on its own.

## Build

Requirements: JDK 17 and the Android SDK (platform 35, build-tools 35).

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

## Permissions

SFiles requests "All files access" (`MANAGE_EXTERNAL_STORAGE`) because browsing and managing every folder is the core purpose of a file manager. Nothing is uploaded anywhere; network storage connects only to servers you add yourself. Saved passwords are stored encrypted on the device.

## Project layout

| File | Purpose |
| --- | --- |
| `MainActivity.kt` | Navigation, permission gate, voice search |
| `HomeScreen.kt` | Home screen |
| `BrowserScreen.kt` | Folder and category browser, selection mode, dialogs |
| `Fs.kt` | Local file operations, volumes, trash, formatting |
| `Remote.kt`, `RemoteScreens.kt` | FTP / SFTP / SMB layer and screens |
| `Extra.kt`, `Picker.kt`, `SearchScreen.kt` | Trash, settings, folder picker, search |

## License

Apache License 2.0. See [LICENSE](LICENSE).
