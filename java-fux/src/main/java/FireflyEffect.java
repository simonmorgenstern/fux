import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class FireflyEffect implements Effect {
    private PixelCoordinates coords;
    private List<Firefly> fireflies;
    private Random random;
    private int fireflyCount;
    private double speed;
    private double glowRadius;
    private int trailFrames;
    private double brightness;
    private int fps;

    // Precomputed nearest neighbors for each LED (top 5 closest)
    private int[][] nearestNeighbors;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.fireflyCount = params.has("firefly_count") ? params.get("firefly_count").getAsInt() : 8;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 0.5;
        this.glowRadius = params.has("glow_radius") ? params.get("glow_radius").getAsDouble() : 40;
        this.trailFrames = params.has("trail_frames") ? params.get("trail_frames").getAsInt() : 15;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;

        // Precompute nearest 5 neighbors for each LED
        int count = coords.getCount();
        nearestNeighbors = new int[count][5];
        for (int i = 0; i < count; i++) {
            PixelCoordinate pi = coords.get(i);
            double piX = pi.getX();
            double piY = pi.getY();

            // Build list of (index, distance) pairs excluding self
            List<int[]> distances = new ArrayList<>();
            for (int j = 0; j < count; j++) {
                if (j == i) continue;
                PixelCoordinate pj = coords.get(j);
                double dx = piX - pj.getX();
                double dy = piY - pj.getY();
                int distInt = (int) (Math.sqrt(dx * dx + dy * dy) * 1000);
                distances.add(new int[]{j, distInt});
            }
            distances.sort((a, b) -> Integer.compare(a[1], b[1]));
            for (int k = 0; k < 5; k++) {
                nearestNeighbors[i][k] = distances.get(k)[0];
            }
        }

        // Spawn fireflies at random LED positions
        this.fireflies = new ArrayList<>();
        for (int i = 0; i < fireflyCount; i++) {
            Firefly f = new Firefly();
            f.currentLedIndex = random.nextInt(count);
            f.glowPhase = random.nextDouble() * Math.PI * 2;
            f.moveTimer = 0.0;
            // Each firefly gets a hue in the warm yellow-green range (70-120 degrees)
            f.hue = (70f + random.nextFloat() * 50f) / 360f;
            f.trail = new LinkedHashMap<>();
            fireflies.add(f);
        }

        System.out.println("FireflyEffect initialized:");
        System.out.println("  Firefly count: " + fireflyCount);
        System.out.println("  Speed: " + speed);
        System.out.println("  Glow radius: " + glowRadius);
        System.out.println("  Trail frames: " + trailFrames);
        System.out.println("  Brightness: " + brightness);
        System.out.println("  LED count: " + count);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        double dt = 1.0 / fps;
        double moveInterval = 1.0 / speed;

        // Accumulators for additive blending
        double[] accR = new double[coords.getCount()];
        double[] accG = new double[coords.getCount()];
        double[] accB = new double[coords.getCount()];

        for (Firefly f : fireflies) {
            // Advance move timer and move if needed
            f.moveTimer += dt;
            if (f.moveTimer >= moveInterval) {
                f.moveTimer -= moveInterval;

                // Record current position in trail
                f.trail.put(f.currentLedIndex, trailFrames);

                // Move to a random neighbor from the 5 nearest
                int neighborIdx = random.nextInt(5);
                f.currentLedIndex = nearestNeighbors[f.currentLedIndex][neighborIdx];
            }

            // Advance glow phase for pulsing
            f.glowPhase += dt * 2.5;

            // Pulse intensity using sin wave, range [0.4, 1.0]
            double pulse = 0.4 + 0.6 * (0.5 + 0.5 * Math.sin(f.glowPhase));
            pulse *= brightness;

            // Current firefly position
            PixelCoordinate fireflyPos = coords.get(f.currentLedIndex);
            double fx = fireflyPos.getX();
            double fy = fireflyPos.getY();

            // Apply glow to nearby LEDs
            for (int j = 0; j < coords.getCount(); j++) {
                PixelCoordinate led = coords.get(j);
                double dx = led.getX() - fx;
                double dy = led.getY() - fy;
                double dist = Math.sqrt(dx * dx + dy * dy);

                if (dist < glowRadius) {
                    double ratio = dist / glowRadius;
                    double intensity = (1.0 - ratio) * (1.0 - ratio) * pulse;

                    Color glowColor = Color.getHSBColor(f.hue, 0.8f, (float) intensity);
                    accR[j] += glowColor.getRed();
                    accG[j] += glowColor.getGreen();
                    accB[j] += glowColor.getBlue();
                }
            }

            // Render trail: fade out visited LEDs
            Iterator<Map.Entry<Integer, Integer>> it = f.trail.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Integer> entry = it.next();
                int ledIdx = entry.getKey();
                int remaining = entry.getValue() - 1;

                if (remaining <= 0) {
                    it.remove();
                    continue;
                }
                entry.setValue(remaining);

                double trailIntensity = ((double) remaining / trailFrames) * pulse * 0.4;
                Color trailColor = Color.getHSBColor(f.hue, 0.8f, (float) trailIntensity);
                accR[ledIdx] += trailColor.getRed();
                accG[ledIdx] += trailColor.getGreen();
                accB[ledIdx] += trailColor.getBlue();
            }
        }

        // Build output, capping at 255
        for (int j = 0; j < coords.getCount(); j++) {
            if (accR[j] > 0 || accG[j] > 0 || accB[j] > 0) {
                int r = Math.min(255, (int) accR[j]);
                int g = Math.min(255, (int) accG[j]);
                int b = Math.min(255, (int) accB[j]);
                pixels.put(j, new Color(r, g, b));
            }
        }

        return pixels;
    }

    @Override
    public void dispose() {
        fireflies.clear();
    }

    @Override
    public String getName() {
        return "Firefly";
    }

    @Override
    public int getFPS() {
        return fps;
    }

    private static class Firefly {
        int currentLedIndex;
        double glowPhase;
        double moveTimer;
        float hue;
        Map<Integer, Integer> trail; // LED index -> remaining fade frames
    }
}
