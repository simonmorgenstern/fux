#!/bin/bash
# Restart the fux LED server (stop + start)

echo "🔄 Restarting fux server..."

# Try graceful stop via WebSocket first
if nc -zv fux.local 80 2>&1 | grep -q succeeded; then
    echo "Sending STOP command..."
    timeout 3 node fux-stop.js 2>/dev/null
    sleep 2
fi

# Force kill any remaining processes
bash fux-kill.sh

# Start the server
bash fux-start.sh
