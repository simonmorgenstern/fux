import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class LavaLampEffect implements Effect {
    private PixelCoordinates coords;
    private List<Blob> blobs;
    private Random random;
    private int blobCount;
    private double speed;
    private double blobRadius;
    private double brightness;
    private int fps;
    private double minY;
    private double maxY;
    private double minX;
    private double maxX;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.blobCount = params.has("blob_count") ? params.get("blob_count").getAsInt() : 5;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 1.5;
        this.blobRadius = params.has("blob_radius") ? params.get("blob_radius").getAsDouble() : 80;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;

        // Find coordinate ranges
        this.minY = Double.MAX_VALUE;
        this.maxY = Double.MIN_VALUE;
        this.minX = Double.MAX_VALUE;
        this.maxX = Double.MIN_VALUE;
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate coord = coords.get(i);
            minY = Math.min(minY, coord.getY());
            maxY = Math.max(maxY, coord.getY());
            minX = Math.min(minX, coord.getX());
            maxX = Math.max(maxX, coord.getX());
        }

        // Spawn initial blobs spread vertically
        this.blobs = new ArrayList<>();
        for (int i = 0; i < blobCount; i++) {
            blobs.add(spawnBlob(true));
        }

        // Spread blobs across the vertical range initially
        double yRange = maxY - minY;
        for (int i = 0; i < blobs.size(); i++) {
            blobs.get(i).y = minY + (yRange * i / blobCount) + random.nextDouble() * (yRange / blobCount);
        }

        System.out.println("LavaLampEffect initialized:");
        System.out.println("  Blob count: " + blobCount);
        System.out.println("  Speed: " + speed);
        System.out.println("  Blob radius: " + blobRadius);
        System.out.println("  Brightness: " + brightness);
        System.out.println("  Y range: " + minY + " - " + maxY);
        System.out.println("  X range: " + minX + " - " + maxX);
    }

    private Blob spawnBlob(boolean randomY) {
        double xCenter = (minX + maxX) / 2.0;
        double xRange = (maxX - minX) * 0.6;
        double x = xCenter + (random.nextDouble() - 0.5) * xRange;
        double y = randomY ? maxY + random.nextDouble() * blobRadius : maxY + blobRadius;
        double radius = blobRadius * (0.7 + random.nextDouble() * 0.6);
        double blobSpeed = speed * (0.6 + random.nextDouble() * 0.8);
        // Warm hues: 0-40 degrees (reds, oranges, yellows)
        float hue = random.nextFloat() * 40f / 360f;
        double phase = random.nextDouble() * Math.PI * 2;

        return new Blob(x, y, radius, blobSpeed, hue, phase);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        // Update blob positions
        for (int i = 0; i < blobs.size(); i++) {
            Blob blob = blobs.get(i);

            // Move upward (decrease Y)
            blob.y -= blob.speed;

            // Apply sine perturbation to X for wobble
            double wobble = Math.sin(timeSeconds * 1.5 + blob.phase) * 20.0;
            blob.currentX = blob.x + wobble;

            // Respawn at bottom when reaching top
            if (blob.y < minY - blobRadius) {
                blobs.set(i, spawnBlob(false));
            }
        }

        // Render each LED
        for (int j = 0; j < coords.getCount(); j++) {
            PixelCoordinate led = coords.get(j);
            double ledX = led.getX();
            double ledY = led.getY();

            double totalR = 0;
            double totalG = 0;
            double totalB = 0;

            // Check contribution from each blob
            for (Blob blob : blobs) {
                double dx = ledX - blob.currentX;
                double dy = ledY - blob.y;
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance < blob.radius) {
                    // Smooth falloff: 1 - (distance/radius)^2
                    double ratio = distance / blob.radius;
                    double intensity = 1.0 - ratio * ratio;
                    intensity *= brightness;

                    // Convert blob hue to RGB using HSB
                    Color blobColor = Color.getHSBColor(blob.hue, 0.9f, (float) intensity);
                    totalR += blobColor.getRed();
                    totalG += blobColor.getGreen();
                    totalB += blobColor.getBlue();
                }
            }

            if (totalR > 0 || totalG > 0 || totalB > 0) {
                int r = Math.min(255, (int) totalR);
                int g = Math.min(255, (int) totalG);
                int b = Math.min(255, (int) totalB);
                pixels.put(j, new Color(r, g, b));
            }
        }

        return pixels;
    }

    @Override
    public void dispose() {
        blobs.clear();
    }

    @Override
    public String getName() {
        return "LavaLamp";
    }

    @Override
    public int getFPS() {
        return fps;
    }

    private static class Blob {
        double x;
        double y;
        double currentX; // X with wobble applied
        double radius;
        double speed;
        float hue;
        double phase;

        Blob(double x, double y, double radius, double speed, float hue, double phase) {
            this.x = x;
            this.y = y;
            this.currentX = x;
            this.radius = radius;
            this.speed = speed;
            this.hue = hue;
            this.phase = phase;
        }
    }
}
