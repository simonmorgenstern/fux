#!/bin/bash

# Test script for Queue HTTP API
# Usage: ./test-queue-api.sh [port]

PORT=${1:-8080}
BASE_URL="http://localhost:$PORT"

echo "================================"
echo "Queue API Test"
echo "================================"
echo "Testing on port $PORT"
echo ""

# Test 1: Get initial queue state
echo "1. GET /api/queue - Get initial queue state"
curl -s "$BASE_URL/api/queue" | jq '.'
echo ""

# Test 2: Add effect to queue (using underscore naming)
echo "2. POST /api/queue - Add 'meteor_shower' to queue"
curl -s -X POST "$BASE_URL/api/queue" \
  -H "Content-Type: application/json" \
  -d '{"effect": "meteor_shower"}' | jq '.'
echo ""

# Test 3: Add effect with duration
echo "3. POST /api/queue - Add 'rain' with duration=10"
curl -s -X POST "$BASE_URL/api/queue" \
  -H "Content-Type: application/json" \
  -d '{"effect": "rain", "duration": 10}' | jq '.'
echo ""

# Test 4: Add effect with duration and repeat
echo "4. POST /api/queue - Add 'rainbow_pulse' with duration=5, repeat=3"
curl -s -X POST "$BASE_URL/api/queue" \
  -H "Content-Type: application/json" \
  -d '{"effect": "rainbow_pulse", "duration": 5, "repeat": 3}' | jq '.'
echo ""

# Test 5: Get queue state with items
echo "5. GET /api/queue - Check queue with items"
curl -s "$BASE_URL/api/queue" | jq '.'
echo ""

# Test 6: Try invalid effect name (should get "Unknown effect" error)
echo "6. POST /api/queue - Try invalid effect 'invalid_effect' (should fail)"
curl -s -X POST "$BASE_URL/api/queue" \
  -H "Content-Type: application/json" \
  -d '{"effect": "invalid_effect"}' | jq '.'
echo ""

# Test 7: Clear queue
echo "7. DELETE /api/queue - Clear queue"
curl -s -X DELETE "$BASE_URL/api/queue" | jq '.'
echo ""

# Test 8: Verify queue is empty
echo "8. GET /api/queue - Verify queue is empty"
curl -s "$BASE_URL/api/queue" | jq '.'
echo ""

# Test 9: Verify effect names match between /api/effects and /api/queue
echo "9. Cross-check: Get effect list and try adding each one"
echo "   Getting first 3 effects from /api/effects..."
EFFECTS=$(curl -s "$BASE_URL/api/effects" | jq -r '.effects[0:3][].id')

for effect in $EFFECTS; do
    echo "   - Testing effect: $effect"
    RESULT=$(curl -s -X POST "$BASE_URL/api/queue" \
      -H "Content-Type: application/json" \
      -d "{\"effect\": \"$effect\"}" | jq -r '.success')
    
    if [ "$RESULT" == "true" ]; then
        echo "     ✓ Success"
    else
        echo "     ✗ Failed - this is the naming mismatch bug!"
    fi
done
echo ""

# Clean up
echo "Cleaning up queue..."
curl -s -X DELETE "$BASE_URL/api/queue" > /dev/null
echo ""

echo "================================"
echo "Test Complete"
echo "================================"
