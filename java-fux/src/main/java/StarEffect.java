import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Star Effect — a dedicated effect for star-shaped box #135 (configurable).
 *
 * The effect auto-detects the star's tips (local maxima of distance from
 * centroid along the perimeter) and runs three layered animations:
 *
 *   1) Base glow: all perimeter LEDs at a dim warm baseline
 *   2) Rotating beam: a bright highlight sweeps around the perimeter,
 *      naturally flaring at tips (which are farther from center)
 *   3) Tip twinkle: each tip sequentially pulses to full brightness
 *      in a round-robin, creating a classic "star twinkle" pattern
 *
 * The box is self-symmetric (on the midline), so no mirror handling needed.
 */
public class StarEffect implements Effect {
    private PixelCoordinates coords;
    private int fps;
    private double rotationPeriodSec;
    private double twinklePeriodSec;
    private double baseGlow;
    private double brightness;
    private float hue;
    private float saturation;
    private int boxId;

    private List<Integer> perimeter;    // LED indices in order
    private double centroidX, centroidY;
    private double[] distFromCenter;    // per-perimeter-LED distance from centroid
    private double maxDist;
    private List<Integer> tipIndices;   // indices INTO perimeter[] that are tips

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.rotationPeriodSec = params.has("rotation_period_sec")
            ? params.get("rotation_period_sec").getAsDouble() : 4.0;
        this.twinklePeriodSec = params.has("twinkle_period_sec")
            ? params.get("twinkle_period_sec").getAsDouble() : 0.8;
        this.baseGlow = params.has("base_glow")
            ? params.get("base_glow").getAsDouble() : 0.08;
        this.brightness = params.has("brightness")
            ? params.get("brightness").getAsDouble() : 1.0;
        this.hue = params.has("hue")
            ? params.get("hue").getAsFloat() : 0.12f; // warm gold
        this.saturation = params.has("saturation")
            ? params.get("saturation").getAsFloat() : 0.8f;
        this.boxId = params.has("box_id")
            ? params.get("box_id").getAsInt() : 135;

        // Load box topology to find the target box
        LEDBoxTopology topology = new LEDBoxTopology();
        topology.load();

        LEDBoxTopology.Box box = null;
        for (LEDBoxTopology.Box b : topology.getBoxes()) {
            if (b.id == boxId) { box = b; break; }
        }

        if (box == null) {
            System.err.println("StarEffect: box #" + boxId + " not found, falling back to empty");
            perimeter = new ArrayList<>();
            tipIndices = new ArrayList<>();
            distFromCenter = new double[0];
            return;
        }

        this.perimeter = box.perimeter;
        this.centroidX = box.centroidX;
        this.centroidY = box.centroidY;

        // Compute distance from centroid for each perimeter LED
        int len = perimeter.size();
        distFromCenter = new double[len];
        maxDist = 0;
        for (int i = 0; i < len; i++) {
            PixelCoordinate pc = coords.get(perimeter.get(i));
            if (pc != null) {
                double d = Math.sqrt((pc.getX() - centroidX) * (pc.getX() - centroidX)
                                   + (pc.getY() - centroidY) * (pc.getY() - centroidY));
                distFromCenter[i] = d;
                if (d > maxDist) maxDist = d;
            }
        }

        // Find tips: local maxima of distance with a minimum threshold
        double tipThreshold = maxDist * 0.55;
        tipIndices = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            int prev = (i - 1 + len) % len;
            int next = (i + 1) % len;
            if (distFromCenter[i] > distFromCenter[prev]
                && distFromCenter[i] > distFromCenter[next]
                && distFromCenter[i] > tipThreshold) {
                tipIndices.add(i);
            }
        }

        System.out.println("StarEffect initialized on box #" + boxId + ":");
        System.out.println("  Perimeter: " + len + " LEDs, " + tipIndices.size() + " tips detected");
        System.out.println("  Max dist from centroid: " + (int) maxDist + " px");
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        int len = perimeter.size();
        if (len == 0) return pixels;

        int tipCount = tipIndices.size();

        for (int i = 0; i < len; i++) {
            double t = (double) i / (double) len; // 0..1 position around perimeter

            // 1) Base glow — slightly brighter at tips (distance-weighted)
            double tipFactor = maxDist > 0 ? distFromCenter[i] / maxDist : 0;
            double base = baseGlow + baseGlow * tipFactor * 0.5;

            // 2) Rotating beam — a smooth Gaussian-ish highlight sweeping around
            double beamPos = (timeSeconds / rotationPeriodSec) % 1.0;
            double beamDelta = t - beamPos;
            // Wrap to [-0.5, 0.5]
            beamDelta = beamDelta - Math.floor(beamDelta + 0.5);
            double beamWidth = 0.12; // fraction of perimeter
            double beam = Math.exp(-(beamDelta * beamDelta) / (2 * beamWidth * beamWidth));
            // Beam is brighter at tips
            beam *= 0.6 + 0.4 * tipFactor;

            // 3) Tip twinkle — one tip lights up per twinkle cycle
            double twinkle = 0;
            if (tipCount > 0) {
                double twinkleSlot = timeSeconds / twinklePeriodSec;
                int activeTip = ((int) twinkleSlot) % tipCount;
                double twinklePhase = twinkleSlot - Math.floor(twinkleSlot); // 0..1

                int activeTipPerimIdx = tipIndices.get(activeTip);
                // How far is this LED from the active tip (along perimeter)?
                int perimDist = Math.abs(i - activeTipPerimIdx);
                perimDist = Math.min(perimDist, len - perimDist);
                int twinkleRadius = 4; // LEDs around the tip that glow
                if (perimDist <= twinkleRadius) {
                    double spatial = 1.0 - (double) perimDist / (double) (twinkleRadius + 1);
                    // Sharp spike envelope: sin(pi * phase), peaks at 0.5
                    double temporal = Math.sin(Math.PI * twinklePhase);
                    twinkle = spatial * temporal * 0.9;
                }
            }

            double bri = Math.min(1.0, (base + beam * 0.7 + twinkle) * brightness);

            // Slight hue shift: tips are whiter (lower saturation), valleys are more saturated
            float localSat = (float) (saturation * (1.0 - 0.4 * twinkle));
            float localHue = hue;
            // The twinkle flash shifts towards white-gold
            if (twinkle > 0.3) {
                localHue = hue - 0.02f;
                if (localHue < 0) localHue += 1.0f;
            }

            if (bri > 0.005) {
                pixels.put(perimeter.get(i),
                    Color.getHSBColor(localHue, Math.max(0f, localSat), (float) bri));
            }
        }
        return pixels;
    }

    @Override public void dispose() {}
    @Override public String getName() { return "Star"; }
    @Override public int getFPS() { return fps; }
}
