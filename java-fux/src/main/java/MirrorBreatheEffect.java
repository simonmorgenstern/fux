import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Mirror Breathe — all LEDs pulse brightness with phase offset based on
 * distance from the vertical midline.  Mirror pairs always share the same
 * phase and color, creating concentric breathing rings that radiate outward
 * from the fox's spine.
 *
 * Center LEDs breathe in blue, gradually shifting to purple at the edges.
 */
public class MirrorBreatheEffect implements Effect {

    private PixelCoordinates coords;
    private LEDMirrorMap mirrorMap;
    private int fps;
    private double breathPeriodSec;
    private double phaseSpread;
    private double minBrightness;
    private double maxBrightness;
    private double centerHue;
    private double edgeHue;
    private double saturation;

    /** Per-LED normalized distance from midline [0..1], cached at init. */
    private double[] normalizedDist;
    private double maxHalfWidth;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.breathPeriodSec = params.has("breath_period_sec")
            ? params.get("breath_period_sec").getAsDouble() : 3.5;
        this.phaseSpread = params.has("phase_spread")
            ? params.get("phase_spread").getAsDouble() : 1.2;
        this.minBrightness = params.has("min_brightness")
            ? params.get("min_brightness").getAsDouble() : 0.05;
        this.maxBrightness = params.has("max_brightness")
            ? params.get("max_brightness").getAsDouble() : 1.0;
        this.centerHue = params.has("center_hue")
            ? params.get("center_hue").getAsDouble() : 0.55;
        this.edgeHue = params.has("edge_hue")
            ? params.get("edge_hue").getAsDouble() : 0.85;
        this.saturation = params.has("saturation")
            ? params.get("saturation").getAsDouble() : 0.8;

        this.mirrorMap = new LEDMirrorMap();
        this.mirrorMap.load();

        this.maxHalfWidth = mirrorMap.computeMaxHalfWidth(coords);
        if (maxHalfWidth < 1.0) maxHalfWidth = 1.0;

        int n = coords.getCount();
        this.normalizedDist = new double[n];
        for (int i = 0; i < n; i++) {
            normalizedDist[i] = mirrorMap.getNormalizedDistance(i, coords, maxHalfWidth);
        }

        System.out.println("MirrorBreatheEffect initialized:");
        System.out.println("  LEDs: " + n + ", maxHalfWidth: " + maxHalfWidth);
        System.out.println("  Breath period: " + breathPeriodSec + " s, phase spread: " + phaseSpread + " rad");
        System.out.println("  Brightness range: " + minBrightness + " - " + maxBrightness);
        System.out.println("  Hue range: " + centerHue + " (center) - " + edgeHue + " (edge)");
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        int n = coords.getCount();
        Map<Integer, Color> pixels = new HashMap<Integer, Color>();

        for (int i = 0; i < n; i++) {
            int mirror = mirrorMap.getMirror(i);

            // Only compute for the canonical LED (i <= mirror), then copy to partner
            if (i > mirror) {
                continue;
            }

            double norm = normalizedDist[i];

            // Phase: global oscillation minus distance-based offset
            double phase = (2.0 * Math.PI * timeSeconds / breathPeriodSec) - (norm * phaseSpread);

            // Envelope: sine mapped to [min_brightness, max_brightness]
            double envelope = (Math.sin(phase) + 1.0) / 2.0;
            double brightness = minBrightness + (maxBrightness - minBrightness) * envelope;

            // Hue: lerp from center to edge
            double hue = centerHue + (edgeHue - centerHue) * norm;

            Color color = Color.getHSBColor((float) hue, (float) saturation, (float) brightness);

            pixels.put(i, color);
            if (mirror != i) {
                pixels.put(mirror, color);
            }
        }

        return pixels;
    }

    @Override
    public void dispose() {
        // Nothing to clean up
    }

    @Override
    public String getName() {
        return "Mirror Breathe";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
