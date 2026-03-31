# Idle Mode - Continuous Effect Playback

## Overview
Idle mode allows effects to play continuously until replaced by another effect. Perfect for ambient lighting that stays on indefinitely.

## Features
- **Indefinite duration**: Effects loop continuously
- **Easy replacement**: Send a new idle effect to replace the current one
- **No auto-switching**: Unlike RANDOM/QUEUE modes, idle mode holds the effect
- **HTTP API + WebSocket**: Control via REST or WebSocket commands

## Usage

### HTTP API (Recommended)

**Set idle effect:**
```bash
curl -X POST http://fux.local:8080/api/idle \
  -H "Content-Type: application/json" \
  -d '{"effect": "diamond_pulse"}'
```

**Using the helper script:**
```bash
./set_idle_effect.sh diamond_pulse
./set_idle_effect.sh aurora
./set_idle_effect.sh rain
```

**Response:**
```json
{
  "success": true,
  "mode": "IDLE",
  "effect": "diamond_pulse",
  "message": "Effect playing in idle mode (indefinite duration)"
}
```

### WebSocket Commands

```
# Set idle mode and play effect
IDLE:diamond_pulse

# Just switch to idle mode (keeps current effect)
MODE:IDLE

# Check state
GET_STATE
```

### Switching Modes

**From idle to queue mode:**
```bash
curl -X POST http://fux.local:8080/api/queue \
  -H "Content-Type: application/json" \
  -d '{"effect": "rain", "duration": 30}'
```

**From idle to random mode:**
```
# WebSocket
MODE:RANDOM
```

## Available Effects

All effects work in idle mode:
- `diamond_pulse` - Diamond pattern breathing
- `aurora` - Northern lights simulation
- `rain` - Falling rain effect
- `fire` - Flickering fire effect
- `breathing` - Smooth breathing animation
- `rainbow_pulse` - Rainbow color cycling
- `meteor_shower` - Shooting stars
- `sparkle` - Random sparkling LEDs
- `snake` - Snake animation
- `radial_wave` - Radial wave from center
- `eye_blink` - Animated eye blinking
- `box_wave` - Wave across LED boxes
- `outside_spin` - Spinning outside ring

## How It Works

1. **Mode Switch**: When you call `/api/idle` or send `IDLE:effect_name`, the system:
   - Switches to `ControlMode.IDLE`
   - Clears the effect queue
   - Loads the specified effect with duration = -1 (indefinite)

2. **Render Loop**: In idle mode:
   - Effect keeps rendering frames continuously
   - No auto-switching to next effect
   - Remaining time shows `null` (indefinite)

3. **Replacement**: To change effects:
   - Send another idle command (replaces immediately)
   - Or switch to QUEUE/RANDOM mode

## State Response

```json
{
  "mode": "IDLE",
  "currentEffect": "diamond_pulse",
  "queue": [],
  "queueCapacity": 50,
  "timestamp": 1680123456789,
  "remainingSeconds": null
}
```

Note: `remainingSeconds` is `null` in idle mode (indefinite duration).

## Deployment

**Build and deploy:**
```bash
cd java-fux
mvn clean package
scp target/java-fux-1.0-SNAPSHOT.one-jar.jar pi@fux.local:/home/pi/fux/
ssh pi@fux.local "sudo systemctl restart fux"
```

**Test:**
```bash
./set_idle_effect.sh diamond_pulse
```

## Implementation Details

- **Files Modified:**
  - `ControlMode.java` - Added `IDLE` enum value
  - `EffectEngine.java` - Added `playIdle()` method and idle mode handling
  - `WebSocket.java` - Added `MODE:IDLE` and `IDLE:` commands
  - `PreviewHttpServer.java` - Registered `/api/idle` endpoint
  - `IdleHttpHandler.java` - New HTTP handler for idle mode

- **Commits:**
  - `5242afa` - Add IDLE mode for continuous effect playback
  - `221831f` - Add helper script for setting idle effects
  - `6b6f8f6` - Fix: Bundle led-groups.json in jar
