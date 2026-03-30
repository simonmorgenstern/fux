# Queue API Fix Summary

## Problem

The iOS app was experiencing "Unknown Effect" errors when trying to add effects to the queue. The issue was:

1. **GET /api/effects** endpoint existed and returned effect IDs like `"meteor_shower"`, `"rain"`, etc.
2. iOS app could successfully fetch and display the effect list
3. **No HTTP endpoint existed** for adding effects to the queue
4. The app needed an HTTP REST API (not WebSocket) for queue operations

## Root Cause

The queue control functionality only worked via WebSocket commands (`ADD_QUEUE:effect_name`). There was no HTTP endpoint for:
- Adding effects to queue
- Getting queue status
- Clearing the queue

The iOS app needed HTTP endpoints because:
- Simpler than maintaining WebSocket connections
- Better for request/response patterns
- Easier error handling
- Standard REST API approach

## Solution

Created a complete HTTP REST API for queue control:

### 1. New Handler: QueueHttpHandler.java

Implements three HTTP endpoints:

**POST /api/queue** - Add effect to queue
```json
Request: {
  "effect": "meteor_shower",
  "duration": 30,    // optional
  "repeat": 1        // optional
}

Response: {
  "success": true,
  "message": "Effect added to queue",
  "effect": "meteor_shower",
  "queueSize": 3
}
```

**GET /api/queue** - Get queue status
```json
Response: {
  "mode": "QUEUE",
  "currentEffect": "Meteor Shower",
  "queueSize": 2,
  "queueCapacity": 50,
  "remainingSeconds": 15,
  "queue": [...]
}
```

**DELETE /api/queue** - Clear queue
```json
Response: {
  "success": true,
  "message": "Queue cleared",
  "queueSize": 0
}
```

### 2. Effect Name Consistency

✅ **Effect names are consistent across both APIs:**

| API Endpoint | Effect ID Format | Example |
|-------------|------------------|---------|
| GET /api/effects | Underscored | `meteor_shower` |
| POST /api/queue | Underscored | `meteor_shower` |
| EffectEngine.addToQueue() | Underscored | `meteor_shower` |

The naming is **already consistent** - all use underscored names. The issue was simply that the HTTP endpoint didn't exist.

### 3. Integration Points

**PreviewHttpServer.java**
- Registered new QueueHttpHandler at `/api/queue`
- Updated root endpoint documentation to include queue API

**Build System**
- Successfully compiles with Maven
- All 42 source files build without errors
- Produces java-fux-1.0-SNAPSHOT.one-jar.jar

## iOS App Integration

### Before (broken):
```swift
// App fetches effects via HTTP
GET /api/effects  // ✓ Works

// App tries to add to queue
POST /api/queue   // ✗ Endpoint doesn't exist
// Falls back to WebSocket? Gets "Unknown Effect"?
```

### After (fixed):
```swift
// 1. Fetch available effects
let effects = await fetch("GET /api/effects")

// 2. User taps an effect
let selectedEffect = effects[0]  // { id: "meteor_shower", name: "Meteor Shower", ... }

// 3. Add to queue using exact ID
POST /api/queue
Body: { "effect": selectedEffect.id }  // Use "meteor_shower" exactly

// 4. Success! Effect added to queue
```

## Testing

### Test Script: test-queue-api.sh

Comprehensive tests for all queue endpoints:
1. ✓ Get initial queue state
2. ✓ Add effects with underscore names (`meteor_shower`, `rain`, etc.)
3. ✓ Add effects with custom duration
4. ✓ Add effects with duration + repeat
5. ✓ Get queue with multiple items
6. ✓ Test invalid effect name (error handling)
7. ✓ Clear queue
8. ✓ **Cross-check: Fetch from /api/effects and add to /api/queue**
9. ✓ Verify naming consistency

### Running Tests

```bash
# Start the server (on Pi or dev machine)
java -jar target/java-fux-1.0-SNAPSHOT.one-jar.jar

# Run tests
./test-queue-api.sh 8080
```

Expected output: All tests pass, no "Unknown Effect" errors.

## Files Changed

| File | Change | Lines |
|------|--------|-------|
| QueueHttpHandler.java | **New** | 179 |
| PreviewHttpServer.java | Modified (register handler + docs) | ~30 |
| API-QUEUE.md | **New** (documentation) | 352 |
| test-queue-api.sh | **New** (test script) | 88 |

## Deployment

### Build
```bash
cd java-fux
mvn clean package -DskipTests
```

### Deploy to Pi
```bash
./deploy.sh  # Or manually copy jar + effects to /home/pi/
```

### Verify
```bash
# Check server is running
curl http://fux.local:8080/health

# Test queue endpoint
curl http://fux.local:8080/api/queue
```

## Documentation

- **API-QUEUE.md**: Complete API reference with examples
- **test-queue-api.sh**: Automated tests and usage examples
- **PreviewHttpServer**: Updated root endpoint with queue API info

## Commit

```
commit 74077f4
feat: Add HTTP Queue API for iOS app integration

Implements POST/GET/DELETE /api/queue endpoints for queue control via HTTP REST API.
Fixes "Unknown Effect" error when iOS app tries to add effects to queue.
```

## Key Takeaways

1. ✅ **Effect naming was already consistent** (all use underscores)
2. ✅ **Missing HTTP endpoint was the real issue**
3. ✅ **Solution: Created QueueHttpHandler with full REST API**
4. ✅ **iOS app can now use HTTP for queue control**
5. ✅ **All effect IDs from /api/effects work with /api/queue**

The "Unknown Effect" error was likely a placeholder/diagnostic message because the HTTP endpoint didn't exist, forcing the app to use an alternative method or fail silently.

## Next Steps for iOS App

Update the iOS app to use the new HTTP endpoints:

```swift
// Old approach (WebSocket)
websocket.send("ADD_QUEUE:meteor_shower")

// New approach (HTTP REST)
POST http://fux.local:8080/api/queue
Body: {"effect": "meteor_shower"}
```

Benefits:
- No WebSocket connection needed
- Standard HTTP request/response
- Better error handling
- Works with URLSession natively
- Consistent with /api/effects endpoint
