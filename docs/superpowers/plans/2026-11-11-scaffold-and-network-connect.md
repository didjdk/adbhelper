# Scaffold, core/adb, and network connect — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Gradle project, the transport-agnostic `core/adb` command layer (unit
tested against a fake transport), the network (TCP) ADB transport wired to a real ADB client
library, the Connect screen's network path, and the Settings/About/Feedback screens — i.e.
build-order steps 1–4 of the design doc. This is the first of several plans; file manager, app
manager, device info, shell, USB-OTG transport, reboot/process-manager, and remote mirroring
each get their own follow-up plan once this foundation lands.

**Architecture:** Single `:app` Gradle module, Kotlin + Jetpack Compose (Material 3) +
Navigation-Compose, MVVM. `core/adb` is pure Kotlin business logic (command building, response
parsing) behind an `AdbTransport` interface; a `FakeAdbTransport` drives its unit tests. A real
ADB client library supplies the actual wire protocol; this plan wires only its TCP/socket
transport (USB-OTG is a later plan). Settings use Jetpack DataStore (Preferences).

**Tech Stack:** Kotlin, Jetpack Compose, Navigation-Compose, Jetpack DataStore, Kotlin
coroutines, JUnit5, an ADB-protocol client library (exact artifact chosen in Task 4 — see that
task's notes), Gradle Kotlin DSL + version catalog.

**Spec:** `docs/superpowers/specs/2026-11-11-adbhelper-rewrite-design.md`

## Global Constraints

- Package name: `com.rel.adbhelper`. English only — no other locale resources.
- `minSdk = 26`. `compileSdk`/`targetSdk`: pick the latest stable SDK installed in this
  environment's Android SDK manager at Task 1 time; record the exact value chosen in the
  commit message for Task 1.
- 100% Kotlin app code. No native/C++ code in this app's own sources.
- Only genuine, well-known, original libraries — no hand-rolled reimplementation of something a
  well-known library already does (this governs the ADB transport library choice in Task 4).
- No premium/unlock gating anywhere. No donation QR codes or the original author's personal
  contact details — Feedback screen links to this repo's issue tracker instead.
- Every `core/adb` behavior must be unit-testable without any real socket/USB/process I/O.

## Review Focus

- **Network connect to an unreachable host or closed port** — must surface a specific
  "Connection failed"/timeout state, not hang the UI or show a bare "error" (spec: Connect
  screen states).
- **Settings persistence across process death** — a value written to DataStore and then the
  ViewModel/Activity recreated must still read back the same value, not reset to default.
- **Empty/blank input in the Connect screen's host:port field** — must disable the connect
  action or show a validation message, not attempt a connect with a malformed address.
- **AdbClient command failure vs. transport failure** — a command that the (fake, in tests)
  target device rejects must be distinguishable from the transport itself being unreachable, so
  later UI code can show the right message for each.
- **Double-tapping "Connect" while a connection attempt is in flight** — must not launch a
  second concurrent attempt or leave the UI in a stuck "Connecting…" state if the first attempt
  finishes after a second tap.

---

### Task 1: Gradle project skeleton

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts` (root), `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/rel/adbhelper/AdbHelperApp.kt` (Application class)
- Create: `app/src/main/kotlin/com/rel/adbhelper/MainActivity.kt` (single Activity, hosts a
  `NavHost` that for now shows one placeholder Composable)
- Create: `app/src/main/kotlin/com/rel/adbhelper/navigation/AppNavHost.kt`

**Interfaces:**
- Produces: a buildable `:app` module (`./gradlew :app:assembleDebug` succeeds) installable on
  the dev emulator, with Compose + Material3 + Navigation-Compose + DataStore + coroutines +
  JUnit5 dependencies declared in the version catalog (no feature code depends on them yet —
  this task just proves the build works).

- [ ] **Step 1: Write the Gradle files, manifest, Application class, MainActivity, and a
  one-screen `AppNavHost` (a single Composable showing the text "ADB Helper")**

Use `minSdk = 26`, package `com.rel.adbhelper`, Kotlin + Compose Material3 BOM (latest stable),
Navigation-Compose, `androidx.datastore:datastore-preferences`, kotlinx-coroutines-android,
JUnit5 (`org.junit.jupiter`) for the `testImplementation` config.

- [ ] **Step 2: Build and install**

Run: `./gradlew :app:assembleDebug :app:installDebug` (emulator running and adb-connected)
Expected: BUILD SUCCESSFUL, app installs, launching it shows "ADB Helper" on screen (verify via
`adb shell am start` + a screenshot, or by eye if a display is available).

- [ ] **Step 3: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle app
git commit -m "chore: scaffold Gradle project skeleton"
```

---

### Task 2: `core/process` — testable process execution

**Files:**
- Create: `app/src/main/kotlin/com/rel/adbhelper/core/process/ProcessRunner.kt`
- Test: `app/src/test/kotlin/com/rel/adbhelper/core/process/ProcessRunnerTest.kt`

**Interfaces:**
- Produces:
  - `interface ProcessRunner { suspend fun run(command: List<String>): ProcessResult }`
  - `data class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String)`
  - `class RealProcessRunner : ProcessRunner` — wraps `ProcessBuilder`, redirects
    stdout/stderr separately, waits for exit, captures both streams fully (used later for the
    `adb pair` and `fastboot` binary invocations — no caller exists yet in this plan).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `run returns exit code and captured stdout`() = runTest {
    val result = RealProcessRunner().run(listOf("echo", "hello"))
    assertEquals(0, result.exitCode)
    assertEquals("hello", result.stdout.trim())
}

@Test
fun `run captures non-zero exit code and stderr separately`() = runTest {
    val result = RealProcessRunner().run(listOf("sh", "-c", "echo err 1>&2; exit 3"))
    assertEquals(3, result.exitCode)
    assertEquals("err", result.stderr.trim())
    assertEquals("", result.stdout.trim())
}
```

(Use whatever shell is available on the JVM test host — Windows test runs may need
`listOf("cmd", "/c", "echo hello")` instead; pick whichever makes the test meaningful in this
environment and say so in the test's name/comment if it's platform-specific.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*ProcessRunnerTest*"`
Expected: FAIL — `RealProcessRunner` not defined.

- [ ] **Step 3: Implement `ProcessRunner`/`ProcessResult`/`RealProcessRunner`**

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*ProcessRunnerTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/rel/adbhelper/core/process app/src/test/kotlin/com/rel/adbhelper/core/process
git commit -m "feat: add testable ProcessRunner for shelling out to adb/fastboot binaries"
```

---

### Task 3: `core/adb` — transport interface, command layer, fake transport

**Files:**
- Create: `app/src/main/kotlin/com/rel/adbhelper/core/adb/AdbTransport.kt`
- Create: `app/src/main/kotlin/com/rel/adbhelper/core/adb/AdbClient.kt`
- Create: `app/src/main/kotlin/com/rel/adbhelper/core/adb/AdbResult.kt`
- Test: `app/src/test/kotlin/com/rel/adbhelper/core/adb/FakeAdbTransport.kt` (test fixture, not
  a test file itself — lives under `src/test` because nothing in `main` should depend on it)
- Test: `app/src/test/kotlin/com/rel/adbhelper/core/adb/AdbClientTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks (independent of `core/process`).
- Produces:
  - `interface AdbTransport { suspend fun open(service: String): AdbStream }` where
    `interface AdbStream { suspend fun write(data: ByteArray); suspend fun readAll(): ByteArray; fun close() }`
    — modeled directly on the real ADB protocol's "open a stream for a service string" shape
    (e.g. service strings like `"shell,v2,raw:ls /sdcard"`), so swapping in the real library's
    transport in Task 4 is a straight implementation of this interface, not a redesign.
  - `sealed class AdbResult { data class Success(val output: String) : AdbResult(); data class
    CommandFailed(val message: String) : AdbResult(); data class TransportUnreachable(val cause:
    Throwable) : AdbResult() }` — the three-way distinction the Review Focus item above calls
    for.
  - `class AdbClient(private val transport: AdbTransport) { suspend fun shell(command: String):
    AdbResult }` — opens `"shell,v2,raw:$command"`, maps a transport-level exception to
    `TransportUnreachable`, maps the stream closing with no output and a non-zero marker (per
    the real protocol's shell v2 exit-code packet) to `CommandFailed`, otherwise `Success`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `shell returns Success with command output when transport succeeds`() = runTest {
    val transport = FakeAdbTransport(scriptedOutput = "hello\n", exitCode = 0)
    val result = AdbClient(transport).shell("echo hello")
    assertEquals(AdbResult.Success("hello\n"), result)
}

@Test
fun `shell returns CommandFailed when the remote command exits non-zero`() = runTest {
    val transport = FakeAdbTransport(scriptedOutput = "", exitCode = 1)
    val result = AdbClient(transport).shell("false")
    assertTrue(result is AdbResult.CommandFailed)
}

@Test
fun `shell returns TransportUnreachable when opening the stream throws`() = runTest {
    val transport = FakeAdbTransport(throwOnOpen = IOException("refused"))
    val result = AdbClient(transport).shell("echo hi")
    assertTrue(result is AdbResult.TransportUnreachable)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*AdbClientTest*"`
Expected: FAIL — types not defined.

- [ ] **Step 3: Implement `AdbTransport`/`AdbStream`/`AdbResult`/`AdbClient`, and
  `FakeAdbTransport`** (the fake takes `scriptedOutput`, `exitCode`, and an optional
  `throwOnOpen` constructor param and otherwise implements `AdbTransport` in-memory)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*AdbClientTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/rel/adbhelper/core/adb app/src/test/kotlin/com/rel/adbhelper/core/adb
git commit -m "feat: add transport-agnostic AdbClient command layer with fake-transport tests"
```

---

### Task 4: `NetworkAdbTransport` — real library wiring

**Files:**
- Create: `app/src/main/kotlin/com/rel/adbhelper/core/adb/NetworkAdbTransport.kt`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (add the chosen ADB library
  dependency)

**Interfaces:**
- Consumes: `AdbTransport`/`AdbStream` from Task 3.
- Produces: `class NetworkAdbTransport(private val host: String, private val port: Int) :
  AdbTransport` — implements `open(service)` by delegating to the chosen library's
  connect-and-open-stream call for that `host:port`.

- [ ] **Step 1: Pick and add the library dependency**

Search for the best-maintained, well-known Java/Kotlin ADB-protocol client library available
today (the design doc names `cgutman/AdbLib` as the known reference point, written in 2013 —
confirm whether a more actively maintained fork/successor exists before depending on the
original). Add it to the version catalog and `app/build.gradle.kts`. Record which artifact and
version was chosen in the commit message for this task — this is a real decision being made at
implementation time, not a foregone one.

- [ ] **Step 2: Implement `NetworkAdbTransport`** wrapping the library's TCP transport and
  stream-open call behind the `AdbTransport`/`AdbStream` interfaces from Task 3.

- [ ] **Step 3: Manual verification against the dev emulator**

Enable the emulator's TCP adb listener (`adb -s <emulator-serial> tcpip 5555`, this talks to the
*dev* adb daemon which is a different thing from our library — the point here is to get a real
`host:port` endpoint speaking the real ADB protocol to test against), then from a small
throwaway `main()` or an instrumented test, call `NetworkAdbTransport("<emulator-ip>",
5555).open("shell,v2,raw:echo hi")` and confirm it returns `"hi\n"`. Record the exact steps and
result in the commit message or a short note — this is the one place in this plan that gets a
real, if narrow, live-protocol check.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/rel/adbhelper/core/adb/NetworkAdbTransport.kt gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: wire NetworkAdbTransport to <chosen library>"
```

---

### Task 5: Connect screen — network path

**Files:**
- Create: `app/src/main/kotlin/com/rel/adbhelper/connect/ConnectViewModel.kt`
- Create: `app/src/main/kotlin/com/rel/adbhelper/connect/ConnectScreen.kt`
- Create: `app/src/main/kotlin/com/rel/adbhelper/connect/ConnectUiState.kt`
- Modify: `app/src/main/kotlin/com/rel/adbhelper/navigation/AppNavHost.kt` (Connect is the start
  destination)
- Test: `app/src/test/kotlin/com/rel/adbhelper/connect/ConnectViewModelTest.kt`

**Interfaces:**
- Consumes: `AdbClient`, `AdbResult`, `NetworkAdbTransport` (Tasks 3–4).
- Produces:
  - `sealed class ConnectUiState { object Idle; object Connecting; data class Connected(val
    target: String); data class Failed(val reason: String) }`
  - `class ConnectViewModel(private val connect: suspend (host: String, port: Int) ->
    AdbResult) { val state: StateFlow<ConnectUiState>; fun onHostPortChanged(text: String); fun
    onConnectClicked() }` — the constructor takes a function, not a concrete transport, so the
    test drives it with a fake without touching sockets.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `connect click with blank input leaves state Idle and does not invoke connect`() = runTest {
    var invoked = false
    val vm = ConnectViewModel(connect = { _, _ -> invoked = true; AdbResult.Success("") })
    vm.onHostPortChanged("")
    vm.onConnectClicked()
    assertEquals(ConnectUiState.Idle, vm.state.value)
    assertFalse(invoked)
}

@Test
fun `successful connect moves Idle to Connecting to Connected`() = runTest {
    val vm = ConnectViewModel(connect = { _, _ -> AdbResult.Success("connected") })
    vm.onHostPortChanged("192.168.1.5:5555")
    vm.onConnectClicked()
    assertTrue(vm.state.value is ConnectUiState.Connected)
}

@Test
fun `transport failure surfaces a specific Failed state, not Idle`() = runTest {
    val vm = ConnectViewModel(connect = { _, _ -> AdbResult.TransportUnreachable(IOException()) })
    vm.onHostPortChanged("192.168.1.5:5555")
    vm.onConnectClicked()
    assertTrue(vm.state.value is ConnectUiState.Failed)
}

@Test
fun `second connect click while Connecting does not restart the attempt`() = runTest {
    var callCount = 0
    val vm = ConnectViewModel(connect = { _, _ -> callCount++; AdbResult.Success("") })
    vm.onHostPortChanged("192.168.1.5:5555")
    vm.onConnectClicked()
    vm.onConnectClicked()
    assertEquals(1, callCount)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*ConnectViewModelTest*"`
Expected: FAIL — types not defined.

- [ ] **Step 3: Implement `ConnectUiState`/`ConnectViewModel`** — blank/unparsable `host:port`
  keeps `Idle` (this is the validation state the Review Focus item asks for, surfaced as "stay
  Idle", with the Composable in Step 5 disabling the Connect button while `Idle` and input is
  blank); a click while already `Connecting` is a no-op.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*ConnectViewModelTest*"`
Expected: PASS

- [ ] **Step 5: Implement `ConnectScreen` Composable** — a text field for `host:port`, a
  Connect button (disabled when input is blank or state is `Connecting`), and state-driven text:
  "Connecting…" / "Connected to <target>" / the `Failed` reason. Wire it as the nav graph's
  start destination.

- [ ] **Step 6: Manual smoke test on the emulator**

Run: `./gradlew :app:installDebug`, launch the app, confirm the Connect screen renders and the
button enables/disables correctly as you type. (Do not expect a real connect to succeed here —
there is no second device; this step only verifies the UI itself.)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/rel/adbhelper/connect app/src/test/kotlin/com/rel/adbhelper/connect app/src/main/kotlin/com/rel/adbhelper/navigation/AppNavHost.kt
git commit -m "feat: add Connect screen network path"
```

---

### Task 6: Settings screen + DataStore

**Files:**
- Create: `app/src/main/kotlin/com/rel/adbhelper/settings/SettingsRepository.kt`
- Create: `app/src/main/kotlin/com/rel/adbhelper/settings/SettingsViewModel.kt`
- Create: `app/src/main/kotlin/com/rel/adbhelper/settings/SettingsScreen.kt`
- Test: `app/src/test/kotlin/com/rel/adbhelper/settings/SettingsRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `data class AppSettings(val defaultPostConnectScreen: String = "device_info", val
    showHiddenFilesByDefault: Boolean = false, val screenshotDirectory: String =
    "/sdcard/Pictures/AdbHelper")`
  - `class SettingsRepository(private val dataStore: DataStore<Preferences>) { val settings:
    Flow<AppSettings>; suspend fun update(settings: AppSettings) }`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `settings written are read back unchanged after a fresh repository instance`() = runTest {
    val dataStore = /* in-memory test DataStore, e.g. via a temp file + PreferenceDataStoreFactory */
    SettingsRepository(dataStore).update(AppSettings(showHiddenFilesByDefault = true))
    val reread = SettingsRepository(dataStore).settings.first()
    assertTrue(reread.showHiddenFilesByDefault)
}
```

This is the Review Focus "settings persist across recreation" check — a fresh
`SettingsRepository` instance over the same `DataStore` stands in for Activity/process
recreation.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsRepositoryTest*"`
Expected: FAIL — types not defined.

- [ ] **Step 3: Implement `AppSettings`/`SettingsRepository`**

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsRepositoryTest*"`
Expected: PASS

- [ ] **Step 5: Implement `SettingsViewModel`/`SettingsScreen`** — a plain preference-style
  Composable (dropdown for default screen, switch for hidden files, text field for screenshot
  directory) bound to `SettingsRepository`.

- [ ] **Step 6: Manual smoke test**

Run: `./gradlew :app:installDebug`, navigate to Settings, change a value, back out and back in,
confirm it stuck.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/rel/adbhelper/settings app/src/test/kotlin/com/rel/adbhelper/settings
git commit -m "feat: add Settings screen backed by DataStore"
```

---

### Task 7: About + Feedback screens

**Files:**
- Create: `app/src/main/kotlin/com/rel/adbhelper/about/AboutScreen.kt`
- Create: `app/src/main/kotlin/com/rel/adbhelper/feedback/FeedbackScreen.kt`
- Modify: `app/src/main/kotlin/com/rel/adbhelper/navigation/AppNavHost.kt` (add both
  destinations, reachable from wherever the "more functions" menu lands — a placeholder menu
  entry is fine until the real hub exists in a later plan)

**Interfaces:**
- Consumes: nothing beyond navigation.
- Produces: two static Composables; no new testable logic (no test task needed — this is UI
  copy/layout only).

- [ ] **Step 1: Implement `AboutScreen`** — app version (read from
  `BuildConfig.VERSION_NAME`), a static open-source-licenses list entry (content filled in once
  the real library/binary set is pinned in later plans — a placeholder "licenses" list is fine
  for now).

- [ ] **Step 2: Implement `FeedbackScreen`** — a link to this repo's GitHub issues page, no
  donation QR codes, no personal contact details (per Non-goals in the spec).

- [ ] **Step 3: Wire both into `AppNavHost`**

- [ ] **Step 4: Manual smoke test**

Run: `./gradlew :app:installDebug`, navigate to both screens, confirm they render.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/rel/adbhelper/about app/src/main/kotlin/com/rel/adbhelper/feedback app/src/main/kotlin/com/rel/adbhelper/navigation/AppNavHost.kt
git commit -m "feat: add About and Feedback screens"
```
