# Queue Control API Documentation

REST API endpoints for controlling the LED effect queue.

## Endpoints

### `POST /api/queue`

Add an effect to the queue.

**Request Body (JSON):**
```json
{
  "effect": "meteor_shower",    // Required: effect ID (must match /api/effects)
  "duration": 30,               // Optional: seconds to play (overrides JSON default)
  "repeat": 1                   // Optional: number of times to repeat (default: 1)
}
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "Effect added to queue",
  "effect": "meteor_shower",
  "duration": 30,
  "repeat": 1,
  "queueSize": 3
}
```

**Error Response (400):**
```json
{
  "error": "Missing required field: effect",
  "status": 400
}
```

**Error Response (queue full or unknown effect):**
The error message from EffectEngine will be returned with appropriate status code.

---

### `GET /api/queue`

Get current queue state and playback status.

**Response (200):**
```json
{
  "mode": "QUEUE",
  "currentEffect": "Meteor Shower",
  "queueSize": 2,
  "queueCapacity": 50,
  "remainingSeconds": 15,
  "queue": [
    {
      "effect": "rain",
      "duration": 10
    },
    {
      "effect": "rainbow_pulse",
      "duration": 5,
      "repeat": 3
    }
  ]
}
```

**Fields:**
- `mode`: Current control mode (`"QUEUE"` or `"RANDOM"`)
- `currentEffect`: Name of currently playing effect (null if none)
- `queueSize`: Number of effects in queue
- `queueCapacity`: Maximum queue size (50)
- `remainingSeconds`: Seconds until current effect finishes (null if no effect playing)
- `queue`: Array of queued effects with their parameters

---

### `DELETE /api/queue`

Clear all effects from the queue.

**Response (200):**
```json
{
  "success": true,
  "message": "Queue cleared",
  "queueSize": 0
}
```

---

## Effect Names

The `effect` field in POST /api/queue **must exactly match** the effect `id` returned by GET /api/effects.

**Available Effects (as of v1.0):**
- `radial_wave`
- `rainbow_pulse`
- `sparkle`
- `fire`
- `breathing`
- `snake`
- `meteor_shower` ← Note: underscore, not camelCase!
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

**Best Practice:** Query GET /api/effects to get the current list of effect IDs, then use those IDs directly when adding to queue. This ensures forward compatibility as new effects are added.

---

## Usage Examples

### Add a simple effect
```bash
curl -X POST http://localhost:8080/api/queue \
  -H "Content-Type: application/json" \
  -d '{"effect": "rain"}'
```

### Add effect with custom duration
```bash
curl -X POST http://localhost:8080/api/queue \
  -H "Content-Type: application/json" \
  -d '{"effect": "meteor_shower", "duration": 10}'
```

### Add effect with duration and repeat
```bash
curl -X POST http://localhost:8080/api/queue \
  -H "Content-Type: application/json" \
  -d '{"effect": "rainbow_pulse", "duration": 5, "repeat": 3}'
```

### Check queue status
```bash
curl http://localhost:8080/api/queue
```

### Clear queue
```bash
curl -X DELETE http://localhost:8080/api/queue
```

### iOS/Swift example
```swift
struct AddEffectRequest: Codable {
    let effect: String
    let duration: Int?
    let repeat: Int?
}

func addEffectToQueue(effectId: String) {
    let url = URL(string: "http://fux.local:8080/api/queue")!
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    
    let body = AddEffectRequest(effect: effectId, duration: nil, repeat: nil)
    request.httpBody = try? JSONEncoder().encode(body)
    
    URLSession.shared.dataTask(with: request) { data, response, error in
        // Handle response
        if let data = data {
            let result = try? JSONDecoder().decode([String: Any].self, from: data)
            print("Queue add result:", result)
        }
    }.resume()
}

// Usage:
addEffectToQueue(effectId: "meteor_shower")  // ← Use exact ID from /api/effects!
```

---

## Integration with /api/effects

**Workflow for iOS App:**

1. **On app launch:** Fetch available effects
   ```swift
   GET /api/effects
   ```

2. **Display effect list:** Use `id` field for selection, `name` for display
   ```swift
   struct Effect {
       let id: String        // e.g., "meteor_shower"
       let name: String      // e.g., "Meteor Shower"
       let description: String
   }
   ```

3. **When user taps effect:** Send effect ID to queue
   ```swift
   POST /api/queue
   Body: {"effect": effect.id}  // Use the ID, not the name!
   ```

4. **Poll queue status:** Optionally fetch queue state every few seconds
   ```swift
   GET /api/queue
   ```

---

## Common Errors

### "Unknown effect: meteorShower"
**Problem:** You're using camelCase instead of underscore naming  
**Solution:** Use `meteor_shower`, not `meteorShower` or `MeteorShower`

### "Missing required field: effect"
**Problem:** JSON body is missing the `effect` field  
**Solution:** Ensure your JSON has `{"effect": "effect_name"}`

### "Queue is full (capacity: 50)"
**Problem:** Queue has reached maximum capacity  
**Solution:** Call DELETE /api/queue to clear it first

---

## CORS Support

All endpoints include CORS headers to allow cross-origin requests from web frontends.

---

## Testing

Run the test script to verify all queue endpoints:
```bash
./test-queue-api.sh [port]
```

Default port is 8080, or specify alternate port as argument.

The test script will:
- Add effects to queue
- Verify effect names match between /api/effects and /api/queue
- Test error handling for invalid effect names
- Clear the queue
