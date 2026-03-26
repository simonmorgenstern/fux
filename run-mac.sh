#!/bin/bash
# Fux Preview Server - macOS/Linux Dev Mode

JAR_PATH="./java-fux/target/java-fux-1.0-SNAPSHOT.one-jar.jar"
WS_PORT="${1:-8081}"
HTTP_PORT=8080

if [ ! -f "$JAR_PATH" ]; then
    echo "❌ JAR not found at $JAR_PATH"
    echo "Run: cd java-fux && mvn clean package -DskipTests"
    exit 1
fi

echo "🎨 Fux Preview Server"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📡 WebSocket:  ws://localhost:$WS_PORT"
echo "🌐 HTTP API:   http://localhost:$HTTP_PORT/api/preview"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Starting preview server..."
echo ""

java -jar "$JAR_PATH" --port "$WS_PORT"
