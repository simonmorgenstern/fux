#!/bin/bash
# Set an effect to play in idle mode (continuous playback until replaced)

EFFECT="${1:-diamond_pulse}"
HOST="${2:-fux.local}"
PORT="${3:-8080}"

echo "Setting idle effect: $EFFECT on $HOST:$PORT"

curl -X POST "http://$HOST:$PORT/api/idle" \
  -H "Content-Type: application/json" \
  -d "{\"effect\": \"$EFFECT\"}"

echo ""
echo ""
echo "Effect '$EFFECT' is now playing continuously in IDLE mode."
echo "It will keep playing until you set another effect."
echo ""
echo "Available effects:"
echo "  - diamond_pulse"
echo "  - aurora"
echo "  - rain"
echo "  - fire"
echo "  - breathing"
echo "  - rainbow_pulse"
echo "  - meteor_shower"
echo "  - sparkle"
echo "  - snake"
echo "  - radial_wave"
echo ""
echo "To change effect: ./set_idle_effect.sh <effect_name>"
echo "To stop: curl -X POST http://$HOST:$PORT/api/queue (switches to queue mode)"
