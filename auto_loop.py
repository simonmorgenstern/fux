#!/usr/bin/env python3
"""
Auto-detect perfect loop by comparing frame states.
Generate many frames, find where it loops back to frame 0.
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

def create_frame_fingerprint(changes):
    """
    Create a dictionary of LED states.
    Returns dict of {led_index: (r, g, b)} for comparison.
    """
    led_states = {}
    for change in changes:
        if change[0] == "c":  # Clear command
            continue
        color, led = change
        # Parse RGB values
        r, g, b = map(int, color.split(','))
        led_states[led] = (r, g, b)
    return led_states

def frames_match(fp1, fp2, tolerance=10):
    """
    Check if two frame fingerprints are similar enough.
    Returns True if they match within tolerance.
    """
    # Must have same LEDs lit
    if set(fp1.keys()) != set(fp2.keys()):
        return False
    
    # Check each LED's color
    for led in fp1:
        r1, g1, b1 = fp1[led]
        r2, g2, b2 = fp2[led]
        
        # Colors must be within tolerance
        if abs(r1 - r2) > tolerance or abs(g1 - g2) > tolerance or abs(b1 - b2) > tolerance:
            return False
    
    return True

print("Generating frames to find loop point...")

frames = []
fingerprints = []
max_waves = 15
max_frames_to_check = 500  # Generate up to 500 frames

for frame_idx in range(max_frames_to_check):
    changes = []
    
    # Clear all LEDs first
    changes.append(["c", "{0-267}"])
    
    # Determine which waves are currently visible
    for wave_num in range(max_waves):
        wave_offset = wave_num * wave_spacing
        wave_radius = (frame_idx * wave_speed) - wave_offset
        
        if wave_radius < 0:
            continue
        
        if wave_radius > max_distance + wave_width:
            continue
        
        color_idx = wave_num % len(wave_colors)
        hue = wave_colors[color_idx]
        
        for led in coords:
            distance = led['distance']
            distance_from_wave = abs(distance - wave_radius)
            
            if distance_from_wave < wave_width / 2:
                intensity = 1.0 - (distance_from_wave / (wave_width / 2))
                r, g, b = hsv_to_rgb(hue, 1.0, intensity)
                
                if r > 5 or g > 5 or b > 5:
                    color = f"{r},{g},{b}"
                    changes.append([color, str(led['index'])])
    
    # Create frame
    frame = {
        "index": frame_idx,
        "duration": 30,
        "changes": changes
    }
    frames.append(frame)
    
    # Create fingerprint
    fp = create_frame_fingerprint(changes)
    fingerprints.append(fp)
    
    # Check if this matches frame 0 (but not for frame 0 itself)
    # Only start checking after a reasonable number of frames
    if frame_idx > 10 and frames_match(fp, fingerprints[0], tolerance=5):
        print(f"✓ Loop detected at frame {frame_idx}!")
        print(f"  Frame {frame_idx} matches Frame 0 (within tolerance)")
        print(f"  Perfect loop length: {frame_idx} frames")
        # Cut off all frames after this
        frames = frames[:frame_idx]
        break
else:
    print(f"⚠ No exact loop found in {max_frames_to_check} frames")
    print(f"  Using all {len(frames)} frames (may have visible seam)")

print(f"\nFinal animation: {len(frames)} frames")
print(f"Duration: ~{len(frames) * 0.03:.1f} seconds per loop")

# Connect and send
try:
    host = 'fux.local'
    port = 80
    
    print(f"\nConnecting to {host}:{port}...")
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
        if frame['index'] % 10 == 0:
            print(f"Sent frame {frame['index']}/{len(frames)-1}")
    
    # Start playback in loop
    time.sleep(0.5)
    send_ws_text(sock, f"start:{len(frames)}")
    print(f"\nStarted seamless loop!")
    print("Press Ctrl+C to stop")
    
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
    sys.exit(1)
