# Journal

A quiet, private journal for Android. Write about your day, attach photos, add a place, and find old entries later. Your journal stays on your device and works offline.

> **Status:** Personal project. The project source is available here; build and test it on your device before relying on it as the only copy of important writing.

## Features

- **Private and offline:** Entries, photos, and profile details are stored in the app's private on-device storage. The app does not use accounts, analytics, cloud sync, or an internet connection.
- **Journal feed:** Entries appear newest first, with a date, writing preview, optional location, and photo thumbnails.
- **Write freely:** Add a title, long-form text, multiple photos, and an optional location. The app records the entry's date and time automatically.
- **Browse and manage entries:** Open an entry to read it in full, view photos, edit it, or delete it.
- **Search and filters:** Search entry text, titles, and locations. Filter by date range and location.
- **Personal profile:** Save optional profile details and a photo locally.
- **Light and dark appearance:** Choose Light, Dark, or System mode.
- **Portable backups:** Export entries, profile details, timestamps, locations, and attached photos to a versioned JSON backup. Import a backup by merging entries or replacing the current journal.
- **Calm interface:** Minimal layout, blue accent color, serif journal typography, and translucent glass-style surfaces.

## Screenshots

Add screenshots here when available. For example:

```md
![Journal feed](docs/screenshots/feed.png)
```

## Requirements

- Android 8.0 (API 26) or later
- Android System WebView
- Android Studio (recommended), or JDK 17 and Android SDK Platform 35 for command-line builds

## Run the app

1. Clone or download this repository.
2. Open the `Journal` project folder in Android Studio.
3. Wait for Gradle sync and install any requested Android SDK components.
4. Select an Android device or emulator running Android 8.0 or later.
5. Press **Run**.

The app requests location permission only if you tap **Use current location**. You can also enter a location manually without granting permission.

## Build a debug APK

From the project root (`Journal`):

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a release build, configure an application ID and signing key in Android Studio. Review the target SDK and current Google Play requirements before publishing.

## Data and backups

Journal data is stored locally in the app's private storage using IndexedDB in Android WebView. Uninstalling the app removes that data. Export a backup before uninstalling or moving to another phone.

To move your journal:

1. On the old device, open **Settings → Export backup** and choose where to save the file.
2. Transfer the backup file to the new device.
3. Install the app, open **Settings → Import backup**, and select the file.
4. Choose **Merge** to add entries that are not already present, or **Replace** to replace the current journal with the backup.

Backups use UTF-8 JSON with the format identifier `journal-backup` and schema version `1`. The file contains a manifest, profile data, entries, timestamps, locations, and photo data URLs. Imports with an unsupported newer version or invalid structure are rejected. Keep backups in a place you control; they contain your private journal and photos.

## Privacy

The app has no `INTERNET` permission and does not include accounts, analytics, or cloud synchronization. Data stays on the device unless you explicitly export it. Location is optional: the app asks for permission only after you select **Use current location**. Manual location entry is also available.

## Known limitations

- The app uses device storage. Photos can use substantially more space than text, and available storage depends on the device.
- Very large backup imports are read into memory and may fail on devices with limited RAM.
- Unsaved drafts preserve text and location, but not newly selected photos.
- Current-location lookup depends on Android location permission and enabled location services.
- The included project has not been confirmed to build in every Android Studio or SDK environment.

## Project structure

```text
Journal/
├── app/
│   └── src/main/
│       ├── assets/www/index.html                 # App UI and journal logic
│       ├── java/com/example/journal/MainActivity.kt # Android WebView host and native bridges
│       └── res/                                   # App icon, strings, and themes
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

## License

No license has been added yet. Unless a license is included, normal copyright applies and others do not automatically have permission to reuse or redistribute the code. Add a license file before inviting outside contributions or reuse.
