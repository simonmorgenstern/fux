#!/usr/bin/env python3
"""Simple script to trigger Fux LED effects via WebSocket"""
import asyncio
import websockets
import sys

async def send_effect(effect_name):
    uri = "ws://fux.local:80"
    try:
        async with websockets.connect(uri) as websocket:
            # Wait for welcome message
            welcome = await websocket.recv()
            print(f"Connected: {welcome}")
            
            # Send effect command
            command = f"EFFECT:{effect_name}"
            await websocket.send(command)
            print(f"Sent: {command}")
            
            # Wait a bit for confirmation
            await asyncio.sleep(1)
            
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: ./trigger-effect.py <effect_name>")
        print("Example: ./trigger-effect.py eye_blink")
        sys.exit(1)
    
    effect = sys.argv[1]
    asyncio.run(send_effect(effect))
