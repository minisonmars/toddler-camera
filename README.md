# Toddler Camera

A stripped-down camera app for toddlers: big buttons, nothing to get lost in.

## What it does

- The **camera and video-camera icons** at the top switch between taking pictures and recording video.
- The big round **centre button** takes a picture, or starts and stops a video (red, with a white square while recording).
- The **flip icon** switches between the front and back camera.
- The **picture icon** opens the gallery: every picture and video on the device, newest first, as big tiles. Tap one to open it, and use the left and right arrows to move through them and the red cross to close. Videos play when opened; tap to pause.
- There is deliberately **no zoom, no flash, no settings and no delete button**.
- New pictures are saved to `Pictures/ToddlerCamera` and videos to `Movies/ToddlerCamera`, so they also show up in the normal gallery app.

## Parent menu and toddler lock

Hold the small **gear** in the top-right corner for two seconds to open the parent menu:

- **Toddler lock ON/OFF**: when on, the app uses Android screen pinning so the toddler can't leave it, and the back button does nothing. Android asks you to confirm pinning the first time. It is **off by default**. To leave a pinned app without the menu, hold Back and Overview together (or swipe up and hold, on gesture navigation).
- **Close app**.

## Permissions

Camera, microphone (for video sound) and access to pictures and videos (for the gallery). The app asks when it first starts.

## Getting the APK

The app is built by GitHub Actions on every push to `main`, and each build publishes a **Release** named with its version number (for example `v0.1.5`) with the APK attached.

1. Open the repo's **Releases** page (github.com/minisonmars/toddler-camera/releases).
2. Tap the APK under **Assets** and open it on the phone. Allow installs from your browser or files app when prompted.

The version number is the one set in `.github/workflows/build.yml` plus the build number. Every build is signed with the same key (`app/debug.keystore`), so new APKs install over the old one. That key is for personal testing only and is not suitable for the Play Store.

Needs Android 10 or newer.

## Project layout

- `MainActivity.kt` – camera screen, photo and video capture, parent menu
- `GalleryActivity.kt` – grid of everything on the device
- `ViewerActivity.kt` – full-screen picture or video
- `Media.kt` – reads pictures and videos from the device
- `Icons.kt` – the round buttons and their drawn icons (so they look the same on every phone)
- `Ui.kt` – colours and button helper
- `.github/workflows/build.yml` – cloud build that produces the APK and publishes the release

## Ideas for later

- Companion parent app to view pictures remotely
- Landscape layout and remote-control (D-pad) navigation for TV use
- Optional shutter sound or animation
- Optional video length limit
