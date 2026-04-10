import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Box Outline Trace — a comet runs around the perimeter of every box at the
 * same time, leaving a fading trail. Mirror pairs run in opposite directions
 * around their perimeters so the fox stays horizontally symmetric.
 *
 * Each box's hue is a function of its symmetric group index, so paired boxes
 * always share a color, and the inside-to-outside layering is visually
 * coherent.
 */
public class BoxOutlineTraceEffect implements Effect {
    private PixelCoordinates coords;
    private LEDBoxTopology topology;
    private int fps;
    private double speedLedsPerSec;
    private int trailLength;
    private double brightness;
    private double saturation;

    private double headPosition; // floating LED-step counter shared by all boxes
    private List<BoxState> states;

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
        this.headPosition = 0;

        this.topology = new LEDBoxTopology();
        this.topology.load();

        this.states = new ArrayList<>();
        List<LEDBoxTopology.Pair> groups = topology.getPairs();
        for (int i = 0; i < groups.size(); i++) {
            LEDBoxTopology.Pair p = groups.get(i);
            // Hue spans 0..1 across the symmetric groups so paired boxes match.
            float hue = groups.size() <= 1 ? 0.6f : (float) i / (float) groups.size();
            Color baseColor = Color.getHSBColor(hue, (float) saturation, 1f);
            // Left box runs forward, right box runs backward (opposite direction).
            states.add(new BoxState(p.left, baseColor, +1));
            if (!p.selfSymmetric) {
                states.add(new BoxState(p.right, baseColor, -1));
            }
        }

        System.out.println("BoxOutlineTraceEffect initialized:");
        System.out.println("  Active boxes: " + states.size());
        System.out.println("  Speed: " + speedLedsPerSec + " leds/sec, trail: " + trailLength);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        headPosition += speedLedsPerSec / fps;

        int n = coords.getCount();
        double[] r = new double[n];
        double[] g = new double[n];
        double[] b = new double[n];

        for (BoxState st : states) {
            int len = st.box.perimeter.size();
            if (len == 0) continue;
            int head = (int) Math.floor(headPosition);
            // direction-aware index along the perimeter
            for (int t = 0; t < trailLength; t++) {
                int offset = head - t;
                int idx;
                if (st.direction > 0) {
                    idx = ((offset % len) + len) % len;
                } else {
                    idx = ((-offset % len) + len) % len;
                }
                int led = st.box.perimeter.get(idx);
                // Brightness falloff along the trail
                double falloff = 1.0 - (double) t / (double) trailLength;
                falloff = falloff * falloff; // soften
                double k = falloff * brightness;
                r[led] += st.color.getRed() * k;
                g[led] += st.color.getGreen() * k;
                b[led] += st.color.getBlue() * k;
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
        if (states != null) states.clear();
    }

    @Override public String getName() { return "Box Outline Trace"; }
    @Override public int getFPS() { return fps; }

    private static class BoxState {
        final LEDBoxTopology.Box box;
        final Color color;
        final int direction; // +1 forward, -1 reverse around the perimeter
        BoxState(LEDBoxTopology.Box box, Color color, int direction) {
            this.box = box;
            this.color = color;
            this.direction = direction;
        }
    }
}
