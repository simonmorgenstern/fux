import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class SymmetricSparkleEffect implements Effect {

    private static class Sparkle {
        final int ledA;
        final int ledB;       // same as ledA for self-mirror LEDs
        final float hue;
        final double birthTime;
        final double duration;

        Sparkle(int ledA, int ledB, float hue, double birthTime, double duration) {
            this.ledA = ledA;
            this.ledB = ledB;
            this.hue = hue;
            this.birthTime = birthTime;
            this.duration = duration;
        }
    }

    private PixelCoordinates coords;
    private LEDMirrorMap mirrorMap;
    private Random random;
    private int fps;
    private double spawnRate;
    private double sparkleDurationSec;
    private int maxActive;
    private double brightness;
    private float saturation;

    private List<Sparkle> activeSparkles;
    private double spawnAccumulator;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.activeSparkles = new ArrayList<>();
        this.spawnAccumulator = 0.0;

        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.spawnRate = params.has("spawn_rate") ? params.get("spawn_rate").getAsDouble() : 8.0;
        this.sparkleDurationSec = params.has("sparkle_duration_sec") ? params.get("sparkle_duration_sec").getAsDouble() : 0.6;
        this.maxActive = params.has("max_active") ? params.get("max_active").getAsInt() : 40;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;
        this.saturation = params.has("saturation") ? params.get("saturation").getAsFloat() : 0.8f;

        this.mirrorMap = new LEDMirrorMap();
        mirrorMap.load();

        System.out.println("SymmetricSparkleEffect initialized:");
        System.out.println("  FPS: " + fps);
        System.out.println("  Spawn rate: " + spawnRate + " pairs/sec");
        System.out.println("  Duration: " + sparkleDurationSec + "s");
        System.out.println("  Max active: " + maxActive);
        System.out.println("  Brightness: " + brightness);
        System.out.println("  Saturation: " + saturation);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        double dt = 1.0 / fps;

        // Remove expired sparkles
        Iterator<Sparkle> it = activeSparkles.iterator();
        while (it.hasNext()) {
            Sparkle s = it.next();
            if (timeSeconds - s.birthTime >= s.duration) {
                it.remove();
            }
        }

        // Spawn new sparkles
        spawnAccumulator += spawnRate * dt;
        while (spawnAccumulator >= 1.0 && activeSparkles.size() < maxActive) {
            spawnAccumulator -= 1.0;
            spawnSparkle(timeSeconds);
        }

        // Render active sparkles
        for (Sparkle s : activeSparkles) {
            double age = timeSeconds - s.birthTime;
            double t = age / s.duration;  // normalised 0..1
            double envelope = computeEnvelope(t);

            float scaledBrightness = (float) (brightness * envelope);
            if (scaledBrightness < 0.01f) continue;

            Color color = Color.getHSBColor(s.hue, saturation, scaledBrightness);

            pixels.put(s.ledA, color);
            if (s.ledB != s.ledA) {
                pixels.put(s.ledB, color);
            }
        }

        return pixels;
    }

    /**
     * Triangle-ish envelope: ramp up first 20%, sustain middle 40%, ramp down last 40%.
     */
    private double computeEnvelope(double t) {
        if (t < 0.0) return 0.0;
        if (t > 1.0) return 0.0;
        if (t < 0.2) {
            // Ramp up
            return t / 0.2;
        } else if (t < 0.6) {
            // Sustain at full
            return 1.0;
        } else {
            // Ramp down
            return (1.0 - t) / 0.4;
        }
    }

    private void spawnSparkle(double timeSeconds) {
        // Collect LEDs already sparkling
        Set<Integer> activeLEDs = new HashSet<>();
        for (Sparkle s : activeSparkles) {
            activeLEDs.add(s.ledA);
            activeLEDs.add(s.ledB);
        }

        // Try a few times to find an LED that isn't already sparkling
        int attempts = 20;
        for (int i = 0; i < attempts; i++) {
            int ledA = random.nextInt(coords.getCount());
            if (activeLEDs.contains(ledA)) continue;

            int ledB = mirrorMap.getMirror(ledA);
            if (ledB != ledA && activeLEDs.contains(ledB)) continue;

            float hue = random.nextFloat();
            Sparkle sparkle = new Sparkle(ledA, ledB, hue, timeSeconds, sparkleDurationSec);
            activeSparkles.add(sparkle);
            return;
        }
    }

    @Override
    public void dispose() {
        activeSparkles.clear();
    }

    @Override
    public String getName() {
        return "Symmetric Sparkle";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
