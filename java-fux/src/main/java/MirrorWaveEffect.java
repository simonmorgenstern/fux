import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mirror Wave — horizontal light waves radiate from the fox's vertical
 * centerline outward.  Mirror LED pairs light up in perfect lockstep
 * because each LED's brightness depends solely on its horizontal distance
 * from the midline, which is identical for both halves of a pair.
 *
 * Multiple waves can be in flight simultaneously, each carrying a random
 * warm-palette color and leaving a fading trail behind.
 */
public class MirrorWaveEffect implements Effect {

    private PixelCoordinates coords;
    private LEDMirrorMap mirrorMap;
    private int fps;
    private double waveSpeed;
    private double waveWidth;
    private double spawnIntervalSec;
    private double brightness;

    private double timeSinceSpawnSec;
    private List<Wave> waves;
    private double maxHalfWidth;
    private Random random;

    /** Per-LED distance from midline, cached once at init. */
    private double[] distFromMidline;

    private static final Color[] WARM_PALETTE = {
        new Color(255, 100,  30),  // deep orange
        new Color(255, 180,  40),  // amber
        new Color(255,  60,  80),  // coral
        new Color(255, 220, 100),  // gold
        new Color(220,  50, 120),  // magenta-rose
        new Color(255, 140,  60),  // tangerine
        new Color(200,  80, 200),  // warm violet
        new Color(255,  60, 160),  // hot pink
    };

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.waveSpeed = params.has("wave_speed")
            ? params.get("wave_speed").getAsDouble() : 120.0;
        this.waveWidth = params.has("wave_width")
            ? params.get("wave_width").getAsDouble() : 80.0;
        this.spawnIntervalSec = params.has("spawn_interval_sec")
            ? params.get("spawn_interval_sec").getAsDouble() : 1.4;
        this.brightness = params.has("brightness")
            ? params.get("brightness").getAsDouble() : 1.0;

        this.timeSinceSpawnSec = Double.MAX_VALUE; // spawn immediately on first frame
        this.waves = new ArrayList<Wave>();
        this.random = new Random();

        this.mirrorMap = new LEDMirrorMap();
        this.mirrorMap.load();

        this.maxHalfWidth = mirrorMap.computeMaxHalfWidth(coords);
        if (maxHalfWidth < 1.0) maxHalfWidth = 1.0;

        // Cache per-LED horizontal distance from midline
        int n = coords.getCount();
        this.distFromMidline = new double[n];
        for (int i = 0; i < n; i++) {
            distFromMidline[i] = mirrorMap.getDistanceFromMidline(i, coords);
        }

        System.out.println("MirrorWaveEffect initialized:");
        System.out.println("  LEDs: " + n + ", maxHalfWidth: " + maxHalfWidth);
        System.out.println("  Wave speed: " + waveSpeed + " px/s, width: " + waveWidth + " px");
        System.out.println("  Spawn interval: " + spawnIntervalSec + " s");
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        double dt = 1.0 / fps;
        timeSinceSpawnSec += dt;

        // Spawn a new wave at the midline when interval elapses
        if (timeSinceSpawnSec >= spawnIntervalSec) {
            waves.add(new Wave(WARM_PALETTE[random.nextInt(WARM_PALETTE.length)]));
            timeSinceSpawnSec = 0;
        }

        // Advance all waves outward
        for (Wave w : waves) {
            w.radius += waveSpeed * dt;
        }

        // Remove waves that have fully passed beyond all LEDs plus trail width
        Iterator<Wave> it = waves.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            if (w.radius > maxHalfWidth + waveWidth) {
                it.remove();
            }
        }

        int n = coords.getCount();
        double[] rAcc = new double[n];
        double[] gAcc = new double[n];
        double[] bAcc = new double[n];

        // For each LED, accumulate contributions from all active waves
        for (int i = 0; i < n; i++) {
            double dist = distFromMidline[i];

            for (Wave w : waves) {
                double delta = Math.abs(w.radius - dist);
                if (delta > waveWidth) continue;

                // Half-cosine envelope: 1.0 at delta=0, 0.0 at delta=waveWidth
                double envelope = 0.5 + 0.5 * Math.cos(Math.PI * delta / waveWidth);
                envelope *= brightness;

                rAcc[i] += w.color.getRed()   * envelope;
                gAcc[i] += w.color.getGreen() * envelope;
                bAcc[i] += w.color.getBlue()  * envelope;
            }
        }

        Map<Integer, Color> pixels = new HashMap<Integer, Color>();
        for (int i = 0; i < n; i++) {
            if (rAcc[i] > 0 || gAcc[i] > 0 || bAcc[i] > 0) {
                pixels.put(i, new Color(
                    Math.min(255, (int) rAcc[i]),
                    Math.min(255, (int) gAcc[i]),
                    Math.min(255, (int) bAcc[i])
                ));
            }
        }
        return pixels;
    }

    @Override
    public void dispose() {
        if (waves != null) waves.clear();
    }

    @Override public String getName() { return "Mirror Wave"; }
    @Override public int getFPS() { return fps; }

    private static class Wave {
        final Color color;
        double radius; // current distance from midline (expands outward over time)

        Wave(Color color) {
            this.color = color;
            this.radius = 0;
        }
    }
}
