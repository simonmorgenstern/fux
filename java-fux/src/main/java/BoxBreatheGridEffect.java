import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Box Breathe Grid — every box pulses ("breathes") in unison, but the hue of
 * each box depends on its horizontal distance from the fox's vertical
 * centerline. The result is perfectly mirror-symmetric: a box on the left
 * side and its mirror twin on the right always show the same color and
 * brightness, because they have the same |x - midline|.
 *
 * A small per-box phase offset (driven by the y-position) gives a gentle
 * vertical wave inside the global breath, so the whole fox feels alive
 * without breaking horizontal symmetry.
 */
public class BoxBreatheGridEffect implements Effect {
    private PixelCoordinates coords;
    private LEDBoxTopology topology;
    private int fps;
    private double breathPeriodSec;
    private double minBrightness;
    private double maxBrightness;
    private double centerHue;   // hue at the midline (0..1)
    private double edgeHue;     // hue at the outermost box (0..1)
    private double saturation;
    private double verticalPhaseSec; // how much vertical position offsets the breath

    private double maxDistFromMidline;
    private double maxYRange;
    private double minY;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.breathPeriodSec = params.has("breath_period_sec")
            ? params.get("breath_period_sec").getAsDouble() : 4.0;
        this.minBrightness = params.has("min_brightness")
            ? params.get("min_brightness").getAsDouble() : 0.10;
        this.maxBrightness = params.has("max_brightness")
            ? params.get("max_brightness").getAsDouble() : 1.0;
        this.centerHue = params.has("center_hue")
            ? params.get("center_hue").getAsDouble() : 0.08; // warm orange
        this.edgeHue = params.has("edge_hue")
            ? params.get("edge_hue").getAsDouble() : 0.58;   // cool blue
        this.saturation = params.has("saturation")
            ? params.get("saturation").getAsDouble() : 0.9;
        this.verticalPhaseSec = params.has("vertical_phase_sec")
            ? params.get("vertical_phase_sec").getAsDouble() : 0.6;

        this.topology = new LEDBoxTopology();
        this.topology.load();

        // Pre-compute the maximum distance from the midline for hue normalization,
        // plus the y range so we can spread the per-box phase offset evenly.
        double maxDist = 1.0;
        double yMin = Double.POSITIVE_INFINITY;
        double yMax = Double.NEGATIVE_INFINITY;
        for (LEDBoxTopology.Box b : topology.getBoxes()) {
            double d = Math.abs(b.centroidX - topology.getMidlineX());
            if (d > maxDist) maxDist = d;
            if (b.centroidY < yMin) yMin = b.centroidY;
            if (b.centroidY > yMax) yMax = b.centroidY;
        }
        this.maxDistFromMidline = maxDist;
        this.minY = yMin;
        this.maxYRange = Math.max(1.0, yMax - yMin);

        System.out.println("BoxBreatheGridEffect initialized:");
        System.out.println("  Boxes: " + topology.boxCount());
        System.out.println("  Breath period: " + breathPeriodSec + "s");
        System.out.println("  Hue range: center=" + centerHue + " edge=" + edgeHue);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        List<LEDBoxTopology.Box> boxes = topology.getBoxes();
        if (boxes.isEmpty()) return pixels;

        double twoPi = Math.PI * 2;
        double breathOmega = twoPi / Math.max(0.001, breathPeriodSec);

        for (LEDBoxTopology.Box b : boxes) {
            // Symmetric horizontal distance from the midline (always >= 0)
            double dist = Math.abs(b.centroidX - topology.getMidlineX());
            double tHue = Math.min(1.0, dist / maxDistFromMidline);
            float hue = (float) (centerHue + (edgeHue - centerHue) * tHue);
            // Normalize hue into [0,1)
            hue = (float) (((hue % 1.0) + 1.0) % 1.0);

            // Per-box vertical phase offset (depends on y only -> stays mirror-symmetric)
            double yNorm = (b.centroidY - minY) / maxYRange;
            double phaseOffset = yNorm * verticalPhaseSec * breathOmega;

            // Cosine breath shaped to (min..max) brightness
            double phase = breathOmega * timeSeconds + phaseOffset;
            double s = 0.5 - 0.5 * Math.cos(phase); // 0..1
            double brightness = minBrightness + (maxBrightness - minBrightness) * s;

            Color base = Color.getHSBColor(hue, (float) saturation, 1f);
            int rr = (int) (base.getRed() * brightness);
            int gg = (int) (base.getGreen() * brightness);
            int bb = (int) (base.getBlue() * brightness);
            Color out = new Color(
                Math.max(0, Math.min(255, rr)),
                Math.max(0, Math.min(255, gg)),
                Math.max(0, Math.min(255, bb))
            );

            for (int led : b.perimeter) {
                // If two boxes share an LED (boxes adjoin at a line), keep the brightest one.
                Color existing = pixels.get(led);
                if (existing == null || luminance(out) > luminance(existing)) {
                    pixels.put(led, out);
                }
            }
        }
        return pixels;
    }

    private static double luminance(Color c) {
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
    }

    @Override
    public void dispose() { /* nothing to clean up */ }

    @Override public String getName() { return "Box Breathe Grid"; }
    @Override public int getFPS() { return fps; }
}
