# Fux Studio

A native macOS editor for Spotify music arrangements on the Fux. Requires macOS
13 or later. Uses SwiftUI, with no third-party Mac dependencies.

## Run

Open `FuxStudio.xcodeproj` in Xcode and run the **FuxStudio** scheme, or:

```sh
cd studio
./build.sh
open build/Build/Products/Debug/FuxStudio.app
```

The unsigned development build is for local use. Distribution signing and
notarization are not configured.

## Connect your fox

The Pi needs the backend from this revision. Build it with `mvn package` in
`java-fux/`, then use the project's existing Pi deployment workflow. The editor
reports an incompatible server if it is connected to an older backend.

1. Enter the Pi HTTP address, normally `http://fux.local:8080`. Use port 8888 if
   the backend selected that fallback port. This is not the WebSocket port.
2. Connect, play a song in Spotify, and choose **Current song**. Existing Spotify
   credentials stay on the Pi; the editor requests no new Spotify permissions.
3. Drag one of the eight music effects into the bottom timeline, or use its +
   button to place it at the playhead. Move clips, drag their right edge to resize,
   or enter exact beat values in the inspector. Press Return to apply numeric edits.
4. Set BPM and first-beat offset as needed. If no BPM is known, the editor starts
   with an explicitly labelled 120 BPM grid that should be checked before saving.
5. **Follow Spotify** auditions the draft against current playback. Clicking the
   ruler inspects a still frame; it does not seek or change Spotify playback.
6. **Save to Fux** stores the arrangement on the Pi. While Music mode is active,
   saving updates the running arrangement at the current song position.

The iPhone Music button is unchanged. The Pi uses the arrangement associated with
that exact Spotify track ID, and uses automatic music effects for other songs.
The Mac can disconnect or close after saving. Spotify still needs to be playing
on its usual device, and the Pi still needs Spotify/network access.

## Editing behavior

- One non-overlapping lane, whole-fox effects, hard cuts and one-beat drag snapping.
- Start beat is zero-based; timeline bar numbers are one-based.
- Gaps are black. Paused or stale live playback falls back to the existing ambient
  effect on the physical fox. The editor freezes its draft preview on pause.
- Grid edits preserve musical clip positions. Edits that move a clip outside the
  song or overlap another clip are rejected with an explanation.
- Undo/redo: Command-Z / Shift-Command-Z. Save: Command-S. Duplicate/delete are in
  the inspector and each clip's context menu.
- Draft edits are autosaved to `~/Library/Application Support/FuxStudio/draft.json`.
  Recovery restores the last draft, conservatively marked unsaved. This is a local
  recovery copy; only **Save to Fux** changes the Pi's saved arrangement.
- Loading a different song asks before replacing unsaved edits. A Spotify song
  change preserves the open draft and leaves follow mode.
- **Explore the editor** opens an editable example without needing the Pi. Its LED
  preview requires a connected backend; the example cannot be saved to the Pi.

## API

All endpoints are beneath `/api/studio` on the existing HTTP server.

| Method/path | Purpose |
| --- | --- |
| `POST /connect` | Start Spotify observation without changing the LED mode; return status |
| `GET /status` | Version, music state, duration and current mode |
| `GET /layout` | The 268 LED coordinates used by the renderer |
| `GET /arrangements/{trackId}` | Load; 404 means no saved arrangement |
| `PUT /arrangements/{trackId}` | Validate, atomically save, and make available to playback |
| `POST /preview` | Isolated draft render; return 268 packed RGB integers |

Preview body: `sessionId`, `arrangement`, `positionSeconds`. Reuse a session ID
for continuous playback. Sessions have a 30-second idle timeout and an eight-session
limit; each request is limited to 512 KiB. A changed draft or seek resets the
appropriate effect. Preview uses the same arrangement renderer and presets as the
physical output, without changing live mode or saving the draft.

Saved files live at `~/.fux/arrangements/{spotifyTrackId}.json` on the Pi. Format:

```json
{
  "version": 1,
  "trackId": "0123456789012345678901",
  "title": "Example",
  "artist": "Artist",
  "durationSeconds": 180,
  "bpm": 120,
  "offsetMs": 0,
  "beatsPerBar": 4,
  "clips": [
    {"id": "clip-1", "effect": "beat_pulse", "startBeat": 0, "lengthBeats": 32}
  ]
}
```

The endpoints use the project's existing trusted-LAN access model. They do not
provide an Internet-facing authenticated service.

## Verification

```sh
# Backend model, persistence, real scheduler and HTTP integration tests
cd java-fux
mvn test

# Mac model and editing tests, from fux/
swift test --package-path studio
```

`StudioFixtureServer` in the backend test sources can run an isolated localhost
server on port 18765 for manual UI verification, using the Maven test classpath.
It uses temporary storage and a synthetic song; it never contacts Spotify or GPIO.

Known v1 limits: no waveform, local audio, Spotify transport controls, blending,
parameter automation, automatic downbeat detection, or variable tempo. Existing
Spotify polling can delay recognition of seeks and song changes. Stateful particle
effects restart after seeking; the exact random particles are not reproduced.
The Mac preview requests approximately 15 frames per second; the shared renderer
advances the effect at its own frame rate. Physical Pi timing and performance
still need to be checked on the actual installation after deployment.
