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

    // Ambient flicker state
    private int[] flickerLEDs;
    private double[] flickerTimers;
    private static final int FLICKER_COUNT = 5;

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

        // Build neighbor graph
        double minDist = params.has("min_neighbor_distance") ?
            params.get("min_neighbor_distance").getAsDouble() : 10.0;
        double maxDist = params.has("max_neighbor_distance") ?
            params.get("max_neighbor_distance").getAsDouble() : 30.0;
        int maxNeighbors = params.has("max_neighbors") ?
            params.get("max_neighbors").getAsInt() : 6;

        this.graph = new LEDNeighborGraph(coords);
        this.graph.build(minDist, maxDist, maxNeighbors);

        this.activeBolts = new ArrayList<>();
        this.nextStrikeTime = randomInterval();

        // Initialize ambient flicker LEDs
        this.flickerLEDs = new int[FLICKER_COUNT];
        this.flickerTimers = new double[FLICKER_COUNT];
        for (int i = 0; i < FLICKER_COUNT; i++) {
            flickerLEDs[i] = random.nextInt(coords.getCount());
            flickerTimers[i] = random.nextDouble() * 2.0;
        }

        System.out.println("LightningEffect initialized:");
        System.out.println("  Bolt length: " + boltLength);
        System.out.println("  Branch probability: " + branchProb);
        System.out.println("  Strike interval: " + minInterval + "-" + maxInterval + "s");
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        // Paint ambient background on all LEDs
        for (int i = 0; i < coords.getCount(); i++) {
            pixels.put(i, AMBIENT);
        }

        // Between-strike flickers
        renderAmbientFlickers(pixels, timeSeconds);

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

    private void renderAmbientFlickers(Map<Integer, Color> pixels, double timeSeconds) {
        for (int i = 0; i < FLICKER_COUNT; i++) {
            if (timeSeconds >= flickerTimers[i]) {
                // Brief dim flicker
                double flickerPhase = (timeSeconds - flickerTimers[i]) * 10.0;
                if (flickerPhase < 1.0) {
                    int b = (int) (30 * (1.0 - flickerPhase) * brightness);
                    pixels.put(flickerLEDs[i], new Color(
                        clamp((int)(b * 0.4)),
                        clamp((int)(b * 0.3)),
                        clamp(b)
                    ));
                } else {
                    // Reset flicker
                    flickerLEDs[i] = random.nextInt(coords.getCount());
                    flickerTimers[i] = timeSeconds + 0.3 + random.nextDouble() * 1.5;
                }
            }
        }
    }

    private Bolt generateBolt(double strikeTime) {
        int startLED = random.nextInt(coords.getCount());
        int targetLength = boltLength + random.nextInt(11) - 5; // boltLength +/- 5
        targetLength = Math.max(15, Math.min(40, targetLength));

        Set<Integer> visited = new HashSet<>();
        List<Integer> mainPath = new ArrayList<>();
        List<List<Integer>> branches = new ArrayList<>();

        // Random walk for main bolt
        walkPath(startLED, targetLength, visited, mainPath);

        // Generate branches at junction nodes
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

        // Determine reflicker frames (age 4 or 6)
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

        // Determine color and brightness based on bolt age
        Color boltColor;
        double intensityMult;

        if (boltAge <= 1 || isReflicker) {
            // Bright white flash
            boltColor = new Color(255, 255, 255);
            intensityMult = 1.0;
        } else if (boltAge <= 4) {
            // Electric blue
            boltColor = new Color(100, 100, 255);
            intensityMult = 0.7;
        } else if (boltAge <= 8) {
            // Purple fade out
            double fade = 1.0 - (boltAge - 5) / 4.0;
            boltColor = new Color(80, 40, 200);
            intensityMult = 0.5 * fade;
        } else {
            // Afterglow on some LEDs
            boltColor = new Color(40, 20, 100);
            double fade = 1.0 - (boltAge - 9) / 7.0;
            intensityMult = 0.2 * Math.max(0, fade);
        }

        // Render main path
        for (int led : bolt.mainPath) {
            if (boltAge > 8 && random.nextDouble() > 0.4) continue; // Sparse afterglow
            applyBoltColor(pixels, led, boltColor, intensityMult * brightness);
        }

        // Render branches (dimmer)
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
            // Additive blend, taking the brighter value
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
}
