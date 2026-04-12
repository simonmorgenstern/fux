import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirror Snake — snakes run around box perimeters, and every LED the snake
 * touches also lights its per-LED mirror twin.  This combines three data
 * layers:
 *
 *   1) LEDBoxTopology — box perimeters and symmetric pairs
 *   2) LEDMirrorMap   — per-LED mirror assignments (from led-mirrors.json)
 *   3) PixelCoordinates — spatial positions for hue gradients
 *
 * For each mirror-pair of boxes the effect drives ONE snake on the left
 * box's perimeter; the right side lights up automatically via the mirror
 * map.  Self-symmetric boxes (on the midline) just run one snake whose
 * LEDs are their own mirrors.
 *
 * Multiple snakes can share a box (evenly spaced around the perimeter).
 */
public class MirrorSnakeEffect implements Effect {
    private PixelCoordinates coords;
    private LEDBoxTopology topology;
    private LEDMirrorMap mirrorMap;
    private int fps;
    private double speedLedsPerSec;
    private int trailLength;
    private int snakesPerBox;
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
            ? params.get("speed_leds_per_sec").getAsDouble() : 14.0;
        this.trailLength = params.has("trail_length")
            ? params.get("trail_length").getAsInt() : 8;
        this.snakesPerBox = params.has("snakes_per_box")
            ? params.get("snakes_per_box").getAsInt() : 2;
        this.brightness = params.has("brightness")
            ? params.get("brightness").getAsDouble() : 1.0;
        this.saturation = params.has("saturation")
            ? params.get("saturation").getAsDouble() : 0.85;
        this.pairDurationSec = params.has("pair_duration_sec")
            ? params.get("pair_duration_sec").getAsDouble() : 6.0;
        this.headPosition = 0;

        this.topology = new LEDBoxTopology();
        this.topology.load();

        this.mirrorMap = new LEDMirrorMap();
        this.mirrorMap.load();

        this.pairs = topology.getPairs();

        System.out.println("MirrorSnakeEffect initialized:");
        System.out.println("  Pairs: " + pairs.size() + " (cycling one at a time)");
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

        // Cycle one pair at a time with crossfade.
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

        renderBox(p.left, baseColor, fade, r, g, b);
        // Mirror twin lights up via mirrorMap — no need to explicitly render right box

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

    private void renderBox(LEDBoxTopology.Box box, Color color, double fade,
                           double[] r, double[] g, double[] b) {
        int len = box.perimeter.size();
        if (len == 0) return;
        int head = (int) Math.floor(headPosition);

        for (int s = 0; s < snakesPerBox; s++) {
            int snakeOffset = (len * s) / snakesPerBox;
            for (int t = 0; t < trailLength; t++) {
                int offset = head + snakeOffset - t;
                int idx = ((offset % len) + len) % len;
                int led = box.perimeter.get(idx);

                double falloff = 1.0 - (double) t / (double) trailLength;
                falloff = falloff * falloff;
                double k = falloff * brightness * fade;

                double cr = color.getRed()   * k;
                double cg = color.getGreen() * k;
                double cb = color.getBlue()  * k;

                r[led] += cr;
                g[led] += cg;
                b[led] += cb;

                int mirror = mirrorMap.getMirror(led);
                if (mirror != led && mirror >= 0 && mirror < r.length) {
                    r[mirror] += cr;
                    g[mirror] += cg;
                    b[mirror] += cb;
                }
            }
        }
    }

    @Override
    public void dispose() {}

    @Override public String getName() { return "Mirror Snake"; }
    @Override public int getFPS() { return fps; }

}
