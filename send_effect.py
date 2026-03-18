#!/usr/bin/env python3
import socket, time, sys

effect = sys.argv[1] if len(sys.argv) > 1 else "rainbow_pulse"

try:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    sock.connect(('fux.local', 80))
    
    handshake = "GET / HTTP/1.1\r\nHost: fux.local\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n"
    sock.send(handshake.encode())
    
    response = sock.recv(1024).decode()
    if "101" not in response:
        print(f"Handshake failed")
        exit(1)
    
    def send_ws(data):
        msg = data.encode('utf-8')
        frame = bytearray([0x81])
        l = len(msg)
        if l <= 125:
            frame.append(0x80 | l)
        frame.extend(b'\x00\x00\x00\x00' + msg)
        sock.send(frame)
    
    print(f"Sending: EFFECT:{effect}")
    send_ws(f"EFFECT:{effect}")
    print("✓ Effect started!")
    
    time.sleep(1)
    sock.close()
    
except Exception as e:
    print(f"Error: {e}")
    exit(1)
