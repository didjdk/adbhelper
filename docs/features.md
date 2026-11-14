# Features

## Connect

Two ways to reach a target over the network:

- **Address** — type `ip:port` directly and tap **Connect**.
- **Scan** — sweeps the local subnet for hosts with the ADB port open, then verifies each
  candidate with a real handshake before listing it. Shows "Scanning for devices on the local
  network…" while running, "No adb devices found" if nothing turns up.
- **Pair** — wireless debugging's pairing flow: enter the target's pairing `ip:port` and
  6-digit code; shows "Pairing…", then "Pairing succeeded" or "Pairing failed".

Recent connections are remembered and listed for one-tap reconnect, each removable.

USB-OTG is a stub for now — see [Known limitations](known-limitations.md).

## Device info

A read-only panel: model, manufacturer, serial, device codename, CPU architecture, core count,
Android version, SDK level, security patch, battery (percentage, temperature, charging state),
storage (used/total), screen resolution and density, and Wi-Fi IP — parsed from `getprop`,
`dumpsys battery`, `df`, `wm size`/`wm density`, and `ip addr`.

A **Screenshot** button captures the target's screen (`screencap`) and saves it to this app's
own external files directory.

## App manager

Lists installed apps with filters for **All** / **Third-party** / **System**. Per app:

- Enable/disable (disabling a system app warns first).
- Uninstall, clear data ("App data cannot be recovered once cleared."), clear cache.
- View details: version, min/target SDK, install/update time, permissions.
- Launch.
- Install an APK via the system file picker.
- "Activate" — a shell-permission-granting action that needs root on the target; disabled with
  an explanation when root isn't available.

## File manager

Browses the target's filesystem starting from `/sdcard`, with a toggle for hidden (dot) files.
Per entry: open (directories browse in, files show an info dialog), copy/paste, rename, delete
(with confirmation), and download. Upload pushes a file from the controlling device onto the
target via the system file picker.

## Shell

A persistent interactive terminal session: type a command, see streamed output. **Clear
output** resets the scrollback; **Restart terminal** closes and reopens the session;
**Restart adb server** issues `setprop ctl.restart adbd` (needs root on the target).

## Monitor

A live, auto-refreshing view of the target's screen, built from repeated screenshots rather
than a true video stream — see [Known limitations](known-limitations.md) for how this differs
from full screen mirroring.

## Power

- **Advanced reboot** — normal, recovery, or bootloader, each behind a confirmation.
- **Display DPI** — view the current density, apply a new one (with a warning that it may
  render incorrectly), or reset to the system default.
- **Wireless debugging** — turn it on, or off (with a confirmation that it will disconnect the
  session).

## Process manager

Lists running processes (`ps`) with a kill action per row; killing a system process shows a
stronger warning.
