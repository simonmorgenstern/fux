import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Box Bloom — boxes (closed cycles in the LED graph) light up one symmetric
 * group at a time. Mirror pairs always bloom together with the same color so
 * the fox stays horizontally symmetric. Each bloom fades out while the next
 * one starts, so several blooms overlap and create a slow, breathing wave.
 */
public class BoxBloomEffect implements Effect {
    private PixelCoordinates coords;
    private LEDBoxTopology topology;
    private Random random;
    private int fps;
    private double bloomDurationSec;
    private double spawnIntervalSec;
    private double brightness;
    private boolean outsideIn;

    private List<ActiveBloom> active;
    private double timeSinceSpawnSec;
    private int nextGroupIdx;

    private static final Color[] PALETTE = {
        new Color(255, 100,  60),  // warm orange
        new Color(255, 200,  60),  // golden
        new Color( 80, 255, 200),  // teal
        new Color( 60, 180, 255),  // sky
        new Color(180,  80, 255),  // violet
        new Color(255,  80, 180),  // pink
        new Color(120, 255, 120),  // mint
    };

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.active = new ArrayList<>();
        this.timeSinceSpawnSec = Double.MAX_VALUE; // spawn immediately
        this.nextGroupIdx = 0;

        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.bloomDurationSec = params.has("bloom_duration_sec")
            ? params.get("bloom_duration_sec").getAsDouble() : 2.2;
        this.spawnIntervalSec = params.has("spawn_interval_sec")
            ? params.get("spawn_interval_sec").getAsDouble() : 0.55;
        this.brightness = params.has("brightness")
            ? params.get("brightness").getAsDouble() : 1.0;
        this.outsideIn = !params.has("outside_in") || params.get("outside_in").getAsBoolean();

        this.topology = new LEDBoxTopology();
        this.topology.load();

        System.out.println("BoxBloomEffect initialized:");
        System.out.println("  Boxes: " + topology.boxCount() + ", symmetric groups: " + topology.pairCount());
        System.out.println("  Bloom duration: " + bloomDurationSec + "s, spawn interval: " + spawnIntervalSec + "s");
        System.out.println("  Order: " + (outsideIn ? "outside-in" : "inside-out"));
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        double dt = 1.0 / fps;
        timeSinceSpawnSec += dt;

        // Spawn a new bloom if it's time and we have any groups
        if (topology.pairCount() > 0 && timeSinceSpawnSec >= spawnIntervalSec) {
            spawnBloom();
            timeSinceSpawnSec = 0.0;
        }

        // Advance and prune blooms
        for (ActiveBloom b : active) b.ageSec += dt;
        active.removeIf(b -> b.ageSec >= bloomDurationSec);

        // Accumulate color contributions (additive blending, clamped to 255)
        int n = coords.getCount();
        double[] r = new double[n];
        double[] g = new double[n];
        double[] bl = new double[n];

        for (ActiveBloom b : active) {
            double t = b.ageSec / bloomDurationSec; // 0..1
            // envelope: fast attack, long decay (mirrored asymmetric triangle)
            double env;
            if (t < 0.18) {
                env = t / 0.18;
            } else {
                double k = (t - 0.18) / 0.82;
                env = 1.0 - k;
            }
            env = Math.max(0, Math.min(1, env)) * brightness;

            applyBox(b.pair.left, b.color, env, r, g, bl);
            if (!b.pair.selfSymmetric) {
                applyBox(b.pair.right, b.color, env, r, g, bl);
            }
        }

        Map<Integer, Color> pixels = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (r[i] > 0 || g[i] > 0 || bl[i] > 0) {
                pixels.put(i, new Color(
                    Math.min(255, (int) r[i]),
                    Math.min(255, (int) g[i]),
                    Math.min(255, (int) bl[i])
                ));
            }
        }
        return pixels;
    }

    private void applyBox(LEDBoxTopology.Box box, Color color, double envelope,
                          double[] r, double[] g, double[] bl) {
        if (envelope <= 0) return;
        double cr = color.getRed() * envelope;
        double cg = color.getGreen() * envelope;
        double cb = color.getBlue() * envelope;
        for (int led : box.perimeter) {
            r[led] += cr;
            g[led] += cg;
            bl[led] += cb;
        }
    }

    private void spawnBloom() {
        List<LEDBoxTopology.Pair> groups = topology.getPairs();
        if (groups.isEmpty()) return;
        // Groups are pre-sorted outside-in. Walk them in order so the bloom
        // sweeps from the perimeter toward the center (or vice versa).
        int idx = outsideIn ? nextGroupIdx : (groups.size() - 1 - nextGroupIdx);
        idx = ((idx % groups.size()) + groups.size()) % groups.size();
        LEDBoxTopology.Pair pair = groups.get(idx);
        Color color = PALETTE[random.nextInt(PALETTE.length)];
        active.add(new ActiveBloom(pair, color));
        nextGroupIdx = (nextGroupIdx + 1) % groups.size();
    }

    @Override
    public void dispose() {
        if (active != null) active.clear();
    }

    @Override public String getName() { return "Box Bloom"; }
    @Override public int getFPS() { return fps; }

    private static class ActiveBloom {
        final LEDBoxTopology.Pair pair;
        final Color color;
        double ageSec;
        ActiveBloom(LEDBoxTopology.Pair pair, Color color) {
            this.pair = pair;
            this.color = color;
            this.ageSec = 0;
        }
    }
}
