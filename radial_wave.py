#!/usr/bin/env python3
"""
Radial wave animation from center outward with rainbow gradient.
Starts in the middle, expands outward, then jumps back to center.
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

# Create wave frames
frames = []
num_frames = 50  # Fewer frames = faster animation
wave_width = 80  # Width of the wave band
fade_length = 60  # Trailing fade

# Start with radius 0 (truly from the center)
start_radius = 0
print(f"Start radius: {start_radius:.1f} (starting from center point)")

for frame_idx in range(num_frames):
    # Current wave radius (expanding from start_radius to max_distance)
    progress = frame_idx / (num_frames - 1)
    wave_radius = start_radius + progress * (max_distance - start_radius + wave_width)
    
    # Build frame changes
    changes = []
    
    # Clear all LEDs first
    changes.append(["c", "{0-267}"])
    
    for led in coords:
        distance = led['distance']
        
        # LEDs that the wave has already passed or are at the wave front
        if distance <= wave_radius:
            # Determine if this is the wave front or already passed
            distance_from_front = wave_radius - distance
            
            if distance_from_front < 20:
                # Front of wave - bright and colorful
                intensity = 1.0
            else:
                # Already passed - stay at full brightness
                intensity = 0.7  # Slightly dimmer than the wave front
            
            # Color based on distance from center (rainbow gradient)
            hue = (distance / max_distance) * 360  # Full rainbow spectrum
            r, g, b = hsv_to_rgb(hue, 1.0, intensity)
            
            if r > 0 or g > 0 or b > 0:  # Only add if not black
                color = f"{r},{g},{b}"
                changes.append([color, str(led['index'])])
    
    # Create frame JSON
    frame = {
        "index": frame_idx,
        "duration": 25,  # 25ms per frame (faster)
        "changes": changes
    }
    frames.append(frame)

print(f"Created {len(frames)} frames")

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
            
            # Wait for current animation to finish (shorter pause)
            time.sleep(len(frames) * 0.025 + 0.3)  # Just 300ms pause at end
            
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
