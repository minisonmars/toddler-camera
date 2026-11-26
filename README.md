# Toddler Camera

A stripped-down camera app for toddlers: big buttons, nothing to get lost in. Photos and videos, front and back camera, a simple gallery, and a PIN-protected parent area for everything else.

Built with Kotlin and CameraX, with the screens drawn in code. Needs Android 10 or newer.

## What it does

- The **camera and video-camera icons** at the top switch between taking pictures and recording video.
- The big round **centre button** takes a picture, or starts and stops a video (red, with a white square while recording).
- The **flip icon** switches between the front and back camera.
- The **picture icon** opens the gallery: every picture and video on the device, newest first, as big tiles. Tap one to open it, and use the left and right arrows to move through them and the red cross to close. Videos play when opened; tap to pause.
- There is deliberately **no zoom, no settings for the toddler and no delete button**.
- New pictures are saved to `Pictures/ToddlerCamera` and videos to `Movies/ToddlerCamera`, so they also show up in the normal gallery app.

## Parent settings

Hold the small **gear** in the top-right corner for two seconds, then enter the PIN (default **3580**) to open the parent settings. Every change is saved and applied straight away.

- **What your toddler can use**: switch the photo camera, video camera and gallery on or off. A switched-off feature's button disappears. At least photo or video must stay on.
- **Gallery**: show everything on the device or only Toddler Camera's own pictures and videos, and choose pictures and videos, pictures only or videos only. Optionally show the last picture taken on the gallery button, like a phone camera app.
- **Camera**: start with the back camera, front camera or the last one used; picture size (maximum, high about 12 MP, medium about 5 MP, low about 2 MP); flash off, auto or on; shutter sound (camera click, pop, bell, boing, chime, beep or none); recording start and stop sounds; a white screen flash when a picture is taken; save location in pictures and videos.
- **Video**: quality (SD, HD, Full HD, 4K; the closest the phone supports is used); longest video of 1, 2 or 5 minutes or no limit; light on while recording.
- **Toddler protection**: toddler lock (screen pinning, with the back button disabled) and an optional play time limit. When time is up the screen goes blank until a parent holds the gear and enters the PIN.
- **Parent PIN**: change the PIN and set a recovery word. **Forgot PIN?** on the PIN screen resets it to the default if you give the recovery word. With no recovery word set, clear the app's storage in Android Settings (this also resets all settings).
- **App updates**: shows the installed version, and turns red with "Version X is ready to install" when a newer one is on GitHub. **Check for updates or choose a version** opens the versions screen (see below).
- **This device**: free storage space, back to the camera, and close the app.

Notes: flash only works on a camera that has one (usually the back one), and in video "light on" means a steady torch. The app's own shutter sounds play at the media volume, so they are quiet if that is turned down. Location uses the phone's last known position, so it can be slightly out of date. Pinned apps can be left without the menu by holding Back and Overview together.

## Permissions

Camera, microphone (for video sound) and access to pictures and videos (for the gallery). Internet is used only for checking and downloading app updates from GitHub (a quiet check at most every 6 hours, and when the parent area opens). The app asks when it first starts. Location is only asked for if a parent switches on "Save location".

## Getting the APK

The app is built by GitHub Actions on every push to `main`, and each build publishes a **Release** named with its version number (for example `v0.1.5`) with the APK attached.

1. Open the repo's **Releases** page (github.com/minisonmars/toddler-camera/releases).
2. Tap the APK under **Assets** and open it on the phone. Allow installs from your browser or files app when prompted.

The version number is the one set in `.github/workflows/build.yml` plus the build number. Every build is a release (non-debuggable) build signed with the same key (`app/debug.keystore`), so new APKs install over the old one. That key is for personal testing only and is not suitable for the Play Store.

To update, install the newest release over the old one (nothing is lost), or update from inside the app.

## In-app updates

Works the same way as in the other toddler apps:

- The app quietly checks GitHub for a new version when the parent area opens, and at most every 6 hours otherwise. It never pops anything up at the toddler.
- When there is one, **App updates** in the parent settings turns red and says "Version X is ready to install (you have Y)".
- **Check for updates or choose a version** opens the versions screen: new versions first (newest at the top, each with its date, size and what changed) and an **Install** button; then the installed version in green; then **Show older versions**, with a drop-down, its notes and **Go back to this version** (Android won't install an older version over a newer one, so this explains how to download, uninstall and install it); and **Reinstall**.
- The first time, Android asks you to allow Toddler Camera to install apps; after that, on Android 12 and later, updates go straight in without Android's confirm screen once the app installed the previous version itself (the first self-update still shows it once). The app closes when the update is in. The toddler lock is switched off while installing.

## What's new

`CHANGELOG.md` lists what changed in each version. Add bullets under `## Next` with each change; every build uses them as the GitHub release notes (shown on the versions screen), renames the section to the new version and commits it back (`.github/scripts/changelog.py`).

## Project layout

- `MainActivity.kt` – camera screen, photo and video capture, time limit
- `GalleryActivity.kt` – grid of everything on the device
- `ViewerActivity.kt` – full-screen picture or video
- `ParentSettingsActivity.kt` – the parent settings screen
- `Settings.kt` – all saved settings, the PIN and the play-time session
- `Gate.kt` – PIN prompt and forgot-PIN reset
- `Media.kt` – reads pictures and videos from the device
- `Sounds.kt` – shutter and recording sounds
- `Icons.kt` – the round buttons and their drawn icons (so they look the same on every phone)
- `Updater.kt` and `InstallReceiver.kt` – automatic update check, versions screen, download and install
- `Ui.kt` – colours and button helper
- `Insets.kt` – pads screens around the status bar, navigation bar and keyboard (needed since the app targets API 35, which updates without the confirm screen require)
- `app/src/main/res` – the app icon (an adaptive icon drawn as vectors, with a one-colour version for themed icons)
- `.github/workflows/build.yml` – cloud build that produces the APK and publishes the release
- `.github/scripts/changelog.py` and `CHANGELOG.md` – release notes

## Ideas for later

- Companion parent app to view pictures remotely
- Landscape layout and remote-control (D-pad) navigation for TV use
