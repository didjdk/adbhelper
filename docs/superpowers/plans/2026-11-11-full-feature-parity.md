# Full Feature Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline, this session) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement every remaining user-visible feature of the adbhelper rewrite (LAN auto-search, pairing, recents, device info, app manager, file manager, shell, screenshot, reboot/DPI, process manager, and — best effort, last — scrcpy mirroring) and remove the Settings/About/Feedback screens per user instruction.

**Architecture:** Pure parsers/fixtures in `core/*` under unit test; thin ViewModels + Compose screens in `features/*`; one shared `DeviceSession` singleton carries the connected `AdbClient` from Connect into every device screen. DeviceScreen hosts tabs (Info / Apps / Files / Shell / Power / Mirror).

**Tech Stack:** Kotlin, Compose M3, libadb-android (`pair(host, port, code)` confirmed, `openStream` for any service string incl. `exec:` and `localabstract:`), DataStore, JUnit5.

**Spec:** `docs/superpowers/specs/2026-11-11-adbhelper-rewrite-design.md`

## Global Constraints

- English only; no monetization; no code/resources extracted from the reference APK.
- minSdk 26, targetSdk 36; monospace only for real device data; single signal-cyan accent.
- Pure logic unit-tested (JUnit5, `useJUnitPlatform`); no live-device tests — smoke-test UI on the dev emulator only.
- Exact failure/confirm copy from spec: "Pairing failed" / "Pairing succeeded" / "Pairing…", "Scanning for devices on the local network…", "No adb devices found", "Uninstall this app?", "App data cannot be recovered once cleared.", "This is a system app — disabling it may cause problems.", "Install <name>?", "This is a system process — are you sure you want to kill it?", "Changing the display DPI may make the UI render incorrectly.", "Failed to change DPI", "Exit remote control?", "This action needs root access on this device, which isn't available.", "Capturing screenshot…", "Connecting…", "Turning off wireless debugging will disconnect — turn it off?".

## Rulings (deviations from spec, user-authorized)

- **Settings/About/Feedback screens removed** (user instruction, overrides spec §10/§11). Hidden-file visibility becomes a toggle inside Files; screenshot save dir becomes the app-specific `getExternalFilesDir("Screenshots")`; DataStore now holds only recent connections.
- **USB-OTG transport deferred**: the pinned `io.github.muntashirakon:libadb-android:3.1.1` has no USB transport (only host/port/TLS; a BouncyCastle fork's `USB_TRANSPORT.md` confirms the original lacks it). Spec itself marks this a research item for its own plan. Not in this pass.
- **App-manager size/install-time/update-time sorts deferred**: per-app sizes/times require a `dumpsys package` sweep per app — disproportionate to value; sorts shipped: name, package, type, enabled. `shortcut: sorts limited to name/package/type/enabled, revisit if a user asks for size sorting`.
- **Mirroring is view-only first**: video decode + display only; input-injection control protocol and bitrate/awake toggles follow after video works. `shortcut: view-only mirror first, control protocol lands as its own task once video streams`.
- **LAN scan = parallel TCP sweep of port 5555**, not mDNS — spec allows either; sweep is testable and dependency-free.

## Review Focus

- Wrong-target routing: every action after a reconnect must hit the connected target — guarded by `AdbKeyConnectionManager.connectTo` reconnect logic; reviewed per feature that each command goes through the session's client.
- `ls -la` variants (toybox vs. toolbox column orders, symlink arrows, locale dates): parser must tolerate the fixture and missing columns without crashing.
- Shell streaming: terminal session must not read the whole stream before showing output (readChunk, not readAll).
- Unreachable target mid-action: every ViewModel surfaces `TransportUnreachable` as "Connection to the device was lost" rather than hanging.
- Large file transfer memory: push/pull streams in chunks, never whole-file `readBytes()` for files > a few MB.

---

### Task 1: Remove Settings/About/Feedback screens

**Files:**
- Delete: `app/src/main/kotlin/com/rel/adbhelper/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsRepository.kt`, `AppSettings.kt`, `about/AboutScreen.kt`, `feedback/FeedbackScreen.kt`
- Delete tests: `settings/SettingsViewModelTest.kt`, `settings/SettingsRepositoryTest.kt`
- Modify: `navigation/AppNavHost.kt` (drop routes), `connect/ConnectScreen.kt` (drop the settings/about/feedback row), `ui/components/ScreenHeader.kt` (still used by device screens — keep)
- Keep `settings/SettingsDataStore.kt` renamed logic: DataStore now stores only `recentConnections: Set<String>`; move it to `core/recents/RecentConnections.kt`

**Interfaces:**
- Produces: `RecentConnections(context).entries: Flow<List<String>>`, `suspend fun add(entry: String)`, `suspend fun remove(entry: String)`

- [ ] Step 1: delete files/routes/row, add RecentConnections with a test (entries add/remove round-trip via a fake DataStore? — DataStore needs a real file: use a tmp dir context? Simplest: keep RecentConnections thin over DataStore and test nothing beyond compile; pure-logic rule doesn't apply to a 3-line wrapper. Ledger: no unit test, verified by compile+smoke.)
- [ ] Step 2: `gradlew testDebugUnitTest` → PASS, `assembleDebug` → PASS
- [ ] Step 3: commit `refactor: remove Settings/About/Feedback screens; recents into DataStore`

### Task 2: AdbStream streaming reads + raw/exec-out services

**Files:**
- Modify: `core/adb/AdbTransport.kt` (add `suspend fun readChunk(): ByteArray?` to AdbStream), `NetworkAdbTransport.kt` (wrapper reads one packet: `InputStream.read(buf)`, null on close), `core/adb/FakeAdbTransport.kt` (test fake supports scripted chunks)
- Modify: `core/adb/AdbClient.kt`: add `suspend fun raw(service: String): ByteArray` (exec-out bytes, no exit-marker), `suspend fun push(path: String, data: ByteArray): Unit` via `exec:cat > '<path>'` + write + close, `suspend fun pull(path: String): ByteArray` via `exec:cat '<path>'`, and `fun openService(service: String): AdbStream` for the terminal.

**Interfaces:**
- Consumes: existing `AdbTransport.open(service)`.
- Produces: `AdbClient.raw/pull: ByteArray`, `push(path, data)`, `openService(service): AdbStream` (streaming, used by Shell task and Mirror task).

- [ ] Step 1: failing tests in `AdbClientTest`: `raw returns the full stream bytes without the exit marker`; `push writes the bytes to the service then closes`; `pull returns streamed bytes`; `readChunk yields scripted chunks then null on EOF`.
- [ ] Step 2: run → FAIL (missing members)
- [ ] Step 3: implement
- [ ] Step 4: run → PASS; full suite PASS
- [ ] Step 5: commit `feat: streaming AdbStream reads + raw exec-out client`

### Task 3: DeviceSession + device hub navigation

**Files:**
- Create: `core/session/DeviceSession.kt` — `object DeviceSession { var target: String?; var client: AdbClient?; fun attach(target, client); fun detach() }`
- Create: `features/device/DeviceScreen.kt` — Scaffold with scrollable TabRow (Info, Apps, Files, Shell, Power) switching content; Mirror tab appears only when `features/mirror` exists (Task 11).
- Modify: `ConnectScreen.kt`/`ConnectViewModel`: on `Connected`, attach session and `onConnected()` callback navigates to route `device`; AppNavHost adds `composable("device") { DeviceScreen() }`.

- [ ] Step 1: build + full suite PASS (UI-only; smoke on emulator after Task 4 gives screens content)
- [ ] Step 2: commit `feat: device hub screen and shared session`

### Task 4: Device info (parsers + screen)

**Files:**
- Create: `core/device/DeviceInfo.kt` (data class `DeviceInfo(...)`), `core/device/DeviceInfoParsers.kt`
- Test: `app/src/test/kotlin/com/rel/adbhelper/core/device/DeviceInfoParsersTest.kt` — fixtures captured from the emulator's real output (paste actual `getprop`, `dumpsys battery`, `df /sdcard`, `wm size`, `wm density`, `ip addr show wlan0` text into the test file).
- Create: `features/deviceinfo/DeviceInfoViewModel.kt` + `InfoScreen.kt`; Screenshot button ("Capturing screenshot…" → toast with saved path, via `AdbClient.raw("exec:screencap -p")` saved to `context.getExternalFilesDir("Screenshots")/adbhelper-<ts>.png`).

Parsers (pure, each tested):
- `parseGetprop(raw): Map<String,String>` — split lines on `: `.
- `buildDeviceInfo(props, battery, df, wmSize, wmDensity, ipAddr): DeviceInfo` — model `ro.product.model`, serial `ro.serialno`, device `ro.product.device`, cpu `ro.product.cpu.abi`, cores `CpuInfo`? (use `getconf _NPROCESSORS_ONLN`? — use `nproc`), android `ro.build.version.release`, sdk `ro.build.version.sdk`, patch `ro.build.version.security_patch`, battery from `dumpsys battery` (`level`, `voltage`, `temperature`, `status` → charging string), storage from `df /sdcard` (total/used/free in KB→human), screen `wm size` (`Physical size: WxH`), density `wm density` (`Physical density: N`), wifi IP from `ip addr show wlan0` (`inet <ip>/...`).

- [ ] Step 1: failing parser tests with emulator fixtures
- [ ] Step 2: FAIL → implement → PASS; suite PASS
- [ ] Step 3: InfoScreen lists KeyValue rows (monospace values); emulator smoke
- [ ] Step 4: commit `feat: device info screen with screenshot`

### Task 5: Process manager

**Files:**
- Create: `core/proc/ProcessEntry.kt` (`data class ProcessEntry(pid, user, name)`, `isSystem = user == "root" || user == "system"`), `core/proc/ProcessListParser.kt` (`parsePs(raw): List<ProcessEntry>` — tolerate toybox `ps` header + missing columns).
- Test: `ProcessListParserTest` with emulator `ps -A` fixture: first line header skipped; system user flagged.
- Create: `features/processes/ProcessListScreen.kt` + ViewModel: list, kill button per row (confirm dialog; system process → "This is a system process — are you sure you want to kill it?"), command `kill <pid>`, failure surfaces "Failed to kill process".

- [ ] Steps: test FAIL → implement → PASS; suite; smoke; commit `feat: process manager`

### Task 6: Reboot / DPI / wireless-debugging toggle (Power screen)

**Files:**
- Create: `features/power/PowerScreen.kt` + ViewModel. Actions: reboot (`reboot`), recovery (`reboot recovery`), bootloader (`reboot bootloader`) each behind confirm; DPI: input current+new, confirm "Changing the display DPI may make the UI render incorrectly.", apply `wm density <n>`, "Failed to change DPI" on error, reset button (`wm density reset`); wireless debugging on target: enable `settings put global adb_wifi_enabled 1`, disable with confirm "Turning off wireless debugging will disconnect — turn it off?" then `settings put global adb_wifi_enabled 0`.
- Command strings are the tested surface: extract `PowerCommands` object (pure) + test asserting exact strings.

- [ ] Steps: failing command-string test → implement → PASS; smoke; commit `feat: reboot/DPI/wireless-debugging power screen`

### Task 7: Shell terminal

**Files:**
- Create: `features/shell/ShellScreen.kt` + `ShellViewModel.kt`. Session: `DeviceSession.client.openService("shell:")` once per session; send writes input line bytes; output = flow of `readChunk` decoded appended to a mutable list state; "Clear output" resets list; "Restart terminal" closes stream and reopens; "Restart adb server" sends `setprop ctl.restart adbd` (root-only on target — failure surfaces).
- Test: none beyond suite (I/O-bound); ledger: verified by emulator smoke against emulator's own adbd? Cannot open shell to emulator from inside itself — smoke limited to screen open + state entry. Ledgered.

- [ ] Steps: implement; suite PASS; smoke screen open; commit `feat: interactive shell terminal`

### Task 8: File manager

**Files:**
- Create: `core/files/FileEntry.kt` (`data class FileEntry(name, path, isDir, size, perms, owner, mtime)`), `core/files/FileListParser.kt` (`parseLsLa(raw, dir): List<FileEntry>` — split lines, 8+ columns, symlink `->` handling, skip `total N`).
- Test: `FileListParserTest` with real emulator `ls -la /sdcard` fixture (captured at execution time): dirs flagged by leading `d`, symlinks resolved to name only, dotfiles retained.
- Create: `features/files/FilesScreen.kt` + ViewModel: browse from `/sdcard`, up-one-level, hidden-files toggle (default off), create folder (`mkdir '<p>'`) / file (`touch '<p>'`), rename (`mv`), delete (`rm -rf '<p>'`) with confirm, copy→paste (remember source path, `cp -r`), upload (SAF ACTION_OPEN_DOCUMENT → `push(path, bytes)` in 256 KB chunks), download (`pull` → save into `getExternalFilesDir("Downloads")`), file info row (size/owner/permissions/mtime from entry).

- [ ] Steps: parser test FAIL → implement → PASS; suite; smoke browse `/sdcard` on emulator; commit `feat: file manager with upload/download`

### Task 9: App manager

**Files:**
- Create: `core/apps/AppEntry.kt` (`data class AppEntry(pkg, isSystem, isEnabled)`), `core/apps/AppParsers.kt` (`parsePmList(raw): List<String>`), `parseDumpsysPackage(raw): AppDetails(versionName, minSdk, targetSdk, firstInstallTime, lastUpdateTime, permissions)`.
- Test: `AppParsersTest` — `package:com.example.app` line parsing, third-party vs system set union, dumpsys fixture (emulator `dumpsys package <pkg>` fragment).
- Create: `features/apps/AppsScreen.kt` + ViewModel:
  - list = `pm list packages -3` ∪ `pm list packages -s`, flags derived from which set; filter chips All/Third-party/System; sort by name/package (pure comparator, tested).
  - enable/disable: `pm disable-user --user 0 <pkg>` / `pm enable <pkg>`; system app disable shows "This is a system app — disabling it may cause problems."
  - uninstall confirm "Uninstall this app?" → `pm uninstall --user 0 <pkg>`
  - clear data confirm "App data cannot be recovered once cleared." → `pm clear <pkg>`; clear cache → `pm clear --cache-only <pkg>`
  - launch → `monkey -p <pkg> -c android.intent.category.LAUNCHER 1`
  - details dialog from `dumpsys package <pkg>` parsed.
  - install: SAF pick → push to `/data/local/tmp/adbhelper-install.apk` → `pm install -r` → rm; states Installing…/success/failure; confirm "Install <name>?"
  - activate: probe `su -c id` once; without root show "This action needs root access on this device, which isn't available." (disabled); with root run `su -c pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`.
- [ ] Steps: parser/comparator tests FAIL → implement → PASS; suite; smoke list apps from emulator; commit `feat: app manager`

### Task 10: LAN auto-search + pairing + recent connections on Connect

**Files:**
- Create: `core/net/LanScanner.kt` — pure `subnetHosts(ip: String, prefix: Int): List<String>` + `suspend fun probe(host: String, port: Int, timeoutMs): Boolean` (Socket connect).
- Create: `features/connect/LanScanViewModel` logic inside ConnectViewModel: scan button → "Scanning for devices on the local network…" → results (tap to connect) or "No adb devices found"; parallel (32 coroutines, 250 ms timeout); subnet from `WifiManager` dhcpInfo (netmask→prefix), fallback 192.168. x.0/24 from active NetworkInterface.
- Create: `features/connect/PairDialog.kt` — ip:port + 6-digit code, calls `AdbKeyConnectionManager.getInstance(ctx).pair(host, port, code)` on IO dispatcher; states Pairing…/Pairing succeeded/Pairing failed.
- Modify: `ConnectScreen.kt`: recents list (from RecentConnections, swipe/remove button), Pair button, Scan button + results.
- Test: `LanScannerTest` — subnetHosts("192.168.1.5", 24) == 254 hosts in order; prefix != 24 → ruled to /24 anyway (ruling: clamp, shortcut comment).

- [ ] Steps: LanScanner test FAIL → implement → PASS; UI; smoke (scan runs against emulator's own subnet; expect "No adb devices found"); commit `feat: LAN auto-search, wireless pairing, recent connections`

### Task 11: Mirroring (scrcpy, view-only first) — LAST, best effort

**Files:**
- Create: `tools/fetch-binaries.sh` (pin current stable Genymobile/scrcpy release tag + sha256 of scrcpy-server-vX.jar; download to `app/src/main/assets/scrcpy-server.jar` at dev time; script checked in, jar fetched not committed).
- Create: `core/mirror/ScrcpyProtocol.kt` — pure encoders/decoders per pinned tag's documented wire format: device meta parse, codec config/frame header parse (SPS/PPS pass-through to MediaCodec), control message encoders reserved for the input task.
- Create: `features/mirror/MirrorScreen.kt` + ViewModel: push jar (`push(/data/local/tmp/scrcpy-server.jar)`), start `app_process / com.genymobile.scrcpy.Server <ver> logtostdout=true audio=false video=true max_size=1280 max_fps=30` via `openService("shell:")`? (server binds localabstract socket; open `localabstract:scrcpy_<socket>` per tag docs), feed MediaCodec (`video/avc`, SurfaceView surface), "Exit remote control?" confirm.
- Test: `ScrcpyProtocolTest` — hand-built byte vectors for meta/frame headers.
- Ruling risk: this is the least testable module (spec acknowledges); if video cannot be verified end-to-end here, commit the protocol tests + push/start wiring with the shortcut comment and report honestly.

- [ ] Steps: protocol tests FAIL → implement → PASS; wire screen behind "Mirror" tab; smoke = screen opens, push+start attempt on emulator (a real scrcpy target needs the emulator's own display — smoke limited); commit `feat: scrcpy view-only mirroring`

### Task 12: Final pass

- Full suite; assembleDebug; install on emulator; smoke every screen via uiautomator taps; fix crashes; commit fixes; merge into local dev. No push.
