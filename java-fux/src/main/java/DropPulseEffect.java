import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

/**
 * Drop Pulse — energy gathers inward towards the fox's visual center
 * (LEDs 145, 146, 183, 184, 213), then explodes outward as a bright ring.
 *
 * Distance is measured as BFS graph hops from the center LEDs so the
 * pulse follows the fox's actual wiring topology. Mirror symmetry is
 * enforced via LEDMirrorMap.
 */
public class DropPulseEffect implements Effect {
    private PixelCoordinates coords;
    private LEDMirrorMap mirrorMap;
    private double dropRate;
    private double buildRatio;
    private double brightness;
    private int fps;
    private double[] normalizedDistances; // BFS distance normalised to [0..1]
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

        // Center LED indices — configurable, default to the fox's visual center
        int[] centerLeds;
        if (params.has("center_leds")) {
            JsonArray arr = params.getAsJsonArray("center_leds");
            centerLeds = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) centerLeds[i] = arr.get(i).getAsInt();
        } else {
            centerLeds = new int[]{145, 146, 183, 184, 213};
        }

        // Mirror map for symmetry enforcement
        this.mirrorMap = new LEDMirrorMap();
        this.mirrorMap.load();

        // BFS from center LEDs to get graph-distance for every LED
        LEDNeighborGraph graph = new LEDNeighborGraph(coords);
        graph.build(0, 50, 8);

        int count = coords.getCount();
        int[] dist = new int[count];
        Arrays.fill(dist, -1);
        Queue<Integer> queue = new LinkedList<>();
        for (int s : centerLeds) {
            if (s >= 0 && s < count) { dist[s] = 0; queue.add(s); }
        }
        int maxDist = 0;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v : graph.getNeighbors(u)) {
                if (v >= 0 && v < count && dist[v] < 0) {
                    dist[v] = dist[u] + 1;
                    if (dist[v] > maxDist) maxDist = dist[v];
                    queue.add(v);
                }
            }
        }

        // Normalise to [0..1]
        this.normalizedDistances = new double[count];
        for (int i = 0; i < count; i++) {
            normalizedDistances[i] = dist[i] >= 0 ? (double) dist[i] / Math.max(1, maxDist) : 1.0;
        }

        System.out.println("DropPulseEffect initialized (center-based):");
        System.out.println("  Center LEDs: " + centerLeds.length + ", max BFS dist: " + maxDist);
        System.out.println("  Drop rate: " + dropRate + " drops/sec, build ratio: " + buildRatio);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        double cyclePos = (timeSeconds * dropRate) % 1.0;
        int cycleIndex = (int)(timeSeconds * dropRate);

        if (cycleIndex != lastCycleIndex) {
            currentExplosionHue = random.nextFloat();
            lastCycleIndex = cycleIndex;
        }

        int count = coords.getCount();
        // Compute per-LED color into arrays so we can enforce mirror symmetry.
        float[] hues = new float[count];
        float[] sats = new float[count];
        float[] bris = new float[count];

        if (cyclePos < buildRatio) {
            // Build phase: LEDs light from outside inward
            double buildProgress = cyclePos / buildRatio;
            double threshold = 1.0 - buildProgress;
            double intensity = buildProgress * 0.6;

            for (int i = 0; i < count; i++) {
                double nd = normalizedDistances[i];
                if (nd > threshold) {
                    hues[i] = 0.7f;
                    sats[i] = (float)(0.6 + 0.4 * buildProgress);
                    bris[i] = Math.min(1.0f, Math.max(0.0f, (float)(intensity * brightness)));
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
                    double ringIntensity = 1.0 - (distFromRing / (ringWidth / 2.0));
                    ringIntensity *= overallDecay;
                    hues[i] = 0.1f;
                    sats[i] = 0.15f;
                    bris[i] = Math.min(1.0f, Math.max(0.0f, (float)(ringIntensity * brightness)));
                } else if (nd < ringPos) {
                    double fade = overallDecay * (1.0 - (ringPos - nd));
                    fade = Math.max(0.0, fade);
                    hues[i] = currentExplosionHue;
                    sats[i] = 0.9f;
                    bris[i] = Math.min(1.0f, Math.max(0.0f, (float)(fade * 0.8 * brightness)));
                }
            }
        }

        // Mirror enforcement: each mirror pair gets the max brightness.
        for (int i = 0; i < count; i++) {
            int m = mirrorMap.getMirror(i);
            if (m != i && m >= 0 && m < count && bris[m] > bris[i]) {
                hues[i] = hues[m];
                sats[i] = sats[m];
                bris[i] = bris[m];
            }
        }

        Map<Integer, Color> pixels = new HashMap<>();
        for (int i = 0; i < count; i++) {
            if (bris[i] > 0.001f) {
                pixels.put(i, Color.getHSBColor(hues[i], sats[i], bris[i]));
            }
        }
        return pixels;
    }

    @Override
    public void dispose() {}

    @Override
    public String getName() { return "Drop Pulse"; }

    @Override
    public int getFPS() { return fps; }
}
