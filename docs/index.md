# ADB Helper

ADB Helper turns an Android phone into an ADB control console for a second Android device,
reached over Wi-Fi (USB-OTG is on the roadmap — see [Known limitations](known-limitations.md)).

It's a clean-room Kotlin rewrite of `didjdk/adbhelper`: same feature set, rebuilt from scratch
as a small, modular, maintainable codebase, with no monetization and no code or resources
carried over from the original project.

## What it does

- **Connect** to a target device over Wi-Fi: type an address, scan the local network, or pair
  with wireless debugging's 6-digit code. Recent connections are remembered.
- **Device info** — model, serial, CPU, Android version, battery, storage, screen, Wi-Fi IP.
- **App manager** — list, filter, enable/disable, uninstall, clear data/cache, install APKs.
- **File manager** — browse, create, rename, delete, copy/paste, upload, and download files.
- **Shell** — a persistent interactive terminal session to the target.
- **Monitor** — a live, auto-refreshing view of the target's screen (screenshot-based).
- **Power** — reboot (normal/recovery/bootloader), change display DPI, toggle wireless
  debugging on the target.
- **Process manager** — list and kill running processes.

See [Features](features.md) for the full screen-by-screen walkthrough, or
[Getting started](getting-started.md) to build and run the app.
