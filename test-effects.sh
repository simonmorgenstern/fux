#!/bin/bash
# Test the new LED group effects on Fux

PI_HOST="fux.local"

echo "Testing new LED group effects on Fux LED controller..."
echo ""

# Function to send effect command via WebSocket
send_effect() {
    local effect_name=$1
    local duration=$2
    echo "Testing $effect_name for ${duration}s..."
    echo -e "EFFECT:$effect_name" | websocat ws://${PI_HOST}:80 &
    sleep $duration
}

# Test each new effect
echo "1. Testing EyeBlinkEffect - Eyes blinking with different colors"
send_effect "eye_blink" 10

echo ""
echo "2. Testing DiamondPulseEffect - Diamond breathing effect"
send_effect "diamond_pulse" 10

echo ""
echo "3. Testing BoxWaveEffect - Wave through boxes 1-8"
send_effect "box_wave" 15

echo ""
echo "4. Testing OutsideSpinEffect - Spinning colors on outside ring"
send_effect "outside_spin" 10

echo ""
echo "Stopping effects..."
echo -e "STOP_EFFECT" | websocat ws://${PI_HOST}:80

echo ""
echo "All effects tested! ✨"
