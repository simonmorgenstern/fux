#!/bin/bash
set -e
echo "Testing LED Namer API..."

# Start server in background if not already running
if ! curl -s http://localhost:3000/api/groups > /dev/null; then
    echo "Starting server..."
    node server.js &
    SERVER_PID=$!
    sleep 3
fi

# Clean up on exit
trap "kill $SERVER_PID 2>/dev/null; exit" INT TERM EXIT

# Get empty list
echo "1. GET /api/groups"
curl -s http://localhost:3000/api/groups | jq .

# Create a group
echo "2. POST /api/groups"
curl -s -X POST -H "Content-Type: application/json" -d '{
    "name": "Test Front",
    "ranges": [{ "start": 0, "end": 10 }]
}' http://localhost:3000/api/groups | jq .

# List again
echo "3. GET /api/groups after POST"
curl -s http://localhost:3000/api/groups | jq .

echo "Test completed successfully."
# Server will be killed by trap