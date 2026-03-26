#!/bin/bash
# Fux Preview Server - macOS/Linux Dev Mode

JAR_PATH="./java-fux/target/java-fux-1.0-SNAPSHOT.one-jar.jar"
WS_PORT="${1:-8081}"
HTTP_PORT=8080

# Check if jar exists and is up to date
NEEDS_BUILD=false
if [ ! -f "$JAR_PATH" ]; then
    echo "📦 JAR not found, building..."
    NEEDS_BUILD=true
else
    # Check if source files are newer than jar
    if [ -n "$(find java-fux/src -newer "$JAR_PATH" 2>/dev/null)" ]; then
        echo "🔄 Source files updated, rebuilding..."
        NEEDS_BUILD=true
    fi
fi

if [ "$NEEDS_BUILD" = true ]; then
    echo "Running: mvn clean package -DskipTests"
    cd java-fux && mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "❌ Build failed"
        exit 1
    fi
    cd ..
    echo "✅ Build complete"
    echo ""
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
