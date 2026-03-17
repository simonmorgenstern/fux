#!/bin/bash
cd "$(dirname "$0")"
echo "Starting LED Namer server..."

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
fi

# Start server
node server.js