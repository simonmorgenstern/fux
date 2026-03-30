#!/bin/bash

# Test script for the new effects API endpoints
# This can be run after starting the PreviewHttpServer

SERVER_PORT=${1:-8080}
BASE_URL="http://localhost:${SERVER_PORT}"

echo "Testing Fux Effects API on ${BASE_URL}"
echo "========================================"
echo ""

# Test 1: List all effects
echo "1. Testing GET /api/effects (list all effects)"
echo "---"
curl -s "${BASE_URL}/api/effects" | python3 -m json.tool | head -40
echo ""
echo ""

# Test 2: Get specific effect details
echo "2. Testing GET /api/effects/rain (specific effect)"
echo "---"
curl -s "${BASE_URL}/api/effects/rain" | python3 -m json.tool
echo ""
echo ""

# Test 3: Get another effect
echo "3. Testing GET /api/effects/fire (another effect)"
echo "---"
curl -s "${BASE_URL}/api/effects/fire" | python3 -m json.tool
echo ""
echo ""

# Test 4: Test non-existent effect (should return 404)
echo "4. Testing GET /api/effects/nonexistent (should return 404)"
echo "---"
curl -s -w "\nHTTP Status: %{http_code}\n" "${BASE_URL}/api/effects/nonexistent" | python3 -m json.tool
echo ""
echo ""

# Test 5: Health check
echo "5. Testing GET /health"
echo "---"
curl -s "${BASE_URL}/health" | python3 -m json.tool
echo ""

echo "========================================"
echo "Tests complete!"
