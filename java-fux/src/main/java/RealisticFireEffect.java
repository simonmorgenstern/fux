import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Realistic fire simulation using a vertical heat model.
 * Heat sources at the bottom rise upward with turbulence and cooling.
 * Uses smooth noise for organic flickering rather than pure randomness.
 */
public class RealisticFireEffect implements Effect {
    private PixelCoordinates coords;
    private int fps;
    private Random random;

    // Per-LED state
    private double[] heat;        // current heat 0.0–1.0
    private double[] heatTarget;  // target heat for smooth interpolation

    // Spatial info computed once
    private double minY, maxY;    // Y range of the installation
    private double minX, maxX;

    // Parameters
    private double intensity;
    private double cooling;
    private double sparking;
    private double turbulence;
    private double riseSpeed;

    // Simple noise state
    private double[] noisePhase;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;

        this.intensity = params.has("intensity") ? params.get("intensity").getAsDouble() : 1.0;
        double rawCooling = params.has("cooling") ? params.get("cooling").getAsDouble() : 55;
        this.cooling = rawCooling / 255.0;
        double rawSparking = params.has("sparking") ? params.get("sparking").getAsDouble() : 120;
        this.sparking = rawSparking / 255.0;
        this.turbulence = params.has("turbulence") ? params.get("turbulence").getAsDouble() : 0.6;
        this.riseSpeed = params.has("rise_speed") ? params.get("rise_speed").getAsDouble() : 0.8;

        int n = coords.getCount();
        heat = new double[n];
        heatTarget = new double[n];
        noisePhase = new double[n];

        // Compute Y range
        minY = Double.MAX_VALUE;
        maxY = -Double.MAX_VALUE;
        minX = Double.MAX_VALUE;
        maxX = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double y = coords.get(i).getY();
            double x = coords.get(i).getX();
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            // Random phase offset per LED for organic variation
            noisePhase[i] = random.nextDouble() * Math.PI * 2;
        }

        System.out.println("RealisticFireEffect initialized: " + n + " LEDs, Y range [" + minY + ", " + maxY + "]");
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        int n = coords.getCount();
        double dt = 1.0 / fps;
        double yRange = maxY - minY;
        if (yRange < 1) yRange = 1;

        // --- Step 1: Generate new spark heat at the bottom ---
        for (int i = 0; i < n; i++) {
            PixelCoordinate led = coords.get(i);
            // Normalize Y: 0 = bottom (high Y), 1 = top (low Y)
            double normalizedHeight = 1.0 - (led.getY() - minY) / yRange;

            // Ember zone: bottom 30% of the installation
            if (normalizedHeight < 0.3) {
                double emberStrength = 1.0 - (normalizedHeight / 0.3); // 1 at very bottom, 0 at 30%
                if (random.nextDouble() < sparking * emberStrength * 0.8) {
                    double sparkHeat = (0.8 + random.nextDouble() * 0.2) * intensity;
                    heatTarget[i] = Math.min(1.0, heatTarget[i] + sparkHeat);
                }
            }
        }

        // --- Step 2: Heat rises — transfer heat upward ---
        double[] newHeatTarget = new double[n];
        System.arraycopy(heatTarget, 0, newHeatTarget, 0, n);

        for (int i = 0; i < n; i++) {
            if (heatTarget[i] < 0.05) continue; // skip cold LEDs

            PixelCoordinate src = coords.get(i);

            // Find LEDs above this one (lower Y = higher position)
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                PixelCoordinate dst = coords.get(j);

                double dx = dst.getX() - src.getX();
                double dy = dst.getY() - src.getY();

                // Only propagate upward (negative dy = higher position)
                if (dy >= 0) continue;

                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist > 80) continue;

                // Stronger transfer for directly-above LEDs, weaker for diagonal
                double verticalFactor = Math.abs(dy) / (dist + 0.001);
                double proximity = 1.0 - (dist / 80.0);
                double transfer = heatTarget[i] * proximity * verticalFactor * riseSpeed * 0.3;

                newHeatTarget[j] = Math.min(1.0, newHeatTarget[j] + transfer);
            }
        }
        heatTarget = newHeatTarget;

        // --- Step 3: Cooling — more at the top, less at the bottom ---
        for (int i = 0; i < n; i++) {
            double normalizedHeight = 1.0 - (coords.get(i).getY() - minY) / yRange;

            // Cool more aggressively higher up (flames taper off)
            double heightCooling = cooling * (0.3 + normalizedHeight * 1.5);

            // Add turbulent noise to cooling for organic flicker
            double noise = Math.sin(timeSeconds * 3.0 + noisePhase[i]) * 0.5 + 0.5;
            double flickerCooling = heightCooling * (0.7 + noise * turbulence * 0.6);

            heatTarget[i] = Math.max(0, heatTarget[i] - flickerCooling * dt * 3.0);
        }

        // --- Step 4: Smooth interpolation from current heat toward target ---
        double smoothing = 0.15; // lower = smoother/slower transitions
        for (int i = 0; i < n; i++) {
            heat[i] += (heatTarget[i] - heat[i]) * smoothing;
            heat[i] = Math.max(0, Math.min(1.0, heat[i]));
        }

        // --- Step 5: Map heat to realistic fire colors ---
        for (int i = 0; i < n; i++) {
            pixels.put(i, heatToFireColor(heat[i]));
        }

        return pixels;
    }

    /**
     * Maps a heat value (0.0–1.0) to a realistic fire color.
     * 0.00–0.15: black to deep red (dark embers)
     * 0.15–0.40: deep red to bright red-orange (base of flame)
     * 0.40–0.65: orange to yellow (mid flame)
     * 0.65–0.85: yellow to bright yellow-white (flame tips)
     * 0.85–1.00: white-hot core
     */
    private Color heatToFireColor(double h) {
        if (h < 0.01) return Color.BLACK;

        int r, g, b;

        if (h < 0.15) {
            // Black → deep red
            double t = h / 0.15;
            r = (int)(t * 120);
            g = 0;
            b = 0;
        } else if (h < 0.40) {
            // Deep red → bright orange
            double t = (h - 0.15) / 0.25;
            r = 120 + (int)(t * 135); // 120→255
            g = (int)(t * 80);         // 0→80
            b = 0;
        } else if (h < 0.65) {
            // Orange → yellow
            double t = (h - 0.40) / 0.25;
            r = 255;
            g = 80 + (int)(t * 175);   // 80→255
            b = 0;
        } else if (h < 0.85) {
            // Yellow → bright yellow-white
            double t = (h - 0.65) / 0.20;
            r = 255;
            g = 255;
            b = (int)(t * 120);        // 0→120
        } else {
            // White-hot
            double t = (h - 0.85) / 0.15;
            r = 255;
            g = 255;
            b = 120 + (int)(t * 135);  // 120→255
        }

        return new Color(
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b))
        );
    }

    @Override
    public void dispose() {}

    @Override
    public String getName() {
        return "Realistic Fire";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
