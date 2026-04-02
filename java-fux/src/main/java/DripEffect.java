import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class DripEffect implements Effect {
    private PixelCoordinates coords;
    private List<Drip> drips;
    private Random random;
    private int dripCount;
    private double gravity;
    private int trailLength;
    private double brightness;
    private int fps;
    private double maxY;
    private double minY;
    private List<Integer> topLedIndices;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;

        this.dripCount = params.has("drip_count") ? params.get("drip_count").getAsInt() : 6;
        this.gravity = params.has("gravity") ? params.get("gravity").getAsDouble() : 0.3;
        this.trailLength = params.has("trail_length") ? params.get("trail_length").getAsInt() : 25;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;

        // Find Y range by iterating through all coordinates
        this.minY = Double.MAX_VALUE;
        this.maxY = Double.MIN_VALUE;
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate coord = coords.get(i);
            minY = Math.min(minY, coord.getY());
            maxY = Math.max(maxY, coord.getY());
        }

        // Pre-compute top 20% LED indices (lowest Y values = top of fox)
        double yRange = maxY - minY;
        double topThreshold = minY + yRange * 0.2;
        this.topLedIndices = new ArrayList<>();
        for (int i = 0; i < coords.getCount(); i++) {
            if (coords.get(i).getY() <= topThreshold) {
                topLedIndices.add(i);
            }
        }

        // Spawn initial drips
        this.drips = new ArrayList<>();
        for (int i = 0; i < dripCount; i++) {
            drips.add(spawnDrip());
        }

        System.out.println("DripEffect initialized:");
        System.out.println("  Drip count: " + dripCount);
        System.out.println("  Gravity: " + gravity);
        System.out.println("  Trail length: " + trailLength);
        System.out.println("  Brightness: " + brightness);
        System.out.println("  Y range: " + minY + " - " + maxY);
        System.out.println("  Top LEDs (20%): " + topLedIndices.size());
    }

    private Drip spawnDrip() {
        // Pick a random LED from the top 20%
        int ledIndex = topLedIndices.get(random.nextInt(topLedIndices.size()));
        PixelCoordinate topCoord = coords.get(ledIndex);
        double x = topCoord.getX();
        double y = topCoord.getY();

        // Random HSB hue, saturation 0.9, full brightness
        float hue = random.nextFloat();
        Color color = Color.getHSBColor(hue, 0.9f, 1.0f);

        return new Drip(x, y, 0.0, color);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        // Update and render each drip
        for (int i = 0; i < drips.size(); i++) {
            Drip drip = drips.get(i);

            // Apply gravity: accelerate and move downward
            drip.velocity += gravity;
            drip.y += drip.velocity;

            // If past the bottom, respawn at top
            if (drip.y > maxY) {
                drips.set(i, spawnDrip());
                continue;
            }

            // Render drip trail onto LEDs
            for (int j = 0; j < coords.getCount(); j++) {
                PixelCoordinate led = coords.get(j);

                // Check horizontal proximity
                double dx = Math.abs(led.getX() - drip.x);
                if (dx > 25) continue;

                // Check if LED is in the trail zone: between (dripY - trailLength) and dripY
                double ledY = led.getY();
                if (ledY > drip.y || ledY < drip.y - trailLength) continue;

                // Trail intensity: strongest at drip head (dripY), fading linearly upward
                double distFromHead = drip.y - ledY;
                double trailIntensity = 1.0 - (distFromHead / trailLength);

                // Reduce intensity based on horizontal distance for narrow drip look
                double horizontalFade = 1.0 - (dx / 25.0);

                double intensity = trailIntensity * horizontalFade * brightness;

                if (intensity > 0.05) {
                    int r = Math.min(255, (int)(drip.color.getRed() * intensity));
                    int g = Math.min(255, (int)(drip.color.getGreen() * intensity));
                    int b = Math.min(255, (int)(drip.color.getBlue() * intensity));

                    Color currentColor = pixels.get(j);
                    if (currentColor == null) {
                        pixels.put(j, new Color(r, g, b));
                    } else {
                        // Additive blend for overlapping drips, cap at 255
                        int newR = Math.min(255, currentColor.getRed() + r);
                        int newG = Math.min(255, currentColor.getGreen() + g);
                        int newB = Math.min(255, currentColor.getBlue() + b);
                        pixels.put(j, new Color(newR, newG, newB));
                    }
                }
            }
        }

        return pixels;
    }

    @Override
    public void dispose() {
        drips.clear();
    }

    @Override
    public String getName() {
        return "Drip";
    }

    @Override
    public int getFPS() {
        return fps;
    }

    private static class Drip {
        double x;
        double y;
        double velocity;
        Color color;

        Drip(double x, double y, double velocity, Color color) {
            this.x = x;
            this.y = y;
            this.velocity = velocity;
            this.color = color;
        }
    }
}
