#!/bin/bash
# deploy.sh — Build the fux effect engine and deploy to pi@fux.local
# Run this from your Mac terminal: bash ~/Projects/fux/deploy.sh

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/java-fux" && pwd)"
EFFECTS_DIR="$(cd "$(dirname "$0")/effects" && pwd)"
LED_NAMER_DIR="$(cd "$(dirname "$0")/led-namer" && pwd)"
JAR="$PROJECT_ROOT/target/java-fux-1.0-SNAPSHOT.one-jar.jar"
PI="pi@fux.local"
PI_FUX_DIR="/home/pi/fux"
PI_EFFECTS_DIR="/home/pi/fux-effects"
PI_LED_NAMER_DIR="/home/pi/fux/led-namer"
SSH_OPTS="-o StrictHostKeyChecking=no"

# --- Helpers ---
ok()   { echo "✅ $1"; }
fail() { echo "❌ $1"; exit 1; }
info() { echo "→  $1"; }

# --- Prerequisites ---
info "Checking prerequisites..."
command -v mvn  >/dev/null 2>&1 || fail "mvn not found. Install Maven first (brew install maven)"
command -v sshpass >/dev/null 2>&1 || fail "sshpass not found. Install with: brew install hudochenkov/sshpass/sshpass"

export SSHPASS=fux

# --- Build ---
info "Building with Maven..."
cd "$PROJECT_ROOT"
BUILD_START=$SECONDS
mvn package -q || {
  echo ""
  echo "Build failed. Last 40 lines of output:"
  mvn package 2>&1 | tail -40
  fail "Maven build failed"
}
BUILD_TIME=$(( SECONDS - BUILD_START ))
ok "Build succeeded in ${BUILD_TIME}s"

# --- Check JAR ---
[ -f "$JAR" ] || fail "JAR not found at $JAR"

# --- Check Pi ---
info "Checking Pi reachability..."
ping -c 1 -W 3 fux.local >/dev/null 2>&1 || fail "Cannot reach fux.local — is the Pi powered on and on the same network?"
ok "Pi is reachable"

# --- Ensure remote dirs ---
sshpass -e ssh $SSH_OPTS "$PI" "mkdir -p $PI_FUX_DIR $PI_EFFECTS_DIR $PI_LED_NAMER_DIR"

# --- Copy JAR ---
info "Copying JAR to Pi..."
sshpass -e scp $SSH_OPTS "$JAR" "$PI:$PI_FUX_DIR/"
ok "JAR copied to $PI:$PI_FUX_DIR/"

# --- Sync effects ---
info "Syncing effect JSONs..."
sshpass -e scp $SSH_OPTS "$EFFECTS_DIR"/*.json "$PI:$PI_EFFECTS_DIR/"
ok "Effects synced to $PI:$PI_EFFECTS_DIR/"

# --- Sync led-namer topology assets (connections + boxes) ---
info "Syncing led-namer topology files..."
sshpass -e scp $SSH_OPTS \
  "$LED_NAMER_DIR/led-connections.json" \
  "$LED_NAMER_DIR/led-boxes.json" \
  "$LED_NAMER_DIR/led-groups.json" \
  "$LED_NAMER_DIR/led-mirrors.json" \
  "$PI:$PI_LED_NAMER_DIR/"
ok "Topology synced to $PI:$PI_LED_NAMER_DIR/"

# --- Stop old server ---
info "Stopping old server..."
sshpass -e ssh $SSH_OPTS "$PI" "sudo pkill -9 -f 'java.*fux' 2>/dev/null; true"
sleep 1
ok "Old server stopped"

# --- Start new server ---
info "Starting new server..."
sshpass -e ssh $SSH_OPTS "$PI" \
  "cd $PI_FUX_DIR && sudo bash -c 'nohup java -jar java-fux-1.0-SNAPSHOT.one-jar.jar > /home/pi/fux.log 2>&1 &'"
sleep 4

# --- Verify ---
info "Verifying server is up..."
nc -zv fux.local 80 2>&1 | grep -q succeeded \
  && ok "Server is live on fux.local:80" \
  || {
    echo ""
    echo "Server didn't respond on port 80. Last 30 lines of log:"
    sshpass -e ssh $SSH_OPTS "$PI" "tail -30 /home/pi/fux.log"
    fail "Server failed to start"
  }

echo ""
echo "🎉 Deploy complete!"
