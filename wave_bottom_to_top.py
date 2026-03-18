#!/usr/bin/env python3
"""
Spatial wave animation from bottom to top for the fux LED fox.
Connects to WebSocket server on fux.local and sends wave frames.
"""

import json
import time
import websocket
import sys

# Load pixel coordinates
with open('assets/pixelCoordinates.json', 'r') as f:
    coords = json.load(f)

print(f"Loaded {len(coords)} LED coordinates")

# Find Y range
y_values = [c['y'] for c in coords]
y_min, y_max = min(y_values), max(y_values)
print(f"Y range: {y_min} (top) to {y_max} (bottom)")

# Create wave frames
frames = []
wave_height = 50  # Height of the wave band
num_frames = 60   # Total frames for the animation
wave_color = "0,150,255"  # Blue wave

for frame_idx in range(num_frames):
    # Calculate current wave position (from bottom to top)
    progress = frame_idx / (num_frames - 1)
    wave_y = y_max - progress * (y_max - y_min)  # Start at 600, move to 0
    
    # Build frame changes
    changes = []
    
    for led in coords:
        led_y = led['y']
        distance = abs(led_y - wave_y)
        
        # LEDs within wave_height of the wave front light up
        if distance < wave_height:
            # Brightness based on distance from wave center
            intensity = 1.0 - (distance / wave_height)
            r = int(0 * intensity)
            g = int(150 * intensity)
            b = int(255 * intensity)
            color = f"{r},{g},{b}"
            changes.append([color, str(led['index'])])
    
    # Create frame JSON
    frame = {
        "index": frame_idx,
        "duration": 50,  # 50ms per frame
        "changes": changes
    }
    frames.append(frame)

print(f"Created {len(frames)} frames")

# Connect to WebSocket server
try:
    ws_url = "ws://fux.local"
    print(f"Connecting to {ws_url}...")
    ws = websocket.create_connection(ws_url, timeout=5)
    print("Connected!")
    
    # Send all frames
    for frame in frames:
        ws.send(json.dumps(frame))
        print(f"Sent frame {frame['index']}/{len(frames)-1}")
    
    # Start playback
    time.sleep(0.5)
    ws.send(f"start:{len(frames)}")
    print(f"Started playback of {len(frames)} frames")
    
    # Keep connection open during playback
    time.sleep(len(frames) * 0.05 + 1)
    
    ws.close()
    print("Animation complete!")

except Exception as e:
    print(f"Error: {e}")
    print("\nMake sure the Java WebSocket server is running on the Pi:")
    print("  ssh pi@fux.local")
    print("  cd java-fux")
    print("  sudo java -jar target/java-fux-1.0-SNAPSHOT.one-jar.jar")
    sys.exit(1)
