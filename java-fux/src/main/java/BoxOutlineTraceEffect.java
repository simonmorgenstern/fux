import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Box Outline Trace — a comet runs around the perimeter of one box pair
 * at a time, leaving a fading trail. For each lit LED, its exact mirror
 * partner (from led-mirrors.json) is also lit, guaranteeing perfect
 * horizontal symmetry.
 */
public class BoxOutlineTraceEffect implements Effect {
    private PixelCoordinates coords;
    private LEDBoxTopology topology;
    private LEDMirrorMap mirrorMap;
    private int fps;
    private double speedLedsPerSec;
    private int trailLength;
    private double brightness;
    private double saturation;
    private double pairDurationSec;

    private double headPosition;
    private List<LEDBoxTopology.Pair> pairs;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.speedLedsPerSec = params.has("speed_leds_per_sec")
            ? params.get("speed_leds_per_sec").getAsDouble() : 18.0;
        this.trailLength = params.has("trail_length")
            ? params.get("trail_length").getAsInt() : 6;
        this.brightness = params.has("brightness")
            ? params.get("brightness").getAsDouble() : 1.0;
        this.saturation = params.has("saturation")
            ? params.get("saturation").getAsDouble() : 0.85;
        this.pairDurationSec = params.has("pair_duration_sec")
            ? params.get("pair_duration_sec").getAsDouble() : 6.0;
        this.headPosition = 0;

        this.topology = new LEDBoxTopology();
        this.topology.load();
        this.pairs = topology.getPairs();

        this.mirrorMap = new LEDMirrorMap();
        this.mirrorMap.load();

        System.out.println("BoxOutlineTraceEffect initialized:");
        System.out.println("  Pairs: " + pairs.size() + " (cycling one at a time)");
        System.out.println("  Mirror map loaded: " + mirrorMap.isLoaded());
        System.out.println("  Speed: " + speedLedsPerSec + " leds/sec, trail: " + trailLength);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        headPosition += speedLedsPerSec / fps;

        int n = coords.getCount();
        double[] r = new double[n];
        double[] g = new double[n];
        double[] b = new double[n];

        if (pairs.isEmpty()) return new HashMap<>();

        // Cycle one pair at a time with crossfade
        int totalPairs = pairs.size();
        double slot = timeSeconds / pairDurationSec;
        int activeIdx = ((int) slot) % totalPairs;
        double progress = slot - Math.floor(slot);
        double fade = 1.0;
        if (progress < 0.1) fade = progress / 0.1;
        else if (progress > 0.9) fade = (1.0 - progress) / 0.1;

        LEDBoxTopology.Pair p = pairs.get(activeIdx);
        float hue = totalPairs <= 1 ? 0.6f : (float) activeIdx / (float) totalPairs;
        Color baseColor = Color.getHSBColor(hue, (float) saturation, 1f);

        // Trace only the left box; mirror each LED to get the right side
        renderBoxWithMirror(p.left, baseColor, fade, r, g, b);

        Map<Integer, Color> pixels = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (r[i] > 0 || g[i] > 0 || b[i] > 0) {
                pixels.put(i, new Color(
                    Math.min(255, (int) r[i]),
                    Math.min(255, (int) g[i]),
                    Math.min(255, (int) b[i])
                ));
            }
        }
        return pixels;
    }

    private void renderBoxWithMirror(LEDBoxTopology.Box box, Color color, double fade,
                                     double[] r, double[] g, double[] b) {
        int len = box.perimeter.size();
        if (len == 0) return;
        int head = (int) Math.floor(headPosition);
        for (int t = 0; t < trailLength; t++) {
            int offset = head - t;
            int idx = ((offset % len) + len) % len;
            int led = box.perimeter.get(idx);
            double falloff = 1.0 - (double) t / (double) trailLength;
            falloff = falloff * falloff;
            double k = falloff * brightness * fade;

            // Light the LED itself
            r[led] += color.getRed() * k;
            g[led] += color.getGreen() * k;
            b[led] += color.getBlue() * k;

            // Light its exact mirror partner
            int mirror = mirrorMap.getMirror(led);
            if (mirror != led) {
                r[mirror] += color.getRed() * k;
                g[mirror] += color.getGreen() * k;
                b[mirror] += color.getBlue() * k;
            }
        }
    }

    @Override
    public void dispose() {}

    @Override public String getName() { return "Box Outline Trace"; }
    @Override public int getFPS() { return fps; }
}
