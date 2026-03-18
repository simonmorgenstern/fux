#!/usr/bin/env python3
"""
Smooth looping waves - generate enough frames that the seam is invisible.
"""

import json
import socket
import time
import sys
import math

with open('assets/pixelCoordinates.json', 'r') as f:
    coords = json.load(f)

x_vals = [c['x'] for c in coords]
y_vals = [c['y'] for c in coords]
cx = (min(x_vals) + max(x_vals)) / 2
cy = (min(y_vals) + max(y_vals)) / 2

max_dist = 0
for led in coords:
    d = math.sqrt((led['x'] - cx)**2 + (led['y'] - cy)**2)
    led['distance'] = d
    max_dist = max(max_dist, d)

def hsv_to_rgb(h, s, v):
    c = v * s
    x = c * (1 - abs((h / 60) % 2 - 1))
    m = v - c
    if h < 60: r, g, b = c, x, 0
    elif h < 120: r, g, b = x, c, 0
    elif h < 180: r, g, b = 0, c, x
    elif h < 240: r, g, b = 0, x, c
    elif h < 300: r, g, b = x, 0, c
    else: r, g, b = c, 0, x
    return int((r + m) * 255), int((g + m) * 255), int((b + m) * 255)

# Simple approach: Use time-based cyclic function
COLORS = [0, 60, 120, 180, 240, 300]
WAVE_SPACING = 100
WAVE_WIDTH = 60
FPS = 33  # ~30ms per frame
LOOP_DURATION_SECONDS = 6  # 6 second loop

NUM_FRAMES = FPS * LOOP_DURATION_SECONDS  # 198 frames

print(f"Generating {NUM_FRAMES} frames ({LOOP_DURATION_SECONDS}s loop)")

frames = []

for frame_idx in range(NUM_FRAMES):
    # Time in animation (0 to 1 = one complete cycle)
    t = frame_idx / NUM_FRAMES  # 0.0 to 0.999...
    
    changes = [["c", "{0-267}"]]
    
    # Generate waves based on LED distance and time
    for led in coords:
        dist = led['distance']
        
        # Which wave is this LED part of?
        # As time progresses, waves move outward
        wave_phase = (dist / WAVE_SPACING) - (t * 6)  # 6 full color cycles
        wave_phase = wave_phase % 1.0  # Wrap to 0-1
        
        # Which color?
        color_idx = int((dist / WAVE_SPACING) * len(COLORS)) % len(COLORS)
        hue = COLORS[color_idx]
        
        # Is this LED within a wave band?
        if wave_phase < (WAVE_WIDTH / WAVE_SPACING):
            intensity = 1.0 - (wave_phase / (WAVE_WIDTH / WAVE_SPACING))
            r, g, b = hsv_to_rgb(hue, 1.0, intensity)
            
            if r > 5 or g > 5 or b > 5:
                changes.append([f"{r},{g},{b}", str(led['index'])])
    
    frames.append({"index": frame_idx, "duration": 30, "changes": changes})

print(f"Created {len(frames)} frames")

# Save
with open('color_waves_animation.json', 'w') as f:
    json.dump(frames, f, indent=2)
print("✓ Saved")

# Send to Pi  
try:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    sock.connect(('fux.local', 80))
    
    handshake = "GET / HTTP/1.1\r\nHost: fux.local\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n"
    sock.send(handshake.encode())
    sock.recv(1024)
    
    def send_ws(data):
        msg = data.encode('utf-8')
        frame = bytearray([0x81])
        l = len(msg)
        if l <= 125:
            frame.append(0x80 | l)
        elif l <= 65535:
            frame.extend([0x80 | 126] + list(l.to_bytes(2, 'big')))
        frame.extend(b'\x00\x00\x00\x00' + msg)
        sock.send(frame)
    
    for i, f in enumerate(frames):
        send_ws(json.dumps(f))
        if i % 30 == 0:
            print(f"{i}/{len(frames)}")
    
    time.sleep(0.5)
    send_ws(f"start:{len(frames)}")
    print("✓ Playing!")
    
    try:
        while True:
            time.sleep(len(frames) * 0.03)
            for f in frames:
                send_ws(json.dumps(f))
            send_ws(f"start:{len(frames)}")
            print("Loop")
    except KeyboardInterrupt:
        pass
    
    sock.close()
except Exception as e:
    print(f"Error: {e}")
