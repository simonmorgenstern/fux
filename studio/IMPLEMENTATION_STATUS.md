# Implementation status — 2026-09-05

Implemented the native Mac app, Studio HTTP API, isolated draft rendering,
arrangement persistence and autonomous arrangement selection in MUSIC mode.
The existing iPhone app needs no protocol change.

Verified locally:

- Xcode builds a macOS application; Swift Package Manager builds and runs five tests.
- Six Java tests cover beat/clip boundaries, validation, atomic persistence,
  shared rendering, real MUSIC selection and HTTP save/load/preview isolation.
- Opened the native app and verified its layout, library drag/drop, beat snapping,
  clip move/resize, and the Undo menu action.
- Used a temporary localhost fixture to load a song, add/move/resize a clip,
  preview, save and reload. No Pi or Spotify credentials were used for these checks.

Not deployed to the Raspberry Pi. The actual Spotify-to-LED timing and performance
on the installation remain unverified. Distribution signing/notarization is not
configured. See README.md for build and connection instructions.
