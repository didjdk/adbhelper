# ADB Helper rewrite — design

## Intent

`didjdk/adbhelper` is an open-source Android app (package `com.didjdk.adbhelper`), but its
upstream repo is unmaintainable (single-module, not something to build on). Per instruction,
we deliberately don't use its GitHub source at all — only the release APK
(`adbhelper_v1.3.1.apk`, checked into this repo) as a feature reference. This project rewrites
the same feature set from scratch as `com.rel.adbhelper`, in Kotlin, as a clean, modular,
maintainable codebase, in English only, matching the original's full feature list exactly.

We reconstructed the feature list from the APK's manifest and `strings.xml` (`apktool
--no-src`). We are also permitted to inspect the APK's decompiled bytecode (jadx) to read off
discrete, factual API-level details where they matter for correctness — exact shell command
strings, exact `getprop` keys, exact intent/extra names, exact scrcpy invocation arguments —
without copying its implementation structure, control flow, or class design. Any such detail
pulled from the decompile is called out inline where used, as a fact learned, not code reused.

Most of the app's actual job — talking to a *second* physical/OTG device over USB or Wi-Fi —
cannot be exercised against the single adb-connected emulator available for development here.
Because of that, this spec is written to leave as little as possible to on-device discovery:
every screen's states, error strings, and edge cases are enumerated up front, and the
architecture is split so the parts that *can* be verified (protocol framing, command building,
response parsing) are plain functions under unit test, isolated from the parts that can't
(actual socket/USB I/O).

## Goals

- Full feature parity with v1.3.1's user-visible functionality (enumerated below).
- Small, single-responsibility modules; short functions; names that say what they do.
- Core logic (command builders, protocol encoders/decoders, response parsers) is pure and
  unit-tested, independent of any real adb/USB/network I/O.
- Only genuine upstream software is bundled: official AOSP-built `adb`/`fastboot` binaries and
  the official Genymobile `scrcpy-server`, fetched from their own canonical sources — never the
  copies extracted from the reference APK.
- Builds and launches cleanly on the adb-connected emulator (UI, navigation, settings,
  non-device-dependent flows all smoke-tested there).

## Non-goals / anti-goals

- No Chinese (or any) localization — English only.
- No "premium unlock" gate or monetization. Everything the original gated is free here.
- No reuse of the original APK's compiled code, resources, or binaries in the shipped app.
- Not attempting full device-to-device integration testing — that's an acknowledged gap, not
  a failure condition, given the environment.
- A failure condition for this project would be: code written loosely because "it can't be
  tested anyway", undocumented protocol assumptions, or silently dropped features.

## Constraints discovered from the APK

- `minSdkVersion` target: 26 (Android 8) — our call, modern enough for `UsbManager`/coroutines,
  old enough to match realistic OTG-capable hardware.
- `android.hardware.usb.host` feature — USB-OTG is core, not optional.
- Original requests `MANAGE_EXTERNAL_STORAGE` and has an "activate apps needing shell
  permission" feature — strongly implies the original assumed a rooted controlling device for
  some flows. We keep that assumption explicit rather than silently requiring root everywhere:
  see per-feature notes below.
- Bundled binaries in the reference APK: `adb`/`fastboot` (ARM32, statically linked, built for
  "Android 30") and `scrcpy_server` (an APK-packaged jar). We do **not** extract or ship these —
  we fetch our own copies at build/setup time from genuine upstream sources, recorded with their
  source URL and checksum next to a fetch script (`tools/fetch-binaries.*`):
  - `adb` (pairing command only — see transport decision above) and `fastboot` (arm64-v8a +
    armeabi-v7a, running ON the controlling device): AOSP's own build targets a desktop host,
    not Android, so there's no official Google Android-target build. Source genuine prebuilt
    Android-target binaries from a well-known existing distribution of the same AOSP code —
    the Termux `android-tools` package is the standard one (genuine AOSP `platform/system/core`
    source, already built for Android, widely used) — rather than cross-building ourselves.
    Pin an exact package/version and checksum.
  - `scrcpy-server.jar` from a tagged GitHub release of `Genymobile/scrcpy` (Apache-2.0/MIT).
    Pick the current stable release tag at implementation time and have the client's control
    protocol implementation (`core/mirror`) match *that* tag's documented wire format — don't
    pin the jar to a protocol version decided in advance, since the client code doesn't exist
    yet; tag selection and protocol implementation happen together, in the same change.
  - Both are fetched by a checked-in script (`tools/fetch-binaries.sh` or similar), not committed
    as binary blobs where avoidable; if a binary must be vendored, it goes through Git with its
    source URL + checksum recorded next to it.

## Decided "how" (ours to make, stated so they're visible)

- **Language**: 100% Kotlin app code. No C/C++ *in this app's own codebase* — the bundled
  `adb`/`fastboot` executables are invoked via `ProcessBuilder` (no JNI needed to run them), and
  scrcpy's H.264 stream is decoded via `MediaCodec` (hardware or software, Android's own
  framework API — no bundled native decoder needed). This claim should be spot-checked against
  the actual pinned scrcpy tag's stream parameters (SPS/PPS, profile/level) once that tag is
  picked; if a target device's hardware decoder can't handle it, fall back to `MediaCodec`'s
  software decoder rather than reaching for a native library.
- **UI**: Jetpack Compose + Material 3, single Activity, Navigation-Compose for screens.
- **Architecture**: MVVM. One Gradle module (`:app`) organized as small internal packages per
  feature (not separate Gradle modules — that overhead isn't justified at this size). Each
  feature package: `screen/` (Composables), `<Feature>ViewModel`, and a small
  repository/service type that owns the actual I/O, behind an interface so the ViewModel can be
  tested with a fake.
- **Transport**: following the "use well-known libraries, implement the least logic possible"
  instruction, the ADB wire protocol itself is not hand-rolled. We depend on an existing,
  well-known, actively-referenced open-source Java ADB client library that implements the
  protocol (`CNXN`/`AUTH`/`OPEN`/`WRTE`/`CLSE`/`OKAY` framing, the sync push/pull service, shell
  streams) over both a raw socket and `UsbDeviceConnection` bulk transfer — e.g. `cgutman/AdbLib`
  or an actively-maintained fork/successor of it (confirm current best-maintained option at
  implementation time; the point is "use the library", not "use this exact GitHub repo"). This
  removes essentially all custom protocol-framing code from our side:
  - `NetworkAdbTransport` — the library's socket transport against `host:port`. No root.
  - `UsbOtgAdbTransport` — the library's `UsbDeviceConnection` transport against a device picked
    via Android's standard USB-permission dialog. No root.
  - Both expose the same small `AdbTransport` surface to our `AdbClient` command layer, so
    everything above this line is transport-agnostic and untouched by which one is active.
  - **Wireless-debugging pairing** (Android 11+ `adb pair host:port <code>`) is a separate
    SPAKE2/TLS handshake, not part of the core wire protocol, and typically isn't covered by
    these USB-era client libraries. Reimplementing that crypto ourselves would be *more* custom
    logic, not less — so pairing specifically shells out to a genuine, prebuilt `adb` binary
    (sourced from a well-known existing Android-target build of AOSP's `adb`, e.g. the Termux
    `android-tools` package — genuine AOSP source, already built for Android, widely used) via
    `ProcessBuilder`, for that one command only. Everything else (connect, shell, push/pull,
    install) goes through the library, not the binary.
  - Fastboot has no comparable well-known Android-hosted client library. We use a genuine
    prebuilt `fastboot` binary (same source as above) via `ProcessBuilder` for the reboot-to-
    bootloader flows, rather than writing a USB fastboot client ourselves.
  - Root is a separate, unrelated axis: only the original's "activate app" shell-permission flow
    implies root, not transport. Detect root via a standard `su` probe (`which su` / attempt
    `su -c true`) exposed as a `RootAccess` capability check; when absent, the activate action
    shows "This action needs root access on this device, which isn't available." and is disabled
    rather than silently failing.
- **Settings persistence**: Jetpack `DataStore` (Preferences), replacing whatever the original
  used.
- **Testing**: JUnit5 + Kotlin for pure-logic unit tests (command builders, protocol
  encode/decode, device-info text parsers, file-listing parsers). No live-device integration
  test suite in v1 — out of reach in this environment; noted as a gap, not silently skipped.

## Feature spec (what ships, screen by screen)

### 1. Connect screen (launcher)
- Two connect paths: **Network** (enter `ip:port`, or pick from scanned LAN results) and
  **USB-OTG** (device picked via Android's USB-permission dialog).
- **Pairing** flow (Android 11+ wireless debugging): dialog for `ip:port` + 6-digit pairing
  code; shows pairing in progress / success / failure states verbatim-equivalent to original
  ("Pairing failed", "Pairing succeeded", "Pairing…").
- **LAN scan**: background scan of the local subnet for hosts with the adb TCP port open;
  "Scanning for devices on the local network…" / "No adb devices found" states.
- Recent connections list (persisted), each removable; a "this device" (local) entry offered
  when a USB target is already attached.
- Enabling wireless debugging on an *already-connected* target is a one-tap action; disabling
  requires a confirmation ("Turning off wireless debugging will disconnect — turn it off?").
- Connecting shows "Connecting…" → "Connected" or a specific failure (unauthorized/timeout/
  refused), never a bare "error".

### 2. Device info
- Read-only panel: connection status, device name, serial, model, product codename, CPU
  architecture, core count, Android version, SDK level, target SDK (per top app, if shown from
  app manager context), security patch level, battery (%, voltage, temperature, charging
  state), storage (total/used/free), screen (resolution, density), network (mobile IP + type,
  Wi-Fi IP + SSID, carrier name).
- All of this comes from parsing `getprop`, `dumpsys battery`, `dumpsys wifi`, `df`, `wm size`,
  `wm density` output — each parser is a pure function taking the raw shell text and returning
  a typed model, independently unit-tested against captured fixture output.

### 3. App manager
- List installed apps, filterable to third-party/system, sortable by name/size/install-time/
  update-time/type/package/enabled-state.
- Per-app actions: enable/disable, uninstall (confirm: "Uninstall this app?"), clear data
  (confirm: "App data cannot be recovered once cleared."), clear cache, view details (package
  name, version name, install/update time, permissions, min/target SDK, size), launch a chosen
  activity, "activate" (the shell-permission-requiring flow — gated behind the `requiresRoot`
  capability check, not behind any unlock paywall).
- Install flow: pick an APK via file picker or app picker, confirm ("Install <name>?"),
  progress, success/failure.
- System-app disable shows a warning first ("This is a system app — disabling it may cause
  problems.").

### 4. File manager
- Browse the target's filesystem from a configurable root (defaults to `/sdcard`), "jump to
  sdcard" shortcut, hidden-dot-file visibility toggle (default from Settings).
- Create file/folder, rename (single selection only — matches original's constraint), delete
  (confirm, including a multi-select case), copy/paste (within the target's filesystem), upload
  (push a file from this controlling device onto the target), download (pull a file from the
  target onto this controlling device's local storage), file info panel (size, owner, group,
  permissions, modified time).

### 5. Shell / terminal
- Persistent shell session to the connected target; command input, scrollable output, "clear
  output", "restart terminal" (kills and reopens the shell session), "restart adb server".

### 6. Screenshot
- One-tap capture (`exec-out screencap`), save directory configurable in Settings, "Capturing
  screenshot…" progress state, success toast with the save path.

### 7. Remote control / screen mirroring (scrcpy-based)
- Pushes the fetched `scrcpy-server.jar` to the target, starts it via `app_process`, opens a
  local `adb forward`, decodes the H.264 stream with `MediaCodec` into a `SurfaceView`.
- Controls: aspect-ratio lock, bitrate, "render all frames" toggle (vs. dropping stale frames
  — default off, matching original), keep-device-awake toggle, fullscreen toggle, exit
  confirmation ("Exit remote control?").
- Surfaces the original's caveat directly in the UI: input injection needs "disable permission
  monitoring"/simulated-input allowed in the target's Developer Options; when that's off we're
  view-only and say so, rather than silently swallowing taps.
- This is the least testable module end-to-end; its H.264 parsing and control-message encoding
  are still unit tested in isolation (known test vectors), and the module is written so the
  decode/encode logic has zero dependency on a live socket.

### 8. Reboot / DPI
- Reboot menu: normal, recovery, bootloader/fastboot (routes through fastboot once in that
  mode), presented as "Advanced reboot".
- DPI change: confirm ("Changing the display DPI may make the UI render incorrectly."), apply,
  "Failed to change DPI" on error.

### 9. Process manager
- List running processes (`ps`), kill with confirmation; system-process kill gets a stronger
  warning ("This is a system process — are you sure you want to kill it?").

### 10. Settings
- Default screen to land on after connecting, default hidden-file visibility, screenshot save
  directory. Plain `DataStore`-backed preference screen.

### 11. Feedback & About
- Feedback: contact info (replace the original's developer-specific contact details with
  placeholders/repo issue link — those were personal to the original author and don't belong
  in a fork), no donation QR codes (those were personal payment details, not a feature to
  reproduce).
- About: app version, open-source licenses screen (now meaningfully populated, since we're
  actually bundling third-party OSS: AOSP adb/fastboot, scrcpy-server, any AndroidX/Compose
  libs).

## Suggested build order

No Gradle project exists yet — this is a from-scratch repo, so the first several commits are
scaffolding, not features. Suggested order, each step a small reviewable commit:

1. Gradle project skeleton (`:app`, Compose + Material 3 wired, empty single Activity, no
   feature code yet).
2. `core/process` (a testable `ProcessRunner` abstraction over `ProcessBuilder`, used for the
   genuine `adb pair` and `fastboot` binary calls) + `core/adb`'s command layer wired to the ADB
   library, against a fake transport for unit tests — the part with the most pure, testable
   logic and no dependency on anything else existing yet.
3. `NetworkAdbTransport` (library's socket transport) + the Connect screen's network path (TCP
   connect + pairing via the bundled `adb` binary) — the one path actually exercisable against
   the dev emulator.
4. Settings + About/Feedback (simplest screens, good smoke test of navigation + DataStore).
5. Device info, then app manager, then file manager, then shell (each adds commands to
   `core/adb` but no new architecture).
6. `UsbOtgAdbTransport` (the library's USB transport wired to our `AdbTransport` interface) —
   tackle once the command layer above it is stable, so it only has to prove the wiring.
7. Reboot/DPI, process manager (small, same pattern as step 5).
8. Remote control / mirroring last — it's the biggest, least testable module; build it against
   an already-stable `AdbClient` so any bugs found are isolated to the mirroring code itself.

## Left to the builder

- Exact package/module layout beyond "one package per feature" above.
- Exact LAN-scan strategy (parallel TCP connect sweep vs. mDNS) as long as it matches the
  "scanning… / found devices / none found" states above.
- Exact AOSP build tag pinned for `adb`/`fastboot` and exact `scrcpy` release tag — pick current
  stable tags at implementation time, record them in `tools/fetch-binaries.*` with checksums.
- Test framework wiring (JUnit5 vs JUnit4 + MockK, whichever fits the Gradle setup chosen).
- Whatever minimal CI/build config is needed to run unit tests; no live-device CI.
