#!/bin/bash
# Start the fux LED server on the Raspberry Pi

echo "Starting fux server..."
ssh -o StrictHostKeyChecking=no pi@fux.local "cd /home/pi/fux && sudo bash -c 'nohup java -jar java-fux-1.0-SNAPSHOT.one-jar.jar > /home/pi/fux.log 2>&1 &' && sleep 1"

# Wait for server to start
sleep 4

# Check if it's running
if nc -zv fux.local 80 2>&1 | grep -q succeeded; then
    echo "✅ Server started successfully on fux.local:80"
else
    echo "❌ Server failed to start - check /home/pi/fux.log on the Pi"
    exit 1
fi
