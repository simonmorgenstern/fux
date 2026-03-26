#!/bin/bash

# Test script for Preview API
# Usage: ./test-preview-api.sh [host] [port]

HOST="${1:-localhost}"
PORT="${2:-8080}"
URL="http://${HOST}:${PORT}"

echo "=== Fux Preview API Test ==="
echo "Testing endpoint: $URL"
echo ""

# Test 1: Health check
echo "Test 1: Health Check"
echo "  GET $URL/health"
curl -s "$URL/health" | jq '.' || echo "Failed"
echo ""

# Test 2: API Documentation
echo "Test 2: API Documentation"
echo "  GET $URL/"
curl -s "$URL/" | head -5
echo ""

# Test 3: Generate sparkle effect preview
echo "Test 3: Generate Sparkle Effect Preview"
echo "  POST $URL/api/preview"
echo "  Body: {\"effect\":\"sparkle\",\"duration\":2,\"fps\":15,\"pixelSize\":8}"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$URL/api/preview" \
  -H "Content-Type: application/json" \
  -d '{"effect":"sparkle","duration":2,"fps":15,"pixelSize":8}' \
  -o sparkle_preview.gif)

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
  SIZE=$(ls -lh sparkle_preview.gif | awk '{print $5}')
  echo "  ✓ Success (HTTP $HTTP_CODE) - Generated GIF: $SIZE"
else
  echo "  ✗ Failed (HTTP $HTTP_CODE)"
fi
echo ""

# Test 4: Generate rainbow_pulse effect preview
echo "Test 4: Generate Rainbow Pulse Effect Preview"
echo "  POST $URL/api/preview"
echo "  Body: {\"effect\":\"rainbow_pulse\",\"duration\":3,\"fps\":20,\"pixelSize\":10}"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$URL/api/preview" \
  -H "Content-Type: application/json" \
  -d '{"effect":"rainbow_pulse","duration":3,"fps":20,"pixelSize":10}' \
  -o rainbow_preview.gif)

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
  SIZE=$(ls -lh rainbow_preview.gif | awk '{print $5}')
  echo "  ✓ Success (HTTP $HTTP_CODE) - Generated GIF: $SIZE"
else
  echo "  ✗ Failed (HTTP $HTTP_CODE)"
fi
echo ""

# Test 5: Test invalid effect
echo "Test 5: Test Invalid Effect (should return 404)"
echo "  POST $URL/api/preview"
echo "  Body: {\"effect\":\"nonexistent\",\"duration\":2,\"fps\":15,\"pixelSize\":8}"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$URL/api/preview" \
  -H "Content-Type: application/json" \
  -d '{"effect":"nonexistent","duration":2,"fps":15,"pixelSize":8}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "404" ]; then
  echo "  ✓ Correctly returned 404"
else
  echo "  ✗ Unexpected response (HTTP $HTTP_CODE)"
fi
echo ""

# Test 6: Test invalid JSON
echo "Test 6: Test Invalid JSON (should return 400)"
echo "  POST $URL/api/preview"
echo "  Body: {invalid json}"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$URL/api/preview" \
  -H "Content-Type: application/json" \
  -d '{invalid json}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "400" ]; then
  echo "  ✓ Correctly returned 400"
else
  echo "  ✗ Unexpected response (HTTP $HTTP_CODE)"
fi
echo ""

# Test 7: Test missing required field
echo "Test 7: Test Missing Required Field (should return 400)"
echo "  POST $URL/api/preview"
echo "  Body: {\"duration\":2,\"fps\":15,\"pixelSize\":8}"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$URL/api/preview" \
  -H "Content-Type: application/json" \
  -d '{"duration":2,"fps":15,"pixelSize":8}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "400" ]; then
  echo "  ✓ Correctly returned 400"
else
  echo "  ✗ Unexpected response (HTTP $HTTP_CODE)"
fi
echo ""

# Test 8: Test parameter bounds
echo "Test 8: Test Parameter Bounds (fps > 60, should return 400)"
echo "  POST $URL/api/preview"
echo "  Body: {\"effect\":\"sparkle\",\"duration\":2,\"fps\":120,\"pixelSize\":8}"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$URL/api/preview" \
  -H "Content-Type: application/json" \
  -d '{"effect":"sparkle","duration":2,"fps":120,"pixelSize":8}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "400" ]; then
  echo "  ✓ Correctly returned 400"
else
  echo "  ✗ Unexpected response (HTTP $HTTP_CODE)"
fi
echo ""

# Summary
echo "=== Test Summary ==="
echo "Generated GIFs:"
[ -f sparkle_preview.gif ] && echo "  ✓ sparkle_preview.gif ($(ls -lh sparkle_preview.gif | awk '{print $5}'))"
[ -f rainbow_preview.gif ] && echo "  ✓ rainbow_preview.gif ($(ls -lh rainbow_preview.gif | awk '{print $5}'))"
echo ""
echo "All tests completed! Check the generated GIFs with your image viewer."
