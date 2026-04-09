import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class FireflySyncEffect implements Effect {
    private PixelCoordinates coords;
    private LEDNeighborGraph graph;
    private Random random;
    private int fps;
    private double couplingStrength;
    private double baseFrequency;
    private double frequencySpread;
    private int flashDurationFrames;
    private double brightness;

    private double[] phase;
    private double[] naturalFreq;
    private int[] flashTimer; // countdown when flashing, -1 if not

    // Colors
    private static final Color RESTING = new Color(5, 3, 0);
    private static final Color GLOW = new Color(80, 60, 0);
    private static final Color FLASH = new Color(180, 255, 50);

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();

        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.couplingStrength = params.has("coupling_strength") ?
            params.get("coupling_strength").getAsDouble() : 0.05;
        this.baseFrequency = params.has("base_frequency") ?
            params.get("base_frequency").getAsDouble() : 1.2;
        this.frequencySpread = params.has("frequency_spread") ?
            params.get("frequency_spread").getAsDouble() : 0.3;
        this.flashDurationFrames = params.has("flash_duration_frames") ?
            params.get("flash_duration_frames").getAsInt() : 4;
        this.brightness = params.has("brightness") ?
            params.get("brightness").getAsDouble() : 1.0;

        // Build neighbor graph
        double minDist = params.has("min_neighbor_distance") ?
            params.get("min_neighbor_distance").getAsDouble() : 5.0;
        double maxDist = params.has("max_neighbor_distance") ?
            params.get("max_neighbor_distance").getAsDouble() : 30.0;
        int maxNeighbors = params.has("max_neighbors") ?
            params.get("max_neighbors").getAsInt() : 6;

        this.graph = new LEDNeighborGraph(coords);
        this.graph.build(minDist, maxDist, maxNeighbors);

        int count = coords.getCount();
        phase = new double[count];
        naturalFreq = new double[count];
        flashTimer = new int[count];

        for (int i = 0; i < count; i++) {
            // Random initial phase so fireflies start unsynchronized
            phase[i] = random.nextDouble() * 2.0 * Math.PI;
            // Natural frequency with random spread around base
            naturalFreq[i] = baseFrequency + (random.nextDouble() * 2.0 - 1.0) * frequencySpread;
            flashTimer[i] = -1;
        }

        System.out.println("FireflySyncEffect initialized:");
        System.out.println("  Graph from file: " + graph.isLoadedFromFile());
        System.out.println("  Coupling strength: " + couplingStrength);
        System.out.println("  Base frequency: " + baseFrequency + " Hz (spread: " + frequencySpread + ")");
        System.out.println("  Flash duration: " + flashDurationFrames + " frames");
        System.out.println("  Brightness: " + brightness);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        int count = coords.getCount();
        double dt = 1.0 / fps;

        // Collect which LEDs flash this frame so we can nudge neighbors after
        List<Integer> flashedThisFrame = new ArrayList<>();

        // 1. Advance phases and detect flashes
        for (int i = 0; i < count; i++) {
            if (flashTimer[i] >= 0) {
                // Currently flashing, just count down
                flashTimer[i]--;
                continue;
            }

            // Advance phase
            phase[i] += naturalFreq[i] * 2.0 * Math.PI * dt;

            // Check for flash trigger
            if (phase[i] >= 2.0 * Math.PI) {
                phase[i] = 0.0;
                flashTimer[i] = flashDurationFrames;
                flashedThisFrame.add(i);
            }
        }

        // 2. Nudge neighbors of LEDs that just flashed
        for (int led : flashedThisFrame) {
            for (int neighbor : graph.getNeighbors(led)) {
                if (flashTimer[neighbor] < 0) {
                    // Only nudge LEDs that are not currently flashing
                    phase[neighbor] += couplingStrength;
                }
            }
        }

        // 3. Render colors
        Map<Integer, Color> pixels = new HashMap<>();
        double threshold = 4.0 * Math.PI / 5.0; // ~80% of cycle

        for (int i = 0; i < count; i++) {
            Color color;

            if (flashTimer[i] >= 0) {
                // Flashing: bright yellow-green fading over flash duration
                double flashProgress = (double) flashTimer[i] / flashDurationFrames;
                color = interpolateColor(GLOW, FLASH, flashProgress * brightness);
            } else if (phase[i] < threshold) {
                // Resting: dark amber
                color = RESTING;
            } else {
                // Ramping up: interpolate from resting to warm glow
                double rampProgress = (phase[i] - threshold) / (2.0 * Math.PI - threshold);
                color = interpolateColor(RESTING, GLOW, rampProgress * brightness);
            }

            pixels.put(i, color);
        }

        return pixels;
    }

    private static Color interpolateColor(Color a, Color b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, bl))
        );
    }

    @Override
    public void dispose() {
        phase = null;
        naturalFreq = null;
        flashTimer = null;
    }

    @Override
    public String getName() {
        return "Firefly Sync";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
