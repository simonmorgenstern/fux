import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Center Heartbeat — the fox's visual center glows with a double-beat
 * heartbeat rhythm (lub-dub) that propagates outward through the wiring
 * graph.  LEDs close to the center pulse brightest, with the glow
 * attenuating and arriving later at the edges.
 *
 * BFS graph distance from the center LEDs determines both the delay
 * and the brightness falloff, so the pulse follows the fox's shape
 * naturally.  Mirror symmetry enforced via LEDMirrorMap.
 *
 * In MUSIC mode {@link EffectEngine} installs the live {@link BeatClock} and
 * the cycle length becomes {@code beats_per_cycle} beats of the track instead
 * of {@code beat_period_sec}; propagation stays in real seconds so the wave
 * front still travels at the same speed.  With no clock the original timing is
 * kept, so queue, idle and preview renders are unchanged.
 */
public class CenterHeartbeatEffect implements Effect, BeatAware {
    private PixelCoordinates coords;
    private LEDMirrorMap mirrorMap;
    private int fps;
    private double beatPeriodSec;       // one full lub-dub cycle
    private double propagationSpeed;    // hops per second
    private double minBrightness;
    private double maxBrightness;
    private float hue;
    private float saturation;
    private double beatsPerCycle;
    private double propagationBeats;    // beats the front takes to reach the outermost LED
    private BeatSource beat;            // null unless music mode installed a clock

    private int[] dist;                 // BFS distance from center
    private int maxDist;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.beatPeriodSec = params.has("beat_period_sec")
            ? params.get("beat_period_sec").getAsDouble() : 1.2;
        this.propagationSpeed = params.has("propagation_speed")
            ? params.get("propagation_speed").getAsDouble() : 25.0;
        this.minBrightness = params.has("min_brightness")
            ? params.get("min_brightness").getAsDouble() : 0.03;
        this.maxBrightness = params.has("max_brightness")
            ? params.get("max_brightness").getAsDouble() : 1.0;
        this.hue = params.has("hue")
            ? params.get("hue").getAsFloat() : 0.0f; // red
        this.saturation = params.has("saturation")
            ? params.get("saturation").getAsFloat() : 0.9f;
        this.beatsPerCycle = params.has("beats_per_cycle")
            ? Math.max(0.25, params.get("beats_per_cycle").getAsDouble()) : 1.0;
        this.propagationBeats = params.has("propagation_beats")
            ? Math.max(0.0, params.get("propagation_beats").getAsDouble()) : 0.35;

        int[] centerLeds;
        if (params.has("center_leds")) {
            JsonArray arr = params.getAsJsonArray("center_leds");
            centerLeds = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) centerLeds[i] = arr.get(i).getAsInt();
        } else {
            centerLeds = new int[]{145, 146, 183, 184, 213};
        }

        this.mirrorMap = new LEDMirrorMap();
        this.mirrorMap.load();

        LEDNeighborGraph graph = new LEDNeighborGraph(coords);
        graph.build(0, 50, 8);
        bfsFromCenter(graph, centerLeds, coords.getCount());

        System.out.println("CenterHeartbeatEffect initialized:");
        System.out.println("  Center LEDs: " + centerLeds.length + ", max BFS dist: " + maxDist);
        System.out.println("  Beat period: " + beatPeriodSec + "s, propagation: " + propagationSpeed + " hops/s");
    }

    private void bfsFromCenter(LEDNeighborGraph graph, int[] seeds, int n) {
        dist = new int[n];
        java.util.Arrays.fill(dist, -1);
        Queue<Integer> queue = new LinkedList<>();
        for (int s : seeds) {
            if (s >= 0 && s < n) { dist[s] = 0; queue.add(s); }
        }
        maxDist = 0;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v : graph.getNeighbors(u)) {
                if (v >= 0 && v < n && dist[v] < 0) {
                    dist[v] = dist[u] + 1;
                    if (dist[v] > maxDist) maxDist = dist[v];
                    queue.add(v);
                }
            }
        }
    }

    /**
     * Double-beat heartbeat envelope:
     *   t in [0,1) within one beat period.
     *   lub  at t ~ 0.0-0.15  (sharp spike)
     *   dub  at t ~ 0.22-0.35 (slightly softer)
     *   rest at t ~ 0.35-1.0
     */
    private double heartbeatEnvelope(double t) {
        // Lub: strong beat
        if (t < 0.12) {
            double p = t / 0.12;
            return Math.sin(p * Math.PI);
        }
        // Gap between beats
        if (t < 0.20) {
            double p = (t - 0.12) / 0.08;
            return Math.max(0, 0.15 * (1 - p));
        }
        // Dub: softer second beat
        if (t < 0.35) {
            double p = (t - 0.20) / 0.15;
            return 0.65 * Math.sin(p * Math.PI);
        }
        // Rest: gentle decay
        if (t < 0.55) {
            double p = (t - 0.35) / 0.20;
            return Math.max(0, 0.08 * (1 - p));
        }
        return 0;
    }

    @Override
    public void setBeatSource(BeatSource source) {
        if (source != null) {
            this.beat = source;
        }
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        int n = coords.getCount();
        float[] bri = new float[n];

        // On a live clock, run off the track's own timeline: the cycle spans
        // beats_per_cycle beats, and "now" is the interpolated beat position
        // expressed in seconds so the propagation delay keeps its units.
        double period = beatPeriodSec;
        double now = timeSeconds;
        // Travel time from the center to the outermost LED. On a live clock it
        // is a fraction of a beat rather than a fixed hops/second: at a fast
        // tempo a fixed speed would still be crossing the fox when the next
        // beat lands, and the pulse would smear into a flat glow.
        double edgeDelay = maxDist / propagationSpeed;
        if (beat != null) {
            double secondsPerBeat = 60.0 / beat.getBpm();
            period = secondsPerBeat * beatsPerCycle;
            now = beat.getBeatPosition() * secondsPerBeat;
            edgeDelay = secondsPerBeat * propagationBeats;
        }

        for (int i = 0; i < n; i++) {
            if (dist[i] < 0) continue;

            // Delay: the pulse arrives later at distant LEDs
            double delay = ((double) dist[i] / Math.max(1, maxDist)) * edgeDelay;
            double localTime = now - delay;
            if (localTime < 0) localTime += period * Math.ceil(-localTime / period);
            double phase = (localTime % period) / period;

            double env = heartbeatEnvelope(phase);

            // Distance attenuation: center is full brightness, edges dimmer
            double distAtten = 1.0 - 0.6 * ((double) dist[i] / (double) Math.max(1, maxDist));

            bri[i] = (float) (minBrightness + (maxBrightness - minBrightness) * env * distAtten);
        }

        // Mirror enforcement: take max of the pair
        for (int i = 0; i < n; i++) {
            int m = mirrorMap.getMirror(i);
            if (m != i && m >= 0 && m < n) {
                float mx = Math.max(bri[i], bri[m]);
                bri[i] = mx;
                bri[m] = mx;
            }
        }

        Map<Integer, Color> pixels = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (bri[i] > 0.001f) {
                // Slight hue shift: redder at center, towards rose at edges
                float localHue = hue + 0.03f * ((float) dist[i] / (float) Math.max(1, maxDist));
                pixels.put(i, Color.getHSBColor(localHue % 1.0f, saturation, bri[i]));
            }
        }
        return pixels;
    }

    @Override public void dispose() {}
    @Override public String getName() { return "Center Heartbeat"; }
    @Override public int getFPS() { return fps; }
}
