# GIF Preview Endpoint - Quick Start

## Build & Run

```bash
# Build the project
cd java-fux
mvn clean package -DskipTests

# Run the server
java -jar target/java-fux-1.0-SNAPSHOT.one-jar.jar

# Server starts with:
# - WebSocket on port 80
# - Preview API on port 8080
```

## Basic Usage

### 1. Check if API is running
```bash
curl http://localhost:8080/health
# Response: {"status":"ok","timestamp":1234567890}
```

### 2. Generate a sparkle effect preview
```bash
curl -X POST http://localhost:8080/api/preview \
  -H "Content-Type: application/json" \
  -d '{
    "effect": "sparkle",
    "duration": 2,
    "fps": 15,
    "pixelSize": 8
  }' \
  > sparkle.gif

# View the GIF
open sparkle.gif  # macOS
# or
xdg-open sparkle.gif  # Linux
```

### 3. Try different effects
```bash
# Rainbow pulse
curl -X POST http://localhost:8080/api/preview \
  -H "Content-Type: application/json" \
  -d '{"effect":"rainbow_pulse","duration":2,"fps":15,"pixelSize":8}' \
  > rainbow.gif

# Fire effect
curl -X POST http://localhost:8080/api/preview \
  -H "Content-Type: application/json" \
  -d '{"effect":"fire","duration":2,"fps":15,"pixelSize":8}' \
  > fire.gif

# Aurora
curl -X POST http://localhost:8080/api/preview \
  -H "Content-Type: application/json" \
  -d '{"effect":"aurora","duration":3,"fps":20,"pixelSize":8}' \
  > aurora.gif
```

## Available Effects

```
radial_wave        | Waves from center
rainbow_pulse      | Pulsing rainbow
sparkle           | Random sparkles
fire              | Fire simulation
breathing         | Breathing fade
snake             | Moving pattern
meteor_shower     | Falling meteors
firework          | Explosions
rain              | Rain effect
aurora            | Northern lights
bilateral_fill    | Fill pattern
motion_blur       | Blur effect
gradient          | Color gradient
box_mirror        | Mirror pattern
eye_blink         | Blinking eyes
diamond_pulse     | Diamond shape
box_wave          | Wave pattern
outside_spin      | Spinning edge
```

## Parameters

| Param | Default | Range | Notes |
|-------|---------|-------|-------|
| `effect` | required | - | Effect name (case-insensitive) |
| `duration` | 2 | 0.1-30 | Seconds of animation |
| `fps` | 15 | 1-60 | Frames per second |
| `pixelSize` | 8 | 1-32 | Canvas resolution (higher = smaller GIF) |

## Tips for Best Results

### For Web/Mobile
```json
{
  "effect": "sparkle",
  "duration": 2,
  "fps": 15,
  "pixelSize": 12
}
```
- Larger pixelSize = smaller file size
- 15 FPS is good for smooth looping
- 2-3 seconds is ideal for GIFs

### For High Quality
```json
{
  "effect": "rainbow_pulse",
  "duration": 3,
  "fps": 20,
  "pixelSize": 6
}
```
- Smaller pixelSize = higher resolution
- 20 FPS for smoother animation
- 3 seconds = longer but still reasonable

### For Quick Testing
```json
{
  "effect": "fire",
  "duration": 1,
  "fps": 10,
  "pixelSize": 16
}
```
- 1 second = fast generation
- 10 FPS = minimal frames
- Fastest response time

## In Your iOS App

```swift
import UIKit

let imageView = UIImageView()

// Generate preview
let request = URLRequest(url: URL(string: "http://your-server:8080/api/preview")!)
var urlRequest = URLRequest(url: url)
urlRequest.httpMethod = "POST"
urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")

let body: [String: Any] = [
    "effect": "sparkle",
    "duration": 2,
    "fps": 15,
    "pixelSize": 8
]
urlRequest.httpBody = try? JSONSerialization.data(withJSONObject: body)

// Load GIF
URLSession.shared.dataTask(with: urlRequest) { data, response, error in
    if let data = data {
        let image = UIImage(data: data)
        imageView.image = image
    }
}.resume()
```

## In Your Web App

```html
<!DOCTYPE html>
<html>
<head>
    <title>Fux Preview</title>
</head>
<body>
    <img id="preview" src="" alt="Effect Preview">
    
    <script>
        async function generatePreview(effectName) {
            const response = await fetch('http://localhost:8080/api/preview', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    effect: effectName,
                    duration: 2,
                    fps: 15,
                    pixelSize: 8
                })
            });
            
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            document.getElementById('preview').src = url;
        }
        
        // Generate preview when page loads
        generatePreview('sparkle');
    </script>
</body>
</html>
```

## Testing

```bash
# Run comprehensive test suite
./test-preview-api.sh localhost 8080

# This will:
# - Test health endpoint
# - Generate sparkle preview
# - Generate rainbow_pulse preview
# - Test error handling
# - Validate parameter checking
```

## Troubleshooting

### "Connection refused"
- Make sure server is running
- Check port: `netstat -an | grep 8080`
- Try alternate port (8888)

### "Effect not found"
- Check effect name spelling (case-insensitive)
- Verify effect is in the switch statement

### Large GIF file
- Increase pixelSize (8 → 16 or 32)
- Decrease duration (2 → 1)
- Decrease fps (15 → 10)

### Memory issues
- Restart server
- Use shorter animations
- Limit concurrent requests

## API Documentation

Full API docs: See `PREVIEW_API.md`
Implementation details: See `IMPLEMENTATION_SUMMARY.md`

## Port Binding

If port 8080 is in use:
```bash
# Check what's using port 8080
lsof -i :8080

# Server will automatically fall back to 8888
# Check logs for confirmation:
# "HTTP server will run on port: 8888"
```

## Performance

Typical generation times:
- Small (2s, 15fps, 12px): ~1 second, 5KB
- Medium (2s, 15fps, 8px): ~2 seconds, 15KB  
- Large (3s, 20fps, 6px): ~5 seconds, 50KB

Files are usually 10-50KB for typical use cases.

## Next Steps

1. ✅ Server running? Check health endpoint
2. ✅ Generate first preview? Try sparkle effect
3. ✅ Works? Integrate into your app
4. ✅ Need tweaking? Adjust parameters
5. ✅ Production? Set up caching and monitoring

Happy previewing! 🎆
