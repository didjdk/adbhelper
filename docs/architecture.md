# Architecture

## Stack

- 100% Kotlin, Jetpack Compose + Material 3, single Activity, Navigation-Compose.
- MVVM: one Gradle module (`:app`), organized as small per-feature packages. Each feature
  package holds its Composable screen, a `ViewModel`, and (where it needs its own I/O) a thin
  service type behind an interface, so the `ViewModel` can be unit-tested with a fake.
- Settings/recents persistence via Jetpack `DataStore`.
- JUnit5 for pure-logic unit tests.

## Transport

The ADB wire protocol itself isn't hand-rolled. `core/adb` wraps
[`io.github.muntashirakon:libadb-android`](https://github.com/MuntashirAkon/libadb-android), an
actively maintained open-source ADB client library, behind a small `AdbTransport` interface:

- `NetworkAdbTransport` — the library's TCP/TLS transport against `host:port`, via
  `AdbKeyConnectionManager`, a singleton that owns this app's RSA key pair and self-signed
  certificate (generated with BouncyCastle, not platform hidden APIs).
- `AdbClient` sits on top of `AdbTransport` and exposes the actual command surface
  (`shell`, `raw`, `push`, `pull`, `openService`) that every feature's `ViewModel` calls into —
  everything above this line is transport-agnostic.

A USB-OTG transport is **not** implemented — see [Known limitations](known-limitations.md).

## Session

`core/session/DeviceSession` is a small singleton that carries the connected `AdbClient` from
the Connect screen into the device hub (`DeviceScreen`), which hosts one tab per feature.

## Pure logic layer

Everything that can be tested without a live device lives in `core/*` as plain functions:
protocol framing, command builders, and text parsers for `getprop`, `ps`, `ls -la`, and
`pm`/`dumpsys package` output, each covered by a unit test against captured real device output.

## Fastboot

There's no comparable well-known Android-hosted client library for fastboot, so reboot-to-
bootloader flows are expected to shell out to a genuine prebuilt `fastboot` binary via
`ProcessBuilder` once that part of the design lands, rather than a hand-rolled USB fastboot
client.
