#!/usr/bin/env python3
"""
Continuous color waves emanating from center.
New colors appear in the middle and push outward in a loop.
"""

import json
import socket
import time
import sys
import math

# Load pixel coordinates
with open('assets/pixelCoordinates.json', 'r') as f:
    coords = json.load(f)

print(f"Loaded {len(coords)} LED coordinates")

# Find center point
x_values = [c['x'] for c in coords]
y_values = [c['y'] for c in coords]
center_x = (min(x_values) + max(x_values)) / 2
center_y = (min(y_values) + max(y_values)) / 2
print(f"Center: ({center_x:.1f}, {center_y:.1f})")

# Calculate distance from center for each LED
max_distance = 0
for led in coords:
    dist = math.sqrt((led['x'] - center_x)**2 + (led['y'] - center_y)**2)
    led['distance'] = dist
    max_distance = max(max_distance, dist)

print(f"Max distance from center: {max_distance:.1f}")

# HSV to RGB conversion
def hsv_to_rgb(h, s, v):
    """h: 0-360, s: 0-1, v: 0-1"""
    c = v * s
    x = c * (1 - abs((h / 60) % 2 - 1))
    m = v - c
    
    if h < 60:
        r, g, b = c, x, 0
    elif h < 120:
        r, g, b = x, c, 0
    elif h < 180:
        r, g, b = 0, c, x
    elif h < 240:
        r, g, b = 0, x, c
    elif h < 300:
        r, g, b = x, 0, c
    else:
        r, g, b = c, 0, x
    
    return int((r + m) * 255), int((g + m) * 255), int((b + m) * 255)

# Wave parameters
wave_spacing = 100  # Distance between wave starts
wave_width = 60     # Width of each color band
wave_speed = 5      # Pixels per frame

# Colors for waves (cycling through rainbow)
wave_colors = [
    0,    # Red
    60,   # Yellow
    120,  # Green
    180,  # Cyan
    240,  # Blue
    300,  # Magenta
]

# Calculate perfect loop: same color at same position
# After wave_spacing/wave_speed frames, a wave moves one spacing
# After (wave_spacing * num_colors) / wave_speed frames, the SAME color is back
frames_per_wave = int(wave_spacing / wave_speed)
num_colors = len(wave_colors)
num_frames = frames_per_wave * num_colors  # Perfect color cycle loop

print(f"Perfect loop: {num_frames} frames")
print(f"  ({frames_per_wave} frames/wave × {num_colors} colors)")
print(f"  Duration: ~{num_frames * 0.03:.1f} seconds per loop")

frames = []
max_waves = 20  # How many concurrent waves to track

# Calculate offset to start in "stable state" (waves already filling the fox)
# Use a multiple of wave_spacing to ensure color alignment
cycles_offset = int(max_distance / wave_spacing) + 2
time_offset = cycles_offset * wave_spacing
print(f"Starting at time offset: {time_offset} ({cycles_offset} wave cycles)")

for frame_idx in range(num_frames):
    changes = []
    
    # Clear all LEDs first
    changes.append(["c", "{0-267}"])
    
    # Animation time with offset applied
    animation_time = (frame_idx * wave_speed) + time_offset
    
    # Check all possible waves
    for wave_num in range(-max_waves, max_waves):
        # Each wave's starting offset
        wave_offset = wave_num * wave_spacing
        # Current radius of this wave
        wave_radius = animation_time - wave_offset
        
        # Skip if wave hasn't started yet
        if wave_radius < 0:
            continue
        
        # Skip if wave has completely passed
        if wave_radius > max_distance + wave_width:
            continue
        
        # Get color for this wave
        color_idx = wave_num % len(wave_colors)
        hue = wave_colors[color_idx]
        
        # Light up LEDs for this wave
        for led in coords:
            distance = led['distance']
            distance_from_wave = abs(distance - wave_radius)
            
            # LED is within this wave's band
            if distance_from_wave < wave_width / 2:
                # Calculate intensity based on distance from wave center
                intensity = 1.0 - (distance_from_wave / (wave_width / 2))
                r, g, b = hsv_to_rgb(hue, 1.0, intensity)
                
                if r > 5 or g > 5 or b > 5:  # Only add if visible
                    color = f"{r},{g},{b}"
                    changes.append([color, str(led['index'])])
    
    # Create frame JSON
    frame = {
        "index": frame_idx,
        "duration": 30,  # 30ms per frame
        "changes": changes
    }
    frames.append(frame)

print(f"Created {len(frames)} frames (seamless endless loop)")

# Save frames to JSON file
output_file = 'color_waves_animation.json'
with open(output_file, 'w') as f:
    json.dump(frames, f, indent=2)
print(f"✓ Saved animation to {output_file}")

# Connect via raw socket with WebSocket handshake
try:
    host = 'fux.local'
    port = 80
    
    print(f"Connecting to {host}:{port}...")
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    sock.connect((host, port))
    
    # WebSocket handshake
    handshake = (
        f"GET / HTTP/1.1\r\n"
        f"Host: {host}\r\n"
        f"Upgrade: websocket\r\n"
        f"Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
        f"Sec-WebSocket-Version: 13\r\n"
        f"\r\n"
    )
    sock.send(handshake.encode())
    
    # Read handshake response
    response = sock.recv(1024).decode()
    if "101" not in response:
        print(f"WebSocket handshake failed: {response}")
        sys.exit(1)
    
    print("Connected!")
    
    # Helper to send WebSocket text frame
    def send_ws_text(sock, data):
        msg = data.encode('utf-8')
        length = len(msg)
        
        # Build frame header
        frame = bytearray()
        frame.append(0x81)  # FIN + text frame
        
        if length <= 125:
            frame.append(0x80 | length)  # Mask bit + payload length
        elif length <= 65535:
            frame.append(0x80 | 126)
            frame.extend(length.to_bytes(2, 'big'))
        else:
            frame.append(0x80 | 127)
            frame.extend(length.to_bytes(8, 'big'))
        
        # Masking key (all zeros for simplicity)
        mask_key = b'\x00\x00\x00\x00'
        frame.extend(mask_key)
        
        # Masked payload
        frame.extend(msg)
        
        sock.send(frame)
    
    # Send all frames
    for frame in frames:
        send_ws_text(sock, json.dumps(frame))
        print(f"Sent frame {frame['index']}/{len(frames)-1}")
    
    # Start playback in loop
    time.sleep(0.5)
    send_ws_text(sock, f"start:{len(frames)}")
    print(f"Started playback of {len(frames)} frames")
    print("Running in loop... Press Ctrl+C to stop")
    
    try:
        loop_count = 0
        while True:
            loop_count += 1
            print(f"Loop {loop_count}")
            
            # Wait for current animation to finish (no pause - seamless loop)
            time.sleep(len(frames) * 0.03)
            
            # Send frames again
            for frame in frames:
                send_ws_text(sock, json.dumps(frame))
            
            # Start playback
            send_ws_text(sock, f"start:{len(frames)}")
    except KeyboardInterrupt:
        print("\nStopping loop...")
    
    sock.close()
    print("Animation complete!")

except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)
