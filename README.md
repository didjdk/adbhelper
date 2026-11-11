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

## Status

In progress. See:
- [Design doc](docs/superpowers/specs/2026-11-11-adbhelper-rewrite-design.md) — full feature
  spec, architecture decisions, and constraints.
- [Implementation plans](docs/superpowers/plans/) — task-by-task build plans, executed and
  reviewed one at a time.

## Requirements

- Android Studio / JDK 17+, Android SDK (minSdk 26).
- An adb-connected device or emulator for local testing of UI/non-transport flows. Full
  USB-OTG/network-target behavior is inherently hard to verify without a second physical
  Android device — see the design doc's testing notes.

## License

Not yet decided for this fork; the bundled third-party components (AOSP `adb`/`fastboot`,
Genymobile `scrcpy-server`) retain their own upstream licenses — see the in-app "About" screen
once implemented.
