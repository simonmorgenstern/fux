#!/bin/bash
# Force-kill any Java processes running fux on the Pi

echo "Force-killing fux server..."
ssh -o StrictHostKeyChecking=no pi@fux.local "sudo pkill -9 -f 'java.*fux'" 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Process killed"
else
    echo "⚠️  No process found (may already be stopped)"
fi

sleep 1
