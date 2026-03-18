#!/usr/bin/env python3
import asyncio
import websockets

async def test_rain():
    uri = "ws://fux.local:80"
    
    try:
        async with websockets.connect(uri) as websocket:
            print("Connected to fux WebSocket server")
            
            # Send EFFECT:rain command
            command = "EFFECT:rain"
            await websocket.send(command)
            print(f"Sent: {command}")
            
            # Wait for response
            response = await websocket.recv()
            print(f"Response: {response}")
            
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    asyncio.run(test_rain())
