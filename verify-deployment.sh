#!/bin/bash
echo "🔍 Verifying Fux LED Effects Deployment"
echo "========================================"
echo ""

echo "📁 Local Files:"
for file in EyeBlinkEffect.java DiamondPulseEffect.java BoxWaveEffect.java OutsideSpinEffect.java; do
    if [ -f "java-fux/src/main/java/$file" ]; then
        echo "  ✅ $file"
    else
        echo "  ❌ $file MISSING"
    fi
done
echo ""

echo "📦 Compiled JAR:"
if [ -f "java-fux/target/java-fux-1.0-SNAPSHOT.one-jar.jar" ]; then
    echo "  ✅ JAR exists ($(ls -lh java-fux/target/java-fux-1.0-SNAPSHOT.one-jar.jar | awk '{print $5}'))"
else
    echo "  ❌ JAR MISSING"
fi
echo ""

echo "🌐 Remote Files (fux.local):"
ssh pi@fux.local "
    echo '  JAR:' && ls -lh /home/pi/fux/java-fux-1.0-SNAPSHOT.one-jar.jar 2>&1 | grep -q 'No such' && echo '    ❌ MISSING' || echo '    ✅ Present'
    echo '  LED Groups:' && ls -lh /home/pi/led-namer/led-groups.json 2>&1 | grep -q 'No such' && echo '    ❌ MISSING' || echo '    ✅ Present'
    echo '  Effect JSONs:'
    for effect in eye_blink diamond_pulse box_wave outside_spin; do
        if [ -f /home/pi/fux-effects/\$effect.json ]; then
            echo \"    ✅ \$effect.json\"
        else
            echo \"    ❌ \$effect.json MISSING\"
        fi
    done
"
echo ""

echo "🚀 Service Status:"
ssh pi@fux.local "pgrep -af 'java.*fux' | head -1" && echo "  ✅ Running" || echo "  ❌ Not running"
echo ""

echo "✨ Deployment verification complete!"
