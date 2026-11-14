# ADB Helper

A clean-room Kotlin rewrite of [`didjdk/adbhelper`](https://github.com/didjdk/adbhelper): an
Android app that turns a phone into an ADB/fastboot control console for a second Android
device, reached over USB-OTG or Wi-Fi — device info, file manager, app manager, shell, screen
mirroring, screenshots, reboot/fastboot, and process management.

## Why a rewrite

The upstream project, while open source, is a single unmaintainable module. This rewrite
reproduces its full feature set from scratch, in Kotlin, as small single-responsibility
modules with short functions and meaningful names — built only from the released APK
(`adbhelper_v1.3.1.apk`, kept in this repo as the feature reference) and genuine, well-known
upstream libraries/binaries, never from the original project's own source tree.

## Documentation

Full docs — features, architecture, build instructions, and known gaps (USB-OTG, screen
mirroring) — live under [`docs/`](docs/index.md), built with
[Material for MkDocs](https://squidfunk.github.io/mkdocs-material/):

```bash
pip install mkdocs-material
mkdocs serve
```

## Requirements

- Android Studio / JDK 17+, Android SDK (minSdk 26).
- A target device or emulator reachable over the network for local testing. USB-OTG isn't
  implemented yet — see [Known limitations](docs/known-limitations.md).

## License

Not yet decided for this fork; the bundled third-party components (AOSP `adb`/`fastboot`,
Genymobile `scrcpy-server`) retain their own upstream licenses — see the in-app "About" screen
once implemented.
