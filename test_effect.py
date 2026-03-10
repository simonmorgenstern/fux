#!/usr/bin/env python3
"""
Test Effect Engine via WebSocket
"""
import socket
import time

try:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    sock.connect(('fux.local', 80))
    
    # WebSocket handshake
    handshake = (
        "GET / HTTP/1.1\r\n"
        "Host: fux.local\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "\r\n"
    )
    sock.send(handshake.encode())
    
    response = sock.recv(1024).decode()
    if "101" not in response:
        print(f"Handshake failed: {response}")
        exit(1)
    
    print("✓ Connected to WebSocket server")
    
    def send_ws(data):
        msg = data.encode('utf-8')
        frame = bytearray([0x81])
        l = len(msg)
        if l <= 125:
            frame.append(0x80 | l)
        frame.extend(b'\x00\x00\x00\x00' + msg)
        sock.send(frame)
    
    # Send EFFECT command
    print("\nSending: EFFECT:radial_waves")
    send_ws("EFFECT:radial_waves")
    
    print("✓ Command sent!")
    print("\nEffect should now be running on the LEDs.")
    print("Check the Pi for visual confirmation.")
    
    time.sleep(2)
    sock.close()
    
except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()
