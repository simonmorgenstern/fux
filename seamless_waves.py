#!/usr/bin/env python3
"""
Seamless looping color waves from center.
Frame 0 looks identical to the last frame for perfect looping.
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

# Calculate distance from center for each LED
max_distance = 0
for led in coords:
    dist = math.sqrt((led['x'] - center_x)**2 + (led['y'] - center_y)**2)
    led['distance'] = dist
    max_distance = max(max_distance, dist)

print(f"Center: ({center_x:.1f}, {center_y:.1f}), Max distance: {max_distance:.1f}")

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
WAVE_SPACING = 90    # Distance between consecutive wave starts
WAVE_WIDTH = 50      # Width of each color band
WAVE_SPEED = 6       # Pixels per frame

# Colors (hues)
COLORS = [0, 60, 120, 180, 240, 300]  # Red, Yellow, Green, Cyan, Blue, Magenta

# Perfect loop: after num_frames, each wave moves exactly WAVE_SPACING
# So the next wave (next color) is in the same position
NUM_FRAMES = int(WAVE_SPACING / WAVE_SPEED)
print(f"Loop length: {NUM_FRAMES} frames (perfect seamless loop)")

def render_frame(time_offset):
    """
    Render a frame at a given time offset.
    Returns changes list for the WebSocket frame.
    """
    changes = []
    changes.append(["c", "{0-267}"])  # Clear all first
    
    # We need to check many waves - enough to fill the entire space
    # Calculate which wave indices might be visible
    max_wave_radius_needed = max_distance + WAVE_WIDTH
    num_waves_to_check = int(max_wave_radius_needed / WAVE_SPACING) + 5
    
    # Check waves from the past (negative indices) to future
    for wave_idx in range(-num_waves_to_check, num_waves_to_check):
        # This wave started at this distance behind the "current" position
        wave_start_offset = wave_idx * WAVE_SPACING
        
        # Current radius of this wave
        wave_radius = time_offset - wave_start_offset
        
        # Skip if not visible
        if wave_radius < -WAVE_WIDTH/2:
            continue
        if wave_radius > max_distance + WAVE_WIDTH/2:
            continue
        
        # Get color (cycles through COLORS)
        color_idx = wave_idx % len(COLORS)
        hue = COLORS[color_idx]
        
        # Light up LEDs for this wave
        for led in coords:
            distance = led['distance']
            distance_from_wave = abs(distance - wave_radius)
            
            # LED is within this wave's band
            if distance_from_wave < WAVE_WIDTH / 2:
                # Intensity based on distance from wave center
                intensity = 1.0 - (distance_from_wave / (WAVE_WIDTH / 2))
                intensity = max(0.1, intensity)  # Minimum brightness
                
                r, g, b = hsv_to_rgb(hue, 1.0, intensity)
                
                if r > 5 or g > 5 or b > 5:
                    color = f"{r},{g},{b}"
                    changes.append([color, str(led['index'])])
    
    return changes

# Generate frames
frames = []
for frame_idx in range(NUM_FRAMES):
    # Time offset increases linearly
    time_offset = frame_idx * WAVE_SPEED
    
    changes = render_frame(time_offset)
    
    frame = {
        "index": frame_idx,
        "duration": 30,
        "changes": changes
    }
    frames.append(frame)

# Verify loop is seamless by checking frame 0 vs what frame NUM_FRAMES would be
print(f"\nVerifying seamless loop:")
time_offset_0 = 0
time_offset_end = NUM_FRAMES * WAVE_SPEED
print(f"  Frame 0 time offset: {time_offset_0}")
print(f"  Frame {NUM_FRAMES} time offset: {time_offset_end}")
print(f"  Difference: {time_offset_end} (should equal WAVE_SPACING={WAVE_SPACING})")

print(f"\nCreated {len(frames)} frames for seamless loop")

# Connect and send
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
    
    response = sock.recv(1024).decode()
    if "101" not in response:
        print(f"WebSocket handshake failed: {response}")
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
    
    # Send all frames
    for frame in frames:
        send_ws_text(sock, json.dumps(frame))
        print(f"Sent frame {frame['index']}/{len(frames)-1}")
    
    # Start playback in loop
    time.sleep(0.5)
    send_ws_text(sock, f"start:{len(frames)}")
    print(f"Started seamless loop of {len(frames)} frames")
    print("Press Ctrl+C to stop")
    
    try:
        loop_count = 0
        while True:
            loop_count += 1
            print(f"Loop {loop_count}")
            time.sleep(len(frames) * 0.03)  # No extra pause
            
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
    sys.exit(1)
