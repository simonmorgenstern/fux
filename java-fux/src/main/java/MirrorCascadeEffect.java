import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Mirror Cascade — a horizontal band of color sweeps top-to-bottom through
 * the fox with perfect left/right symmetry enforced per-LED.  Unlike
 * box-based cascade effects this works at individual LED granularity,
 * producing a much smoother wave.  Hue slowly shifts over time and varies
 * slightly with vertical position for a rainbow-cascade feel.
 */
public class MirrorCascadeEffect implements Effect {

    private PixelCoordinates coords;
    private LEDMirrorMap mirrorMap;
    private int fps;
    private double cascadeSpeed;
    private double waveWidth;
    private double hueShiftSpeed;
    private double brightness;
    private double saturation;

    /** Cached Y coordinate per LED. */
    private double[] ledY;
    /** Minimum and maximum Y across all LEDs. */
    private double minY;
    private double maxY;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.cascadeSpeed = params.has("cascade_speed")
            ? params.get("cascade_speed").getAsDouble() : 100.0;
        this.waveWidth = params.has("wave_width")
            ? params.get("wave_width").getAsDouble() : 120.0;
        this.hueShiftSpeed = params.has("hue_shift_speed")
            ? params.get("hue_shift_speed").getAsDouble() : 0.05;
        this.brightness = params.has("brightness")
            ? params.get("brightness").getAsDouble() : 1.0;
        this.saturation = params.has("saturation")
            ? params.get("saturation").getAsDouble() : 0.85;

        this.mirrorMap = new LEDMirrorMap();
        this.mirrorMap.load();

        int n = coords.getCount();
        this.ledY = new double[n];
        this.minY = Double.MAX_VALUE;
        this.maxY = -Double.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            double y = coords.get(i).getY();
            ledY[i] = y;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }

        System.out.println("MirrorCascadeEffect initialized:");
        System.out.println("  LEDs: " + n + ", Y range: " + minY + " - " + maxY);
        System.out.println("  Cascade speed: " + cascadeSpeed + " px/s, wave width: " + waveWidth);
        System.out.println("  Hue shift speed: " + hueShiftSpeed + ", brightness: " + brightness);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        int n = coords.getCount();
        Map<Integer, Color> pixels = new HashMap<Integer, Color>();

        double totalTravel = (maxY - minY) + waveWidth;
        // Band center Y position, wrapping around
        double bandY = minY - waveWidth / 2.0
            + ((timeSeconds * cascadeSpeed) % totalTravel);

        // Base hue shifts slowly over time
        double baseHue = (timeSeconds * hueShiftSpeed) % 1.0;
        if (baseHue < 0) baseHue += 1.0;

        double yRange = maxY - minY;
        if (yRange < 1.0) yRange = 1.0;

        // Process only one LED per mirror pair (the one with smaller index)
        for (int i = 0; i < n; i++) {
            int mirror = mirrorMap.getMirror(i);
            if (i > mirror) {
                // This LED's mirror partner has a smaller index — it was
                // already processed and copied to us, so skip.
                continue;
            }

            double y = ledY[i];
            double delta = Math.abs(y - bandY);

            // Handle wrap-around: band can wrap from bottom back to top
            double wrappedDelta = totalTravel - delta;
            if (wrappedDelta < delta) {
                delta = wrappedDelta;
            }

            if (delta > waveWidth / 2.0) {
                // Outside the band — leave dark (not added to map)
                continue;
            }

            // Half-cosine envelope: 1.0 at center, 0.0 at edges
            double envelope = 0.5 + 0.5 * Math.cos(Math.PI * delta / (waveWidth / 2.0));

            // Per-LED hue offset based on normalized Y position
            double normalizedY = (y - minY) / yRange;
            double hue = (baseHue + normalizedY * 0.15) % 1.0;

            float h = (float) hue;
            float s = (float) saturation;
            float b = (float) (brightness * envelope);

            Color color = Color.getHSBColor(h, s, b);
            pixels.put(i, color);

            // Enforce mirror symmetry: set partner to the same color
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
        return "Mirror Cascade";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
