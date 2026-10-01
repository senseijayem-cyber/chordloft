# Chordloft for Android

A songbook and chord app for worship bands. Songs, setlists and settings are stored on the phone and work offline.

## What's in the app

- Song library with chords above the lyrics, sections and song order
- Transpose, capo with easier-shape suggestions, Nashville numbers
- Chord diagrams for guitar, ukulele and keys
- Setlists with a key and song order for each service
- Auto-scroll, metronome, Stage mode (full screen, screen stays on)
- Two-column charts on tablets
- Import ChordPro and chords-over-lyrics text files, or share a chart to Chordloft from any app

## How it is built

The song screens are a web app in `app/src/main/assets/index.html`, shown full screen by a small Kotlin activity (`MainActivity.kt`). The activity connects the web app to Android:

| Android feature | Where |
| --- | --- |
| Back button moves back inside the app before closing it | `chordloftBack()` in index.html |
| "Choose files" opens the Android file picker | `onShowFileChooser` |
| Share or "Open with" Chordloft sends songs to the Import screen | intent filters in AndroidManifest.xml, `chordloftReceive()` |
| Screen stays on while a song is open | `setKeepAwake` |
| Stage mode hides the status and navigation bars | `setImmersive` |
| Metronome and auto-scroll stop when you leave the app | `chordloftPause()` |

The fonts are downloaded once during the build (the `fetchFonts` task in `app/build.gradle.kts`). If that download fails, the app still builds and uses the phone's fonts.

## Option A: Build on your computer with Android Studio

1. Install Android Studio (free) from developer.android.com/studio.
2. Unzip this folder, then choose **File > Open** in Android Studio and pick the `chordloft-android` folder.
3. Wait for "Gradle sync" to finish. The first time, it downloads the Android tools, which takes a few minutes.
4. To try it on your phone: turn on **Developer options > USB debugging** on the phone, plug it in, and press the green **Run** button.
5. To get an APK file you can send to others: **Build > Build App Bundle(s) / APK(s) > Build APK(s)**. The file appears in `app/build/outputs/apk/debug/`.

## Option B: Let GitHub build it (no Android Studio needed)

1. Create a new repository on GitHub and upload the contents of this folder (keep the `.github` folder).
2. Open the repository's **Actions** tab. The "Build Android app" job starts on every upload and takes about 5 minutes.
3. When it finishes, open the run and download **chordloft-test-apk** at the bottom of the page.

## Installing the APK on a phone

Copy the `.apk` file to the phone and tap it. Android asks you to allow installs from that app (for example Files or Chrome) the first time. A test APK is fine for your band to use, but it can't go on Google Play.

## Publishing on Google Play

1. **Choose your app ID first.** It is `com.rexven.chordloft` in `app/build.gradle.kts` and can never change after you publish. Change `applicationId` and `namespace` now if you want a different one (also move the Kotlin file to the matching folder).
2. **Create an upload key** in Android Studio with **Build > Generate Signed App Bundle / APK > Create new**. Keep the `.jks` file and its passwords somewhere safe. You need them for every update.
3. **Build the bundle**: with the same menu, choose **Android App Bundle**, release. You get an `.aab` file.
   - Or with GitHub: add four repository secrets (**Settings > Secrets and variables > Actions**): `KEYSTORE_BASE64` (the .jks file as base64, e.g. `base64 -w0 upload.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. The next build also produces **chordloft-release** with the `.aab`.
4. **Create a Google Play developer account** at play.google.com/console, create the app, fill in the store listing, content rating and privacy details, and upload the `.aab`. Check the Play Console for its current requirements, such as testing with a group of testers before your first public release.
5. **For each update**, raise `versionCode` (1, 2, 3...) and `versionName` in `app/build.gradle.kts`.

## Changing the app

Edit `app/src/main/assets/index.html` and rebuild. You can open that file in a desktop browser to try changes quickly. The Android-only parts do nothing there.
