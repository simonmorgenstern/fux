import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class RippleBurstEffect implements Effect {
    private PixelCoordinates coords;
    private List<Ripple> ripples;
    private Random random;
    private double spawnRate;
    private double expandSpeed;
    private double ringWidth;
    private double maxRadius;
    private double brightness;
    private int fps;
    private double spawnAccumulator;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.ripples = new ArrayList<>();
        this.spawnAccumulator = 0.0;

        this.spawnRate = params.has("spawn_rate") ? params.get("spawn_rate").getAsDouble() : 3.0;
        this.expandSpeed = params.has("expand_speed") ? params.get("expand_speed").getAsDouble() : 150.0;
        this.ringWidth = params.has("ring_width") ? params.get("ring_width").getAsDouble() : 30.0;
        this.maxRadius = params.has("max_radius") ? params.get("max_radius").getAsDouble() : 250.0;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;

        System.out.println("RippleBurstEffect initialized:");
        System.out.println("  Spawn rate: " + spawnRate);
        System.out.println("  Expand speed: " + expandSpeed);
        System.out.println("  Ring width: " + ringWidth);
        System.out.println("  Max radius: " + maxRadius);
        System.out.println("  Brightness: " + brightness);
    }

    private void spawnRipple() {
        // Pick a random LED as the origin
        int ledIndex = random.nextInt(coords.getCount());
        PixelCoordinate origin = coords.get(ledIndex);
        float hue = random.nextFloat();
        ripples.add(new Ripple(origin.getX(), origin.getY(), hue));
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        // Spawn new ripples based on spawn rate
        spawnAccumulator += spawnRate / fps;
        while (spawnAccumulator >= 1.0) {
            spawnRipple();
            spawnAccumulator -= 1.0;
        }

        // Expand all ripples
        double radiusStep = expandSpeed / fps;
        for (Ripple ripple : ripples) {
            ripple.currentRadius += radiusStep;
        }

        // Remove expired ripples
        ripples.removeIf(r -> r.currentRadius > maxRadius);

        // Render each LED
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate led = coords.get(i);
            double totalR = 0;
            double totalG = 0;
            double totalB = 0;

            for (Ripple ripple : ripples) {
                double dx = led.getX() - ripple.originX;
                double dy = led.getY() - ripple.originY;
                double dist = Math.sqrt(dx * dx + dy * dy);

                // Check if LED is within the ring
                double halfWidth = ringWidth / 2.0;
                double distFromRing = Math.abs(dist - ripple.currentRadius);

                if (distFromRing <= halfWidth) {
                    // Cosine-shaped intensity within the ring
                    double ringIntensity = 0.5 * (1.0 + Math.cos(Math.PI * distFromRing / halfWidth));

                    // Fade as ripple expands
                    double ageFade = 1.0 - (ripple.currentRadius / maxRadius);

                    double intensity = ringIntensity * ageFade * brightness;

                    // Get color from HSB
                    Color rippleColor = Color.getHSBColor(ripple.hue, 1.0f, 1.0f);

                    totalR += rippleColor.getRed() * intensity;
                    totalG += rippleColor.getGreen() * intensity;
                    totalB += rippleColor.getBlue() * intensity;
                }
            }

            if (totalR > 0 || totalG > 0 || totalB > 0) {
                int r = Math.min(255, (int) totalR);
                int g = Math.min(255, (int) totalG);
                int b = Math.min(255, (int) totalB);
                pixels.put(i, new Color(r, g, b));
            }
        }

        return pixels;
    }

    @Override
    public void dispose() {
        ripples.clear();
    }

    @Override
    public String getName() {
        return "Ripple Burst";
    }

    @Override
    public int getFPS() {
        return fps;
    }

    private static class Ripple {
        double originX;
        double originY;
        double currentRadius;
        float hue;

        Ripple(double originX, double originY, float hue) {
            this.originX = originX;
            this.originY = originY;
            this.currentRadius = 0.0;
            this.hue = hue;
        }
    }
}
