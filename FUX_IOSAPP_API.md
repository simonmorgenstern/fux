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
- `mode`: `"QUEUE"` or `"RANDOM"`
- `currentEffect`: Currently playing effect name (null if none)
- `queue`: Array of queued effect names (in order)
- `queueCapacity`: Maximum queue size (50)
- `timestamp`: Unix timestamp in milliseconds
- `remainingSeconds`: Seconds until current effect finishes (null if no effect playing)

#### Error Messages
```json
{
  "type": "ERROR",
  "message": "Unknown effect: invalid_name",
  "timestamp": 1773868224487
}
```

---

## iOS App Implementation Guide

### Connection Flow
1. Connect to WebSocket: `ws://fux.local:80`
2. On open: Send `GET_STATE` to fetch initial state
3. Listen for all incoming messages (state updates + errors)
4. Parse `type` field: `STATE` or `ERROR`

### UI Elements
- **Mode Toggle:** Button to switch `MODE:QUEUE` ↔ `MODE:RANDOM`
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
```

---

## Changes from v1.0
- ✅ Queue system with 50-effect capacity
- ✅ Two control modes (QUEUE, RANDOM)
- ✅ Real-time state broadcasts
- ✅ Minimum 2-second effect display time
- ✅ 18 effects supported
- ✅ Error messages for invalid commands
