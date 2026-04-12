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
    private double centerHue;
    private double edgeHue;
    private double saturation;
    private double verticalPhaseSec;
    private double pairDurationSec;

    private double maxDistFromMidline;
    private double maxYRange;
    private double minY;
    private List<LEDBoxTopology.Pair> pairs;

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
            ? params.get("center_hue").getAsDouble() : 0.08;
        this.edgeHue = params.has("edge_hue")
            ? params.get("edge_hue").getAsDouble() : 0.58;
        this.saturation = params.has("saturation")
            ? params.get("saturation").getAsDouble() : 0.9;
        this.verticalPhaseSec = params.has("vertical_phase_sec")
            ? params.get("vertical_phase_sec").getAsDouble() : 0.6;
        this.pairDurationSec = params.has("pair_duration_sec")
            ? params.get("pair_duration_sec").getAsDouble() : 5.0;

        this.topology = new LEDBoxTopology();
        this.topology.load();
        this.pairs = topology.getPairs();

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
        System.out.println("  Pairs: " + pairs.size() + " (cycling one at a time)");
        System.out.println("  Breath period: " + breathPeriodSec + "s");
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        if (pairs.isEmpty()) return pixels;

        // Cycle one pair at a time with crossfade.
        int totalPairs = pairs.size();
        double slot = timeSeconds / pairDurationSec;
        int activeIdx = ((int) slot) % totalPairs;
        double progress = slot - Math.floor(slot);
        double fade = 1.0;
        if (progress < 0.1) fade = progress / 0.1;
        else if (progress > 0.9) fade = (1.0 - progress) / 0.1;

        LEDBoxTopology.Pair p = pairs.get(activeIdx);
        double twoPi = Math.PI * 2;
        double breathOmega = twoPi / Math.max(0.001, breathPeriodSec);

        renderBox(p.left, breathOmega, timeSeconds, fade, pixels);
        if (!p.selfSymmetric) {
            renderBox(p.right, breathOmega, timeSeconds, fade, pixels);
        }
        return pixels;
    }

    private void renderBox(LEDBoxTopology.Box b, double breathOmega, double timeSeconds,
                           double fade, Map<Integer, Color> pixels) {
        double dist = Math.abs(b.centroidX - topology.getMidlineX());
        double tHue = Math.min(1.0, dist / maxDistFromMidline);
        float hue = (float) (centerHue + (edgeHue - centerHue) * tHue);
        hue = (float) (((hue % 1.0) + 1.0) % 1.0);

        double yNorm = (b.centroidY - minY) / maxYRange;
        double phaseOffset = yNorm * verticalPhaseSec * breathOmega;

        double phase = breathOmega * timeSeconds + phaseOffset;
        double s = 0.5 - 0.5 * Math.cos(phase);
        double brightness = minBrightness + (maxBrightness - minBrightness) * s * fade;

        Color base = Color.getHSBColor(hue, (float) saturation, 1f);
        int rr = Math.max(0, Math.min(255, (int) (base.getRed() * brightness)));
        int gg = Math.max(0, Math.min(255, (int) (base.getGreen() * brightness)));
        int bb = Math.max(0, Math.min(255, (int) (base.getBlue() * brightness)));
        Color out = new Color(rr, gg, bb);

        for (int led : b.perimeter) {
            pixels.put(led, out);
        }
    }

    @Override
    public void dispose() {}

    @Override public String getName() { return "Box Breathe Grid"; }
    @Override public int getFPS() { return fps; }
}
