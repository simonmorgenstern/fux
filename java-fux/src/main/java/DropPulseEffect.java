import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class DropPulseEffect implements Effect {
    private PixelCoordinates coords;
    private double dropRate;
    private double buildRatio;
    private double brightness;
    private int fps;
    private double[] normalizedDistances;
    private Random random;
    private float currentExplosionHue;
    private int lastCycleIndex;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.dropRate = params.has("drop_rate") ? params.get("drop_rate").getAsDouble() : 0.5;
        this.buildRatio = params.has("build_ratio") ? params.get("build_ratio").getAsDouble() : 0.7;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.random = new Random();
        this.currentExplosionHue = random.nextFloat();
        this.lastCycleIndex = -1;

        // Precompute normalized distances for all LEDs
        double maxDist = coords.getMaxDistance();
        int count = coords.getCount();
        this.normalizedDistances = new double[count];
        for (int i = 0; i < count; i++) {
            normalizedDistances[i] = coords.get(i).getDistanceFromCenter() / maxDist;
        }

        System.out.println("DropPulseEffect initialized:");
        System.out.println("  Drop rate: " + dropRate + " drops/sec");
        System.out.println("  Build ratio: " + buildRatio);
        System.out.println("  Brightness: " + brightness);
        System.out.println("  LED count: " + count);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        double cycleDuration = 1.0 / dropRate;
        double cyclePos = (timeSeconds * dropRate) % 1.0;
        int cycleIndex = (int)(timeSeconds * dropRate);

        // Pick a new random hue at the start of each cycle
        if (cycleIndex != lastCycleIndex) {
            currentExplosionHue = random.nextFloat();
            lastCycleIndex = cycleIndex;
        }

        int count = coords.getCount();

        if (cyclePos < buildRatio) {
            // Build phase: LEDs light from outside inward
            double buildProgress = cyclePos / buildRatio;
            double threshold = 1.0 - buildProgress;
            double intensity = buildProgress * 0.6;

            for (int i = 0; i < count; i++) {
                double nd = normalizedDistances[i];
                if (nd > threshold) {
                    // Deep blue/purple hue (~0.7), increasing saturation and brightness
                    float sat = (float)(0.6 + 0.4 * buildProgress);
                    float bri = (float)(intensity * brightness);
                    bri = Math.min(1.0f, Math.max(0.0f, bri));
                    Color c = Color.getHSBColor(0.7f, sat, bri);
                    pixels.put(coords.get(i).getIndex(), c);
                } else {
                    pixels.put(coords.get(i).getIndex(), Color.BLACK);
                }
            }
        } else {
            // Explosion phase: bright ring expands from center outward
            double explosionProgress = (cyclePos - buildRatio) / (1.0 - buildRatio);
            double ringPos = explosionProgress;
            double ringWidth = 0.3;
            double overallDecay = 1.0 - explosionProgress;

            for (int i = 0; i < count; i++) {
                double nd = normalizedDistances[i];
                double distFromRing = Math.abs(nd - ringPos);

                if (distFromRing < ringWidth / 2.0) {
                    // On the ring: white/warm flash at full brightness
                    double ringIntensity = 1.0 - (distFromRing / (ringWidth / 2.0));
                    ringIntensity *= overallDecay;
                    float bri = (float)(ringIntensity * brightness);
                    bri = Math.min(1.0f, Math.max(0.0f, bri));
                    // Warm white (low saturation, slight warm hue)
                    Color c = Color.getHSBColor(0.1f, 0.15f, bri);
                    pixels.put(coords.get(i).getIndex(), c);
                } else if (nd < ringPos) {
                    // Behind the ring: saturated explosion color that fades
                    double fade = overallDecay * (1.0 - (ringPos - nd));
                    fade = Math.max(0.0, fade);
                    float bri = (float)(fade * 0.8 * brightness);
                    bri = Math.min(1.0f, Math.max(0.0f, bri));
                    Color c = Color.getHSBColor(currentExplosionHue, 0.9f, bri);
                    pixels.put(coords.get(i).getIndex(), c);
                } else {
                    pixels.put(coords.get(i).getIndex(), Color.BLACK);
                }
            }
        }

        return pixels;
    }

    @Override
    public void dispose() {
        // Nothing to clean up
    }

    @Override
    public String getName() {
        return "Drop Pulse";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
