# Fux LED Effects - Quick Reference

## 🎯 New Effects Created

| Effect Name | LED Group | Description | Command |
|------------|-----------|-------------|---------|
| **Eye Blink** | eyes (41 LEDs) | Color-changing blink effect | `EFFECT:eye_blink` |
| **Diamond Pulse** | diamond (20 LEDs) | Breathing/pulsing effect | `EFFECT:diamond_pulse` |
| **Box Wave** | boxes1-8 (~100 LEDs) | Rainbow wave animation | `EFFECT:box_wave` |
| **Outside Spin** | outside (91 LEDs) | Rotating RGB segments | `EFFECT:outside_spin` |

## ⚡ Quick Commands

### Start an Effect
```bash
echo "EFFECT:eye_blink" | websocat ws://fux.local:80
```

### Stop Current Effect
```bash
echo "STOP_EFFECT" | websocat ws://fux.local:80
```

### Restart Service
```bash
ssh pi@fux.local "sudo pkill java && cd /home/pi/fux && sudo java -jar java-fux-1.0-SNAPSHOT.one-jar.jar &"
```

## 📝 Edit Effect Parameters

Edit JSON files in `/home/pi/fux-effects/`:
- `eye_blink.json` - Change blink speed
- `diamond_pulse.json` - Change color and breathing rate
- `box_wave.json` - Adjust wave speed
- `outside_spin.json` - Modify rotation speed

After editing, restart the service to reload.

## 🔧 Rebuild & Deploy

From workspace:
```bash
cd /home/simon/.openclaw/workspace/fux/java-fux
mvn clean package
scp target/java-fux-1.0-SNAPSHOT.one-jar.jar pi@fux.local:/home/pi/fux/
ssh pi@fux.local "sudo pkill java && cd /home/pi/fux && sudo java -jar java-fux-1.0-SNAPSHOT.one-jar.jar &"
```

## ✅ Verify Deployment
```bash
cd /home/simon/.openclaw/workspace/fux
./verify-deployment.sh
```

---
All systems ready! 🚀
