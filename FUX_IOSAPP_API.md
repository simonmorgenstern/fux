# Fux LED Controller - iOS App API v2.0

## Overview
The Fux WebSocket API now supports **Queue Mode** and **Random Mode** for effect control. The server broadcasts state changes to all connected clients.

## WebSocket Connection
- **Host:** `fux.local` (or `192.168.178.82`)
- **Port:** `80`
- **Protocol:** WebSocket (ws://)

## Message Protocol

### 1. Mode Control

#### Switch to Queue Mode
```
MODE:QUEUE
```
Effects are played sequentially from the queue. Each effect plays for a minimum of 2 seconds before the next one starts.

#### Switch to Random Mode
```
MODE:RANDOM
```
Effects are randomly selected from available effects. Each plays for 2+ seconds before switching.

#### Switch to Music Mode
```
MODE:MUSIC
```
The backend polls Spotify for what you are playing and drives a beat-aware effect from the track's
tempo. Requires a one-time Spotify login (see **Spotify Endpoints** below). With nothing playing —
or with no credentials configured — the mode still runs and falls back to `aurora`, reporting the
reason in `music.error`.

The effect is drawn from a pool of eight beat-aware effects (`beat_pulse`, `beat_sweep`,
`beat_sparkle`, `firework`, `heartbeat`, `strobe`, `center_pulse`, `center_heartbeat`) and changes
on every track change as well as every 32 bars within a long track, so `currentEffect` in the
`STATE` payload can change mid-song.

---

### 1b. Music Mode Tuning

#### Nudge the Beat Grid
```
MUSIC_OFFSET:+150
MUSIC_OFFSET:-50
MUSIC_OFFSET:0
MUSIC_OFFSET:250
```
Shifts the beat grid in milliseconds, to compensate for network and audio latency. A **signed**
value (`+`/`-`) is applied relative to the current offset; an **unsigned** value sets it absolutely;
`0` resets it. The offset is stored per track in `~/.fux/beat_offsets.json` and reapplied whenever
that track comes round again.

#### Set the Tempo Manually
```
MUSIC_BPM:128
```
Used when no BPM could be looked up for the track (`music.bpm` is null). The value is remembered in
the BPM cache for that track with `bpmSource: "manual"`.

Both commands are safe to send with nothing playing — they are ignored.

---

### 2. Queue Operations

#### Add Effect to Queue
```
ADD_QUEUE:effect_name
```
**Example:**
```
ADD_QUEUE:aurora
ADD_QUEUE:snake
ADD_QUEUE:rainbow_pulse
```

**Available Effects:**
- `radial_wave`
- `rainbow_pulse`
- `sparkle`
- `fire`
- `breathing`
- `snake`
- `meteor_shower`
- `firework`
- `rain`
- `aurora`
- `bilateral_fill`
- `gradient`
- `box_mirror`
- `eye_blink`
- `diamond_pulse`
- `box_wave`
- `outside_spin`

#### Clear Queue
```
CLEAR_QUEUE
```
Removes all queued effects. The current effect continues until completion or next effect becomes available.

#### Get Current State
```
GET_STATE
```
Returns the current state object (see **State Messages** below).

---

### 3. State Messages (Server → Client)

The server broadcasts state updates whenever something changes (mode switched, effect queued, current effect changed, etc.).

#### State Object
```json
{
  "type": "STATE",
  "mode": "QUEUE",
  "currentEffect": "Aurora Borealis",
  "queue": ["snake", "rainbow_pulse"],
  "queueCapacity": 50,
  "timestamp": 1773868224487,
  "remainingSeconds": 2
}
```

**Fields:**
- `mode`: `"QUEUE"`, `"RANDOM"`, `"IDLE"`, `"OFF"` or `"MUSIC"`
- `currentEffect`: Currently playing effect name (null if none)
- `queue`: Array of queued effect names (in order)
- `queueCapacity`: Maximum queue size (50)
- `timestamp`: Unix timestamp in milliseconds
- `remainingSeconds`: Seconds until current effect finishes (null if no effect playing; always null in `MUSIC` mode, which has no fixed duration)
- `music`: Present **only in `MUSIC` mode** (see below)

#### Music Object

```json
{
  "type": "STATE",
  "mode": "MUSIC",
  "currentEffect": "Beat Pulse",
  "queue": [],
  "queueCapacity": 50,
  "timestamp": 1773868224487,
  "remainingSeconds": null,
  "music": {
    "configured": true,
    "authorized": true,
    "polling": true,
    "playing": true,
    "synced": true,
    "trackId": "2Foc5Q5nqNiosCNqttzHof",
    "title": "Get Lucky",
    "artist": "Daft Punk",
    "bpm": 116.1,
    "bpmSource": "deezer",
    "offsetMs": 120,
    "beatPhase": 0.37,
    "positionSeconds": 84.2
  }
}
```

**Fields:**
- `configured`: `~/.fux/spotify.json` (or the env vars) supplies a client id and secret
- `authorized`: A Spotify login has been completed and the refresh token is stored
- `polling`: The background poller is running
- `playing`: Spotify reports playback in progress
- `synced`: A tempo is known and the beat clock is locked — the beat-aware effect is running. When false, the mode falls back to `aurora`
- `trackId` / `title` / `artist`: Now playing (null when nothing is)
- `bpm`: Resolved tempo, or null when unknown — that is the cue to offer manual BPM entry
- `bpmSource`: `"deezer"`, `"getsongbpm"`, `"manual"` or `"unknown"`
- `offsetMs`: Current beat-grid offset for this track (see `MUSIC_OFFSET:`)
- `beatPhase`: Position within the current beat, `0.0`–`1.0`, at `timestamp`. State is pushed about once a second, so interpolate locally between pushes for a smooth beat indicator
- `positionSeconds`: Interpolated playback position
- `error`: Present only when something is wrong (not configured, not authorized, Spotify unreachable). Reported once, not on every push

#### Error Messages
```json
{
  "type": "ERROR",
  "message": "Unknown effect: invalid_name",
  "timestamp": 1773868224487
}
```

---

## Spotify Endpoints (HTTP, port 8080)

Music mode needs a one-time Spotify login. These live on the REST server, not the WebSocket.

| Endpoint | Purpose |
|---|---|
| `GET /api/spotify/status` | JSON: `configured`, `authorized`, `polling`, `redirectUri`, `loginUrl`, optional `error` and `nowPlaying` |
| `GET /api/spotify/login` | HTML page: the "Connect Spotify" authorize link plus the paste-the-code form |
| `GET /api/spotify/callback?code=…` | Token exchange when the redirect reaches the fux |
| `POST /api/spotify/callback` | Same exchange, `code=…` form-encoded, from the paste form |
| `GET /callback` | The same handler at the bare path the redirect URI points at, so a login driven from a browser on the fux itself completes with no paste step |
| `POST /api/spotify/logout` | Forgets the stored tokens |

### Setup (once, by hand)

1. Create an app at the Spotify developer dashboard and add
   `http://127.0.0.1:8080/callback` as a redirect URI.
2. Put the credentials on the fux in `~/.fux/spotify.json` (written owner-only):
   ```json
   {
     "clientId": "…",
     "clientSecret": "…",
     "redirectUri": "http://127.0.0.1:8080/callback"
   }
   ```
   `SPOTIFY_CLIENT_ID` / `SPOTIFY_CLIENT_SECRET` / `SPOTIFY_REDIRECT_URI` work instead.
3. Open `http://<fux>:8080/api/spotify/login` in a browser and click **Connect Spotify**.
4. Spotify only accepts HTTPS or loopback redirect URIs, so the redirect goes to `127.0.0.1` —
   **your own machine**, not the fux — and the page will fail to load. That is expected: copy the
   `code` value out of the address bar and paste it into the form on the login page.

   The one exception is doing the login from a browser running on the fux itself, where
   `127.0.0.1:8080` *is* the fux: `/callback` is served there, so it completes with no paste.

> The redirect URI is deliberately kept in the shape of Spotify's own documented example
> (`http://127.0.0.1:8000/callback`). The dashboard's validator rejects anything it does not like
> with a bare *"Please enter a valid redirect URI"* — notably `localhost` (must be the literal
> `127.0.0.1` or `[::1]`), plain `http://` on any non-loopback host, and hyphens anywhere in the
> path. Whatever you register must match `redirectUri` in `spotify.json` character for character.

The refresh token is stored in `~/.fux/spotify_tokens.json` and the access token is refreshed
automatically, so this is needed only once.

### In the App

`ControlModel` exposes `spotifyLoginURL` — show it as a setup card whenever `music.configured` or
`music.authorized` is false.

---

## iOS App Implementation Guide

### Connection Flow
1. Connect to WebSocket: `ws://fux.local:80`
2. On open: Send `GET_STATE` to fetch initial state
3. Listen for all incoming messages (state updates + errors)
4. Parse `type` field: `STATE` or `ERROR`

### UI Elements
- **Mode Toggle:** Button to switch `MODE:QUEUE` ↔ `MODE:RANDOM` ↔ `MODE:MUSIC`
- **Effect List:** Picker with 18 available effects
- **Add Button:** Sends `ADD_QUEUE:effect_name`
- **Clear Button:** Sends `CLEAR_QUEUE`
- **Status Display:** Shows:
  - Current effect + remaining seconds
  - Queue list (next 5 effects)
  - Current mode
  - Error messages

### Example Flow (Queue Mode)
```
User taps "Aurora"          → Send: ADD_QUEUE:aurora
Server broadcasts STATE     → UI updates: queue now has aurora
User taps "Snake"           → Send: ADD_QUEUE:snake
Server broadcasts STATE     → UI updates: queue has [aurora, snake]
Effect finishes (2s)        → Server plays aurora
Server broadcasts STATE     → UI: currentEffect=aurora, remainingSeconds=2
Aurora finishes             → Server plays snake
Server broadcasts STATE     → UI: currentEffect=snake, queue=[], remainingSeconds=2
```

### Error Handling
Listen for `ERROR` messages:
- `"Unknown effect: ..."` → Invalid effect name (shouldn't happen with your picker)
- `"Queue is full (capacity: 50)"` → User has added 50+ effects (clear to add more)

### Connection Loss
If WebSocket closes:
1. Show "Disconnected" indicator
2. Attempt reconnect every 3-5 seconds
3. On reconnect: Send `GET_STATE`

---

## Testing Commands (Direct WebSocket)
```bash
# Switch to queue mode
MODE:QUEUE

# Add 3 effects
ADD_QUEUE:rainbow_pulse
ADD_QUEUE:aurora
ADD_QUEUE:snake

# Get state
GET_STATE

# Clear queue
CLEAR_QUEUE

# Switch to random mode
MODE:RANDOM

# Music mode, then nudge the beat 150ms later and reset it
MODE:MUSIC
MUSIC_OFFSET:+150
MUSIC_OFFSET:0
MUSIC_BPM:128
```

---

## Changes from v1.0
- ✅ Queue system with 50-effect capacity
- ✅ Two control modes (QUEUE, RANDOM)
- ✅ Real-time state broadcasts
- ✅ Minimum 2-second effect display time
- ✅ 18 effects supported
- ✅ Error messages for invalid commands
