#!/usr/bin/env python3
"""
Spatial wave animation from bottom to top using raw socket (no external deps).
"""

import json
import socket
import time
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
wave_height = 80  # Height of the wave band (front + fade trail)
fade_length = 60  # Length of the trailing fade
num_frames = 60   # Total frames

for frame_idx in range(num_frames):
    # Calculate current wave position (from bottom to top)
    progress = frame_idx / (num_frames - 1)
    wave_y = y_max - progress * (y_max - y_min)  # Start at 600, move to 0
    
    # Build frame changes
    changes = []
    
    # First, clear all LEDs
    changes.append(["c", "{0-267}"])
    
    for led in coords:
        led_y = led['y']
        distance_from_front = wave_y - led_y  # Positive if LED is behind wave front
        
        # Wave front (bright blue)
        if -10 <= distance_from_front <= 20:
            # Brightest part at the front
            r, g, b = 0, 150, 255
            color = f"{r},{g},{b}"
            changes.append([color, str(led['index'])])
        
        # Trailing fade (behind the wave)
        elif 20 < distance_from_front <= fade_length:
            # Fade from bright to dark
            fade_progress = (distance_from_front - 20) / (fade_length - 20)
            intensity = 1.0 - fade_progress
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
            
            # Wait for current animation to finish
            time.sleep(len(frames) * 0.05)
            
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
