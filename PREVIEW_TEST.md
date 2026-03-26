# Fux Preview Server - macOS/Dev Testing

The app now runs on macOS in **preview-only mode** — no GPIO hardware needed! ✨

## Running on Mac

```bash
cd ~/[...]/fux
./run-mac.sh [port]
```

Default: WebSocket on 8081, HTTP preview on 8080

## Testing the Preview Endpoint

The `/api/preview` endpoint generates animated GIF previews of effects.

### cURL Example

```bash
curl -X POST http://localhost:8080/api/preview \
  -H "Content-Type: application/json" \
  -d '{
    "effect": "rainbow_pulse",
    "duration": 2.0,
    "fps": 15,
    "pixelSize": 8
  }' \
  --output preview.gif
```

Then open `preview.gif` to see the animation!

### Request Parameters

| Parameter   | Type   | Default | Range      | Notes                          |
|-------------|--------|---------|------------|--------------------------------|
| `effect`    | string | -       | (required) | Effect name from availableEffects |
| `duration`  | number | 2.0     | 0.1–30    | Animation length in seconds    |
| `fps`       | int    | 15      | 1–60      | Frames per second              |
| `pixelSize` | int    | 8       | 1–32      | Pixel size in output GIF       |

### Available Effects

- `rainbow_pulse`
- `radial_wave`
- `sparkle`
- `fire`
- `breathing`
- `snake`
- `meteor_shower`
- `firework`
- `rain`
- `aurora`
- `bilateral_fill`
- `motion_blur`
- `gradient`
- `box_mirror`
- `eye_blink`
- `diamond_pulse`
- `box_wave`
- `outside_spin`

### Python Test Script

```python
#!/usr/bin/env python3
import requests
import json

BASE_URL = "http://localhost:8080"

def test_preview(effect_name):
    """Generate and save a preview GIF"""
    payload = {
        "effect": effect_name,
        "duration": 2.0,
        "fps": 15,
        "pixelSize": 8
    }
    
    response = requests.post(
        f"{BASE_URL}/api/preview",
        json=payload,
        timeout=10
    )
    
    if response.status_code == 200:
        filename = f"preview_{effect_name}.gif"
        with open(filename, "wb") as f:
            f.write(response.content)
        print(f"✅ Saved: {filename}")
    else:
        print(f"❌ Error: {response.status_code} - {response.text}")

if __name__ == "__main__":
    # Test a few effects
    effects = ["rainbow_pulse", "sparkle", "fire"]
    for effect in effects:
        print(f"Testing {effect}...")
        test_preview(effect)
```

## Notes

- The preview server is **independent** of the WebSocket server
- Effects with missing JSON files will fail gracefully (fixed in latest build)
- Run `mvn clean package` in `java-fux/` to rebuild after code changes
