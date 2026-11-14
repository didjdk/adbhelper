# Contributing

## Ground rules

- English only — no localization.
- No monetization, no "premium" gating. Everything is free.
- No code, resources, or binaries copied from the original `didjdk/adbhelper` project — only
  genuine upstream libraries and official prebuilt binaries (AOSP `adb`/`fastboot`, Genymobile
  `scrcpy-server`), never anything extracted from the reference APK.
- Small, single-responsibility modules; short functions; names that say what they do.

## Code layout

See [Architecture](architecture.md) for the package structure and transport design.

## Testing

Pure logic (`core/*`) gets a real unit test — see existing tests under `app/src/test` for the
pattern (fixture-based parser tests, fake-transport `AdbClient` tests). UI and anything that
talks to a live device is smoke-tested by hand against a real target; note what you smoke
tested in your PR description rather than claiming automated coverage that doesn't exist.

## Building docs locally

This site is built with [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/):

```bash
pip install mkdocs-material
mkdocs serve
```

## Picking up a known gap

[Known limitations](known-limitations.md) lists the two biggest gaps (USB-OTG transport, screen
mirroring) along with what each would actually require. Either is a substantial, standalone
piece of work — expect to open its own design discussion before writing code.
