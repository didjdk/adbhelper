# Getting started

## Requirements

- Android Studio or a JDK 17+ toolchain, plus the Android SDK (minSdk 26, targetSdk 36).
- A target Android device or emulator reachable over the same network, with
  [wireless debugging](https://developer.android.com/tools/adb#wireless) enabled — or a plain
  `ip:port` if you've already run `adb tcpip`.

## Build and run

```bash
./gradlew :app:assembleDebug
```

Install the resulting APK (`app/build/outputs/apk/debug/`) on the controlling device, or run
directly from Android Studio.

## Run the tests

```bash
./gradlew :app:testDebugUnitTest
```

Unit tests cover the pure logic layer (`core/*`): protocol framing, command builders, and the
text parsers for `getprop`, `ps`, `ls -la`, and `pm`/`dumpsys package` output. There's no
live-device integration suite — connecting to and controlling a second physical device isn't
something CI can exercise, so that part is smoke-tested by hand against a real target.

## Connecting to a target

1. Open the app and enter the target's `ip:port` (find it via `adb tcpip 5555` or the target's
   own wireless-debugging screen), or tap **Scan** to sweep the local subnet, or **Pair** to use
   a wireless-debugging pairing code.
2. On first connect, accept the "Allow debugging" prompt that appears on the target's screen.
3. Once connected, the app lands on the device hub — a row of tabs (Info, Apps, Files, Shell,
   Power, Monitor) for everything else.

See [Features](features.md) for what each tab does.
