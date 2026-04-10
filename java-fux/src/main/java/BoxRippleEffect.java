import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Box Ripple — a wavefront expands outward from the fox's vertical centerline.
 * Each box's brightness depends on how far the wave is from its centroid in
 * horizontal distance from the midline, so a left-side box and its right-side
 * mirror always light up at the exact same instant.
 *
 * Multiple wavefronts can be in flight at once, each carrying its own color.
 * When a wave reaches the outermost box it wraps and starts again from the
 * center, giving a continuous breathing-outward feel.
 */
public class BoxRippleEffect implements Effect {
    private PixelCoordinates coords;
    private LEDBoxTopology topology;
    private int fps;
    private double waveSpeedPxPerSec;
    private double waveWidthPx;
    private double spawnIntervalSec;
    private double brightness;

    private double timeSinceSpawnSec;
    private java.util.List<Wave> waves;
    private double maxDistFromMidline;
    private java.util.Random random;

    private static final Color[] PALETTE = {
        new Color(255, 120,  60),  // warm
        new Color(255, 220,  80),  // gold
        new Color( 80, 255, 200),  // teal
        new Color( 60, 180, 255),  // sky
        new Color(180,  80, 255),  // violet
        new Color(255,  80, 180),  // pink
    };

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.waveSpeedPxPerSec = params.has("wave_speed_px_per_sec")
            ? params.get("wave_speed_px_per_sec").getAsDouble() : 80.0;
        this.waveWidthPx = params.has("wave_width_px")
            ? params.get("wave_width_px").getAsDouble() : 60.0;
        this.spawnIntervalSec = params.has("spawn_interval_sec")
            ? params.get("spawn_interval_sec").getAsDouble() : 1.6;
        this.brightness = params.has("brightness")
            ? params.get("brightness").getAsDouble() : 1.0;

        this.timeSinceSpawnSec = Double.MAX_VALUE; // spawn immediately
        this.waves = new java.util.ArrayList<>();
        this.random = new java.util.Random();

        this.topology = new LEDBoxTopology();
        this.topology.load();

        double maxDist = 1.0;
        for (LEDBoxTopology.Box b : topology.getBoxes()) {
            double d = Math.abs(b.centroidX - topology.getMidlineX());
            if (d > maxDist) maxDist = d;
        }
        this.maxDistFromMidline = maxDist;

        System.out.println("BoxRippleEffect initialized:");
        System.out.println("  Boxes: " + topology.boxCount() + ", max distance from midline: " + maxDistFromMidline);
        System.out.println("  Wave speed: " + waveSpeedPxPerSec + " px/s, width: " + waveWidthPx + " px");
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        double dt = 1.0 / fps;
        timeSinceSpawnSec += dt;

        if (timeSinceSpawnSec >= spawnIntervalSec) {
            waves.add(new Wave(PALETTE[random.nextInt(PALETTE.length)]));
            timeSinceSpawnSec = 0;
        }
        for (Wave w : waves) w.distance += waveSpeedPxPerSec * dt;
        // A wave dies once it has fully passed the outermost box plus one wavelength
        waves.removeIf(w -> w.distance > maxDistFromMidline + waveWidthPx);

        int n = coords.getCount();
        double[] r = new double[n];
        double[] g = new double[n];
        double[] b = new double[n];

        List<LEDBoxTopology.Box> boxes = topology.getBoxes();
        double mid = topology.getMidlineX();

        for (LEDBoxTopology.Box box : boxes) {
            double dist = Math.abs(box.centroidX - mid);
            for (Wave w : waves) {
                double delta = Math.abs(w.distance - dist);
                if (delta > waveWidthPx) continue;
                // Smooth half-cosine falloff: 1 at delta=0, 0 at delta=waveWidthPx
                double env = 0.5 + 0.5 * Math.cos(Math.PI * delta / waveWidthPx);
                env *= brightness;
                double cr = w.color.getRed()   * env;
                double cg = w.color.getGreen() * env;
                double cb = w.color.getBlue()  * env;
                for (int led : box.perimeter) {
                    r[led] += cr;
                    g[led] += cg;
                    b[led] += cb;
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
        if (waves != null) waves.clear();
    }

    @Override public String getName() { return "Box Ripple"; }
    @Override public int getFPS() { return fps; }

    private static class Wave {
        final Color color;
        double distance; // current radial distance from midline
        Wave(Color color) { this.color = color; this.distance = 0; }
    }
}
