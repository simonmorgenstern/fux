# Fux Preview API

## Overview

The Preview API generates animated GIF previews of LED effects without requiring hardware rendering. This is perfect for testing effects, sharing previews with iOS apps, or creating effect libraries.

## Architecture

### Components

1. **PreviewRenderer.java** - Renders effect frames to in-memory pixel grid
   - Maps LED coordinates to a configurable resolution canvas
   - Renders only the first 268 LEDs (main strip)
   - Supports custom pixel sizes (8-16px recommended)
   - Reuses existing Effect interface and PixelCoordinates system

2. **GifEncoder.java** - Converts frame sequences to animated GIF
   - Uses GIF89a format for maximum compatibility
   - Includes Netscape extension for infinite looping
   - Implements LZW compression for efficiency
   - Configurable frame delay

3. **PreviewHttpHandler.java** - REST endpoint handler
   - Handles POST requests to `/api/preview`
   - Validates parameters and effect names
   - Manages effect instantiation and frame rendering
   - Returns animated GIF with appropriate headers

4. **PreviewHttpServer.java** - Standalone HTTP server
   - Runs on port 8080 (fallback to 8888 if unavailable)
   - Integrated into WebSocket.java for automatic startup
   - Provides health check endpoint at `/health`
   - API documentation at `/`

## API Usage

### Request Format

```bash
POST /api/preview HTTP/1.1
Content-Type: application/json

{
  "effect": "sparkle",
  "duration": 2,
  "fps": 15,
  "pixelSize": 8
}
```

### Parameters

| Parameter | Type | Default | Range | Description |
|-----------|------|---------|-------|-------------|
| `effect` | string | required | - | Effect name (e.g., "sparkle", "rainbow_pulse") |
| `duration` | number | 2.0 | 0.1-30 | Animation duration in seconds |
| `fps` | integer | 15 | 1-60 | Frames per second |
| `pixelSize` | integer | 8 | 1-32 | Rendered pixel size (smaller = higher res) |

### Response

- **Success (200)**: Binary GIF data with `Content-Type: image/gif`
- **Bad Request (400)**: JSON error message
- **Not Found (404)**: Effect not found
- **Internal Error (500)**: Server error with details

### Example cURL Request

```bash
curl -X POST http://localhost:8080/api/preview \
  -H "Content-Type: application/json" \
  -d '{
    "effect": "sparkle",
    "duration": 2,
    "fps": 15,
    "pixelSize": 8
  }' \
  > preview.gif
```

### Example JavaScript/Fetch

```javascript
const response = await fetch('http://localhost:8080/api/preview', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    effect: 'rainbow_pulse',
    duration: 3,
    fps: 20,
    pixelSize: 10
  })
});

const gifBlob = await response.blob();
const gifUrl = URL.createObjectURL(gifBlob);
document.getElementById('preview').src = gifUrl;
```

## Supported Effects

All effects in the EffectRenderer are supported:

- `radial_wave` - Waves emanating from center
- `rainbow_pulse` - Pulsing rainbow colors
- `sparkle` - Random sparkling effect
- `fire` - Fire simulation
- `breathing` - Breathing/fading effect
- `snake` - Moving snake pattern
- `meteor_shower` - Meteors falling across strip
- `firework` - Firework explosions
- `rain` - Rain effect
- `aurora` - Aurora/northern lights effect
- `bilateral_fill` - Bilateral fill pattern
- `gradient` - Color gradient transitions
- `box_mirror` - Mirrored box pattern
- `eye_blink` - Blinking eyes effect
- `diamond_pulse` - Diamond pulse pattern
- `box_wave` - Wave in box pattern
- `outside_spin` - Spinning outer edge
- `beat_pulse` - Whole-fox pulse on the beat, accent colour on downbeats
- `beat_sweep` - Horizontal band stepping down the fox, one band per beat
- `beat_sparkle` - Mirrored spark bursts per beat over a breathing wash

`GET /api/effects` is the authoritative list (38 effects).

The three `beat_*` effects are beat-aware: in `MUSIC` mode they render against the live
`BeatClock`, and in previews (and every other mode) against a fixed-tempo fallback driven by the
render time rather than the wall clock — so an offline preview animates correctly instead of
freezing on one frame.

## Spotify / Music Mode Endpoints

Music mode's one-time Spotify login is served from this same HTTP server under `/api/spotify`:

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/spotify/status` | GET | JSON: `configured`, `authorized`, `polling`, `redirectUri`, `loginUrl`, optional `error` and `nowPlaying` |
| `/api/spotify/login` | GET | HTML page with the authorize link and the paste-the-code form |
| `/api/spotify/callback` | GET | Token exchange from `?code=…` (or an error page for `?error=…`) |
| `/api/spotify/callback` | POST | Same exchange from the paste form, `code=…` form-encoded |
| `/callback` | GET/POST | The same handler at the bare path the redirect URI points at |
| `/api/spotify/logout` | POST | Forgets the stored tokens |

```bash
curl -s http://localhost:8080/api/spotify/status
# {"configured":false,"authorized":false,"polling":false,
#  "redirectUri":"http://127.0.0.1:8080/callback","loginUrl":"/api/spotify/login"}
```

Credentials come from `~/.fux/spotify.json` (or `SPOTIFY_CLIENT_ID` / `SPOTIFY_CLIENT_SECRET` /
`SPOTIFY_REDIRECT_URI`); the refresh token is written to `~/.fux/spotify_tokens.json`. Full setup
walkthrough in `FUX_IOSAPP_API.md`.

## Coordinate System

The preview renderer uses the coordinate system from `pixelCoordinates.json`:

- Each LED has an (x, y) position
- The renderer maps these to a 2D canvas
- Canvas size is automatically calculated based on LED bounds
- 10% padding is added around the edges
- Pixel size parameter controls the resolution

Example coordinate structure:
```json
[
  { "index": 0, "x": 226, "y": 600 },
  { "index": 1, "x": 241, "y": 588 },
  ...
]
```

## Implementation Details

### Rendering Pipeline

1. **Initialization**: Load PixelCoordinates and create PreviewRenderer
2. **Frame Generation**: Call effect.renderFrame() for each frame
3. **Pixel Mapping**: Map LED indices to canvas positions
4. **Image Encoding**: Render each frame as BufferedImage
5. **GIF Encoding**: Encode frames with LZW compression

### Canvas Calculation

```java
// Find bounds of first 268 LEDs
minX, maxX = LED coordinate extremes
minY, maxY = LED coordinate extremes

// Add 10% padding
padding = 10%

// Calculate canvas dimensions
canvasWidth = (width / pixelSize) + 1
canvasHeight = (height / pixelSize) + 1
```

### GIF Format

- **Format**: GIF89a (animated GIF)
- **Color Table**: 256-color global palette (grayscale)
- **Looping**: Netscape extension for infinite loop
- **Compression**: LZW (8-bit codes)
- **Frame Delays**: Configurable per FPS setting

## Performance Considerations

### Optimization Tips

1. **Reduce Duration**: Shorter animations use fewer frames
2. **Lower FPS**: Fewer frames per second = smaller GIFs
3. **Smaller Canvas**: Use larger pixelSize values (16+ for mobile)
4. **Limit Requests**: Cache GIF results on the client side

### Benchmarks

- Small effect (8px size, 2sec, 15fps): ~10KB
- Medium effect (8px size, 3sec, 20fps): ~50KB
- High-res effect (4px size, 5sec, 30fps): ~200KB

## Integration with Fux.java

The preview server starts automatically when the WebSocket server starts:

```java
// In WebSocket.java constructor
previewHttpServer = new PreviewHttpServer(effectEngine);
previewHttpServer.start();
```

The HTTP server runs on a separate port (8080/8888) from the WebSocket server (80), allowing both to run simultaneously.

## Testing

### Quick Test

```bash
# Start the fux server
cd /home/pi/fux/java-fux
java -jar target/java-fux-1.0-SNAPSHOT.one-jar.jar

# In another terminal
curl -X POST http://localhost:8080/api/preview \
  -H "Content-Type: application/json" \
  -d '{"effect":"sparkle","duration":2,"fps":15,"pixelSize":8}' \
  > test.gif

# View the GIF
open test.gif
```

### Health Check

```bash
curl http://localhost:8080/health
# Response: {"status":"ok","timestamp":1234567890}
```

### API Documentation

```bash
curl http://localhost:8080/
```

## Troubleshooting

### Port Conflict

If port 8080 is in use, the server automatically falls back to 8888. Check the logs:

```
HTTP server will run on port: 8888
```

### Effect Not Found

Ensure the effect name is correct and matches the switch statement in `PreviewHttpHandler.instantiateEffect()`.

### Large GIF Files

- Reduce duration
- Reduce FPS
- Increase pixelSize (use 16 or 32 for mobile)
- Request multiple short clips instead of one long animation

### Memory Usage

For very long animations, the system loads all frames in memory. If memory is limited:
- Reduce frame count (lower FPS or duration)
- Reduce canvas size (increase pixelSize)
- Restart the server periodically

## iOS App Integration

The animated GIFs can be displayed directly in iOS WebViews:

```html
<img src="http://server:8080/api/preview" alt="Effect Preview">
```

Or programmatically:

```swift
let task = URLSession.shared.dataTask(with: url) { data, response, error in
    if let data = data {
        let image = UIImage(data: data)
        imageView.image = image
    }
}
task.resume()
```

## Future Enhancements

Potential improvements for future versions:

1. **Video Output**: MP4/WebM for larger screens
2. **Static Images**: Single-frame PNG exports
3. **Color Adjustment**: Brightness/saturation parameters
4. **Custom Palettes**: Override default grayscale palette
5. **Effect Chaining**: Multiple effects in one preview
6. **Caching**: Store generated GIFs for repeated requests
7. **WebP Format**: Better compression for smaller files
