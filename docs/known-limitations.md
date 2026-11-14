# Known limitations

## USB-OTG transport

Not implemented. The original app connects over USB-OTG as well as Wi-Fi; this rewrite
currently only supports network targets.

The dependency this project uses for the ADB protocol itself,
[`libadb-android`](https://github.com/MuntashirAkon/libadb-android) (latest published version:
3.1.1), only implements the TCP/TLS transport — there is no USB transport in any version of
that library. Supporting USB-OTG here means implementing the ADB wire protocol's handshake
(`AUTH`/`CNXN`) and framing (`OPEN`/`WRTE`/`OKAY`/`CLSE`) directly over raw
`UsbManager`/`UsbDeviceConnection` bulk-transfer endpoints — a substantial undertaking in its
own right, not a small addition on top of the existing network path.

The **USB-OTG** button on the Connect screen is a visible placeholder: it opens a dialog
explaining that USB isn't supported yet, rather than silently doing nothing or being absent
from the UI. Use a network connection (address entry, Scan, or Pair) in the meantime.

## Screen mirroring (scrcpy)

Not implemented. The original app includes full interactive screen mirroring/remote control
(video + input injection) based on [scrcpy](https://github.com/Genymobile/scrcpy)'s server and
protocol. Building this requires fetching and pushing `scrcpy-server.jar` to the target,
starting it via `app_process`, decoding its H.264 stream with `MediaCodec`, and implementing
its control-message protocol for input — each a significant piece of work on its own.

The **Mirror** tab in the device hub is a visible placeholder for the same reason as the USB
button above. The **Monitor** tab is a lighter substitute available today: a live view built
from repeated screenshots, not a real video stream, and view-only.

## No live-device CI

Connecting to and controlling a second physical Android device isn't something a CI runner can
exercise. The pure logic layer (protocol framing, command builders, text parsers) is unit
tested; everything that actually talks to a target is smoke-tested by hand.
