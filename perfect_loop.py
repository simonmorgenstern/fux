#!/usr/bin/env python3
"""
Perfect looping color waves - mathematically guaranteed.
"""

import json
import socket
import time
import sys
import math

# Load pixel coordinates
with open('assets/pixelCoordinates.json', 'r') as f:
    coords = json.load(f)

# Find center and distances
x_values = [c['x'] for c in coords]
y_values = [c['y'] for c in coords]
center_x = (min(x_values) + max(x_values)) / 2
center_y = (min(y_values) + max(y_values)) / 2

max_distance = 0
for led in coords:
    dist = math.sqrt((led['x'] - center_x)**2 + (led['y'] - center_y)**2)
    led['distance'] = dist
    max_distance = max(max_distance, dist)

print(f"LEDs: {len(coords)}, Max distance: {max_distance:.1f}")

# HSV to RGB
def hsv_to_rgb(h, s, v):
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
WAVE_SPACING = 100
WAVE_WIDTH = 60
WAVE_SPEED = 5
COLORS = [0, 60, 120, 180, 240, 300]  # 6 colors

# For perfect loop: num_frames = (WAVE_SPACING * len(COLORS)) / WAVE_SPEED
NUM_FRAMES = (WAVE_SPACING * len(COLORS)) // WAVE_SPEED  # = 120

print(f"Generating {NUM_FRAMES} frames for perfect loop")
print(f"  Wave spacing: {WAVE_SPACING}, Speed: {WAVE_SPEED}, Colors: {len(COLORS)}")

def render_frame_at_time(t):
    """Render a frame at a specific time offset"""
    changes = [["c", "{0-267}"]]
    
    # Check many possible waves
    for wave_num in range(-20, 20):
        wave_start_time = wave_num * WAVE_SPACING
        wave_radius = t - wave_start_time
        
        if wave_radius < -WAVE_WIDTH/2:
            continue
        if wave_radius > max_distance + WAVE_WIDTH/2:
            continue
        
        color_idx = wave_num % len(COLORS)
        hue = COLORS[color_idx]
        
        for led in coords:
            dist_from_wave = abs(led['distance'] - wave_radius)
            
            if dist_from_wave < WAVE_WIDTH / 2:
                intensity = 1.0 - (dist_from_wave / (WAVE_WIDTH / 2))
                r, g, b = hsv_to_rgb(hue, 1.0, intensity)
                
                if r > 5 or g > 5 or b > 5:
                    color = f"{r},{g},{b}"
                    changes.append([color, str(led['index'])])
    
    return changes

# Generate frames
# Start at an offset so waves are already visible
START_OFFSET = 400  # Start 400 units into the animation (waves already present)

frames = []
for frame_idx in range(NUM_FRAMES):
    t = START_OFFSET + (frame_idx * WAVE_SPEED)
    changes = render_frame_at_time(t)
    
    frames.append({
        "index": frame_idx,
        "duration": 30,
        "changes": changes
    })

# Verify loop
print("\nVerifying perfect loop...")
t_first = START_OFFSET
t_after_loop = START_OFFSET + (NUM_FRAMES * WAVE_SPEED)

print(f"  First frame time: {t_first}")
print(f"  After loop time: {t_after_loop}")
print(f"  Difference: {t_after_loop - t_first}")
print(f"  Wave spacing * colors: {WAVE_SPACING * len(COLORS)}")

if (t_after_loop - t_first) == (WAVE_SPACING * len(COLORS)):
    print("  ✓ Time difference matches full color cycle!")
    
    # Render hypothetical "frame NUM_FRAMES" and compare with frame 0
    changes_loop = render_frame_at_time(t_after_loop)
    
    def changes_to_dict(changes):
        d = {}
        for c in changes:
            if c[0] == 'c':
                continue
            d[c[1]] = c[0]
        return d
    
    dict_0 = changes_to_dict(frames[0]['changes'])
    dict_loop = changes_to_dict(changes_loop)
    
    if dict_0 == dict_loop:
        print("  ✓✓ Frame 0 and hypothetical frame 120 are IDENTICAL!")
        print("  ✓✓✓ PERFECT SEAMLESS LOOP GUARANTEED!")
    else:
        print(f"  ✗ Frames differ: {len(dict_0)} vs {len(dict_loop)} LEDs")
else:
    print(f"  ✗ Time mismatch!")

# Save
with open('color_waves_animation.json', 'w') as f:
    json.dump(frames, f, indent=2)
print(f"\n✓ Saved {len(frames)} frames to color_waves_animation.json")

# Send to Pi
try:
    host = 'fux.local'
    port = 80
    
    print(f"\nConnecting to {host}:{port}...")
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    sock.connect((host, port))
    
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
    
    response = sock.recv(1024).decode()
    if "101" not in response:
        print(f"WebSocket handshake failed")
        sys.exit(1)
    
    print("Connected!")
    
    def send_ws_text(sock, data):
        msg = data.encode('utf-8')
        length = len(msg)
        frame = bytearray()
        frame.append(0x81)
        
        if length <= 125:
            frame.append(0x80 | length)
        elif length <= 65535:
            frame.append(0x80 | 126)
            frame.extend(length.to_bytes(2, 'big'))
        else:
            frame.append(0x80 | 127)
            frame.extend(length.to_bytes(8, 'big'))
        
        mask_key = b'\x00\x00\x00\x00'
        frame.extend(mask_key)
        frame.extend(msg)
        sock.send(frame)
    
    # Send frames
    for i, frame in enumerate(frames):
        send_ws_text(sock, json.dumps(frame))
        if i % 20 == 0:
            print(f"Sent {i}/{len(frames)}")
    
    time.sleep(0.5)
    send_ws_text(sock, f"start:{len(frames)}")
    print(f"\n✓ Animation started - seamless loop!")
    print("Running... Press Ctrl+C to stop")
    
    try:
        loop_count = 0
        while True:
            loop_count += 1
            print(f"Loop {loop_count}")
            time.sleep(len(frames) * 0.03)
            
            for frame in frames:
                send_ws_text(sock, json.dumps(frame))
            send_ws_text(sock, f"start:{len(frames)}")
    except KeyboardInterrupt:
        print("\nStopped")
    
    sock.close()

except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()
