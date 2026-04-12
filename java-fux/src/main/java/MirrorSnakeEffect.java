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

    private double headPosition; // shared floating LED-step counter
    private List<SnakeGroup> groups;

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
        this.headPosition = 0;

        // Load topology and mirror map
        this.topology = new LEDBoxTopology();
        this.topology.load();

        this.mirrorMap = new LEDMirrorMap();
        this.mirrorMap.load();

        // Build one SnakeGroup per symmetric pair, driving only the left box.
        this.groups = new ArrayList<>();
        List<LEDBoxTopology.Pair> pairs = topology.getPairs();
        for (int i = 0; i < pairs.size(); i++) {
            LEDBoxTopology.Pair p = pairs.get(i);
            float hue = pairs.size() <= 1 ? 0.6f : (float) i / (float) pairs.size();
            Color baseColor = Color.getHSBColor(hue, (float) saturation, 1f);
            groups.add(new SnakeGroup(p.left, baseColor));
        }

        int snakeCount = 0;
        for (SnakeGroup g : groups) snakeCount += snakesPerBox;
        System.out.println("MirrorSnakeEffect initialized:");
        System.out.println("  Groups: " + groups.size() + ", snakes total: " + snakeCount);
        System.out.println("  Speed: " + speedLedsPerSec + " leds/sec, trail: " + trailLength);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        headPosition += speedLedsPerSec / fps;

        int n = coords.getCount();
        double[] r = new double[n];
        double[] g = new double[n];
        double[] b = new double[n];

        for (SnakeGroup sg : groups) {
            int len = sg.box.perimeter.size();
            if (len == 0) continue;
            int head = (int) Math.floor(headPosition);

            for (int s = 0; s < snakesPerBox; s++) {
                // Evenly space snakes around the perimeter
                int snakeOffset = (len * s) / snakesPerBox;

                for (int t = 0; t < trailLength; t++) {
                    int offset = head + snakeOffset - t;
                    int idx = ((offset % len) + len) % len;
                    int led = sg.box.perimeter.get(idx);

                    // Brightness falloff along the trail
                    double falloff = 1.0 - (double) t / (double) trailLength;
                    falloff = falloff * falloff; // quadratic soften
                    double k = falloff * brightness;

                    double cr = sg.color.getRed()   * k;
                    double cg = sg.color.getGreen() * k;
                    double cb = sg.color.getBlue()  * k;

                    // Light the LED on the driving (left) box
                    r[led] += cr;
                    g[led] += cg;
                    b[led] += cb;

                    // Light its per-LED mirror twin
                    int mirror = mirrorMap.getMirror(led);
                    if (mirror != led) {
                        r[mirror] += cr;
                        g[mirror] += cg;
                        b[mirror] += cb;
                    }
                }
            }
        }

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

    @Override
    public void dispose() {
        if (groups != null) groups.clear();
    }

    @Override public String getName() { return "Mirror Snake"; }
    @Override public int getFPS() { return fps; }

    /** One group per symmetric pair — drives the left box only. */
    private static class SnakeGroup {
        final LEDBoxTopology.Box box;
        final Color color;
        SnakeGroup(LEDBoxTopology.Box box, Color color) {
            this.box = box;
            this.color = color;
        }
    }
}
