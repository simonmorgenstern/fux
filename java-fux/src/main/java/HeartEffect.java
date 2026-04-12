import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Heart Effect — a 46-LED heart outline traced through the fox's center.
 *
 * The heart's perimeter is stored as a JSON array of LED indices. The
 * effect layers three animations:
 *
 *   1) Base glow: warm red baseline across the whole heart
 *   2) Rotating pulse: a bright highlight circulates around the outline,
 *      creating a "blood flow" feel
 *   3) Heartbeat throb: the entire heart pulses with a double-beat
 *      (lub-dub) rhythm, brighter at the bottom tip and top bumps
 *
 * The heart is naturally mirror-symmetric (every LED's mirror twin is
 * also in the heart), so no explicit mirror enforcement needed.
 */
public class HeartEffect implements Effect {
    private PixelCoordinates coords;
    private int fps;
    private double rotationPeriodSec;
    private double beatPeriodSec;
    private double baseGlow;
    private double brightness;

    private int[] perimeter;        // LED indices forming the heart outline
    private double centroidX, centroidY;
    private double[] distFromCenter; // per-LED distance from centroid (normalised)
    private double maxDist;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.rotationPeriodSec = params.has("rotation_period_sec")
            ? params.get("rotation_period_sec").getAsDouble() : 3.0;
        this.beatPeriodSec = params.has("beat_period_sec")
            ? params.get("beat_period_sec").getAsDouble() : 1.2;
        this.baseGlow = params.has("base_glow")
            ? params.get("base_glow").getAsDouble() : 0.06;
        this.brightness = params.has("brightness")
            ? params.get("brightness").getAsDouble() : 1.0;

        // Heart perimeter — configurable, default to the discovered 46-LED heart
        if (params.has("perimeter")) {
            JsonArray arr = params.getAsJsonArray("perimeter");
            perimeter = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) perimeter[i] = arr.get(i).getAsInt();
        } else {
            perimeter = new int[]{
                184, 213, 212, 211, 210, 209, 234, 235, 236, 237, 238,
                239, 240, 241, 242, 243, 246, 245, 244, 178, 177, 176,
                175, 171, 172, 173, 174, 151, 152, 153, 154, 127, 128,
                129, 130, 131, 132, 133, 134, 135, 136, 141, 142, 143,
                144, 145
            };
        }

        // Compute centroid
        double sx = 0, sy = 0;
        int count = 0;
        for (int id : perimeter) {
            PixelCoordinate pc = coords.get(id);
            if (pc != null) { sx += pc.getX(); sy += pc.getY(); count++; }
        }
        centroidX = count > 0 ? sx / count : 230;
        centroidY = count > 0 ? sy / count : 300;

        // Compute normalised distance from centroid for each LED
        int len = perimeter.length;
        distFromCenter = new double[len];
        maxDist = 0;
        for (int i = 0; i < len; i++) {
            PixelCoordinate pc = coords.get(perimeter[i]);
            if (pc != null) {
                double d = Math.sqrt((pc.getX() - centroidX) * (pc.getX() - centroidX)
                                   + (pc.getY() - centroidY) * (pc.getY() - centroidY));
                distFromCenter[i] = d;
                if (d > maxDist) maxDist = d;
            }
        }
        if (maxDist > 0) {
            for (int i = 0; i < len; i++) distFromCenter[i] /= maxDist;
        }

        System.out.println("HeartEffect initialized:");
        System.out.println("  Perimeter: " + len + " LEDs, centroid (" + (int) centroidX + "," + (int) centroidY + ")");
    }

    /** Double-beat heartbeat: lub at t~0.0-0.12, dub at t~0.20-0.35, rest after. */
    private double heartbeatEnvelope(double t) {
        if (t < 0.12) return Math.sin((t / 0.12) * Math.PI);
        if (t < 0.20) return Math.max(0, 0.15 * (1.0 - (t - 0.12) / 0.08));
        if (t < 0.35) return 0.65 * Math.sin(((t - 0.20) / 0.15) * Math.PI);
        if (t < 0.55) return Math.max(0, 0.08 * (1.0 - (t - 0.35) / 0.20));
        return 0;
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        int len = perimeter.length;
        if (len == 0) return pixels;

        // Heartbeat phase
        double beatPhase = (timeSeconds % beatPeriodSec) / beatPeriodSec;
        double beat = heartbeatEnvelope(beatPhase);

        for (int i = 0; i < len; i++) {
            double t = (double) i / (double) len; // 0..1 position around perimeter
            double tipFactor = distFromCenter[i];  // 0..1, higher at tips/bumps

            // 1) Base glow
            double base = baseGlow;

            // 2) Rotating pulse — two highlights 180 degrees apart for symmetry
            double pulsePos = (timeSeconds / rotationPeriodSec) % 1.0;
            double delta1 = t - pulsePos;
            delta1 = delta1 - Math.floor(delta1 + 0.5);
            double delta2 = t - (pulsePos + 0.5);
            delta2 = delta2 - Math.floor(delta2 + 0.5);
            double pulseWidth = 0.10;
            double pulse1 = Math.exp(-(delta1 * delta1) / (2 * pulseWidth * pulseWidth));
            double pulse2 = Math.exp(-(delta2 * delta2) / (2 * pulseWidth * pulseWidth));
            double pulse = Math.max(pulse1, pulse2) * 0.5;

            // 3) Heartbeat throb — whole heart pulses, tips glow brighter
            double throb = beat * (0.5 + 0.5 * tipFactor);

            double bri = Math.min(1.0, (base + pulse + throb * 0.8) * brightness);

            // Color: deep red base, whiter during beat peaks
            float sat = (float) (0.9 - 0.35 * throb); // less saturated during beat flash
            float hue = 0.0f; // red
            if (throb > 0.3) hue = 0.98f; // slightly pink-red during flash

            if (bri > 0.005) {
                pixels.put(perimeter[i], Color.getHSBColor(hue, Math.max(0.1f, sat), (float) bri));
            }
        }
        return pixels;
    }

    @Override public void dispose() {}
    @Override public String getName() { return "Heart"; }
    @Override public int getFPS() { return fps; }
}
