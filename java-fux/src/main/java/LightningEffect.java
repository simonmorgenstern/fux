import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class LightningEffect implements Effect {
    private PixelCoordinates coords;
    private LEDNeighborGraph graph;
    private Random random;
    private int fps;
    private double brightness;

    // Bolt generation parameters
    private double minInterval;
    private double maxInterval;
    private int boltLength;
    private double branchProb;

    // Active bolts
    private List<Bolt> activeBolts;
    private double nextStrikeTime;

    // Rain state
    private List<Raindrop> raindrops;
    private int rainCount;
    private double rainSpeedMin;
    private double rainSpeedMax;
    private double minY;
    private double maxY;

    // Background color
    private static final Color AMBIENT = new Color(5, 2, 10);

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;
        this.minInterval = params.has("min_interval") ? params.get("min_interval").getAsDouble() : 1.0;
        this.maxInterval = params.has("max_interval") ? params.get("max_interval").getAsDouble() : 3.5;
        this.boltLength = params.has("bolt_length") ? params.get("bolt_length").getAsInt() : 30;
        this.branchProb = params.has("branch_prob") ? params.get("branch_prob").getAsDouble() : 0.3;

        // Rain parameters
        this.rainCount = params.has("rain_count") ? params.get("rain_count").getAsInt() : 15;
        this.rainSpeedMin = params.has("rain_speed_min") ? params.get("rain_speed_min").getAsDouble() : 3.0;
        this.rainSpeedMax = params.has("rain_speed_max") ? params.get("rain_speed_max").getAsDouble() : 7.0;

        // Build neighbor graph for lightning bolts
        double minDist = params.has("min_neighbor_distance") ?
            params.get("min_neighbor_distance").getAsDouble() : 10.0;
        double maxDist = params.has("max_neighbor_distance") ?
            params.get("max_neighbor_distance").getAsDouble() : 30.0;
        int maxNeighbors = params.has("max_neighbors") ?
            params.get("max_neighbors").getAsInt() : 6;

        this.graph = new LEDNeighborGraph(coords);
        this.graph.build(minDist, maxDist, maxNeighbors);

        // Find Y range for rain
        this.minY = Double.MAX_VALUE;
        this.maxY = Double.MIN_VALUE;
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate coord = coords.get(i);
            minY = Math.min(minY, coord.getY());
            maxY = Math.max(maxY, coord.getY());
        }

        // Initialize bolts
        this.activeBolts = new ArrayList<>();
        this.nextStrikeTime = randomInterval();

        // Initialize raindrops spread across the Y range
        this.raindrops = new ArrayList<>();
        for (int i = 0; i < rainCount; i++) {
            Raindrop drop = spawnRaindrop();
            drop.y = minY + random.nextDouble() * (maxY - minY); // spread out initially
            raindrops.add(drop);
        }

        System.out.println("LightningEffect initialized:");
        System.out.println("  Bolt length: " + boltLength);
        System.out.println("  Branch probability: " + branchProb);
        System.out.println("  Strike interval: " + minInterval + "-" + maxInterval + "s");
        System.out.println("  Rain drops: " + rainCount);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        // Paint ambient background on all LEDs
        for (int i = 0; i < coords.getCount(); i++) {
            pixels.put(i, AMBIENT);
        }

        // Render rain
        renderRain(pixels);

        // Check if it's time for a new strike
        if (timeSeconds >= nextStrikeTime) {
            activeBolts.add(generateBolt(timeSeconds));
            nextStrikeTime = timeSeconds + randomInterval();
        }

        // Render and age active bolts
        List<Bolt> expired = new ArrayList<>();
        for (Bolt bolt : activeBolts) {
            int boltAge = (int) ((timeSeconds - bolt.strikeTime) * fps);
            if (boltAge > 15) {
                expired.add(bolt);
            } else {
                renderBolt(bolt, boltAge, pixels);
            }
        }
        activeBolts.removeAll(expired);

        return pixels;
    }

    // --- Rain ---

    private Raindrop spawnRaindrop() {
        PixelCoordinate randomCoord = coords.get(random.nextInt(coords.getCount()));
        double x = randomCoord.getX();
        double speed = rainSpeedMin + random.nextDouble() * (rainSpeedMax - rainSpeedMin);
        return new Raindrop(x, minY, speed);
    }

    private void renderRain(Map<Integer, Color> pixels) {
        for (int i = 0; i < raindrops.size(); i++) {
            Raindrop drop = raindrops.get(i);

            // Move raindrop down
            drop.y += drop.speed;

            // Respawn at top if past bottom
            if (drop.y > maxY) {
                raindrops.set(i, spawnRaindrop());
                continue;
            }

            // Light up LEDs near this raindrop
            for (int j = 0; j < coords.getCount(); j++) {
                PixelCoordinate led = coords.get(j);

                double dx = led.getX() - drop.x;
                if (Math.abs(dx) > 15) continue;

                double dy = led.getY() - drop.y;
                // Trail extends upward from head
                if (dy < -35 || dy > 3) continue;

                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance < 15) {
                    double trailPos = Math.abs(dy) / 35.0;
                    double intensity = (1.0 - trailPos) * brightness;
                    intensity *= Math.max(0, 1.0 - (Math.abs(dx) / 15.0));

                    if (intensity > 0.05) {
                        // Blue-ish rain color
                        int r = clamp((int) (10 * intensity));
                        int g = clamp((int) (30 * intensity));
                        int b = clamp((int) (80 * intensity));

                        Color existing = pixels.get(j);
                        if (existing != null) {
                            r = Math.min(255, existing.getRed() + r);
                            g = Math.min(255, existing.getGreen() + g);
                            b = Math.min(255, existing.getBlue() + b);
                        }
                        pixels.put(j, new Color(r, g, b));
                    }
                }
            }
        }
    }

    // --- Lightning ---

    private Bolt generateBolt(double strikeTime) {
        int startLED = random.nextInt(coords.getCount());
        int targetLength = boltLength + random.nextInt(11) - 5;
        targetLength = Math.max(15, Math.min(40, targetLength));

        Set<Integer> visited = new HashSet<>();
        List<Integer> mainPath = new ArrayList<>();
        List<List<Integer>> branches = new ArrayList<>();

        walkPath(startLED, targetLength, visited, mainPath);

        for (int i = 0; i < mainPath.size(); i++) {
            int led = mainPath.get(i);
            List<Integer> neighbors = graph.getNeighbors(led);
            if (neighbors.size() >= 3 && random.nextDouble() < branchProb) {
                List<Integer> branch = new ArrayList<>();
                int branchLength = 5 + random.nextInt(10);
                walkPath(led, branchLength, new HashSet<>(visited), branch);
                if (branch.size() > 1) {
                    branches.add(branch);
                    visited.addAll(branch);
                }
            }
        }

        Set<Integer> reflickerFrames = new HashSet<>();
        if (random.nextDouble() < 0.5) reflickerFrames.add(4);
        if (random.nextDouble() < 0.4) reflickerFrames.add(6);

        return new Bolt(mainPath, branches, strikeTime, reflickerFrames);
    }

    private void walkPath(int startLED, int maxSteps, Set<Integer> visited, List<Integer> path) {
        int current = startLED;
        path.add(current);
        visited.add(current);

        for (int step = 0; step < maxSteps; step++) {
            List<Integer> neighbors = graph.getNeighbors(current);
            List<Integer> unvisited = new ArrayList<>();
            for (int n : neighbors) {
                if (!visited.contains(n)) {
                    unvisited.add(n);
                }
            }
            if (unvisited.isEmpty()) break;

            int next = unvisited.get(random.nextInt(unvisited.size()));
            path.add(next);
            visited.add(next);
            current = next;
        }
    }

    private void renderBolt(Bolt bolt, int boltAge, Map<Integer, Color> pixels) {
        boolean isReflicker = bolt.reflickerFrames.contains(boltAge);

        Color boltColor;
        double intensityMult;

        if (boltAge <= 1 || isReflicker) {
            boltColor = new Color(255, 255, 255);
            intensityMult = 1.0;
        } else if (boltAge <= 4) {
            boltColor = new Color(100, 100, 255);
            intensityMult = 0.7;
        } else if (boltAge <= 8) {
            double fade = 1.0 - (boltAge - 5) / 4.0;
            boltColor = new Color(80, 40, 200);
            intensityMult = 0.5 * fade;
        } else {
            boltColor = new Color(40, 20, 100);
            double fade = 1.0 - (boltAge - 9) / 7.0;
            intensityMult = 0.2 * Math.max(0, fade);
        }

        for (int led : bolt.mainPath) {
            if (boltAge > 8 && random.nextDouble() > 0.4) continue;
            applyBoltColor(pixels, led, boltColor, intensityMult * brightness);
        }

        double branchDimming = 0.5;
        for (List<Integer> branch : bolt.branches) {
            for (int led : branch) {
                if (boltAge > 8 && random.nextDouble() > 0.3) continue;
                applyBoltColor(pixels, led, boltColor, intensityMult * branchDimming * brightness);
            }
        }
    }

    private void applyBoltColor(Map<Integer, Color> pixels, int led, Color color, double intensity) {
        int r = clamp((int) (color.getRed() * intensity));
        int g = clamp((int) (color.getGreen() * intensity));
        int b = clamp((int) (color.getBlue() * intensity));

        Color existing = pixels.get(led);
        if (existing != null) {
            r = Math.max(r, existing.getRed());
            g = Math.max(g, existing.getGreen());
            b = Math.max(b, existing.getBlue());
        }
        pixels.put(led, new Color(r, g, b));
    }

    private double randomInterval() {
        return minInterval + random.nextDouble() * (maxInterval - minInterval);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Override
    public void dispose() {
        activeBolts.clear();
        raindrops.clear();
    }

    @Override
    public String getName() {
        return "Lightning";
    }

    @Override
    public int getFPS() {
        return fps;
    }

    // --- Inner classes ---

    private static class Bolt {
        final List<Integer> mainPath;
        final List<List<Integer>> branches;
        final double strikeTime;
        final Set<Integer> reflickerFrames;

        Bolt(List<Integer> mainPath, List<List<Integer>> branches,
             double strikeTime, Set<Integer> reflickerFrames) {
            this.mainPath = mainPath;
            this.branches = branches;
            this.strikeTime = strikeTime;
            this.reflickerFrames = reflickerFrames;
        }
    }

    private static class Raindrop {
        double x;
        double y;
        double speed;

        Raindrop(double x, double y, double speed) {
            this.x = x;
            this.y = y;
            this.speed = speed;
        }
    }
}
