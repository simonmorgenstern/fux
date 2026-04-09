import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class PulseNetworkEffect implements Effect {
    private PixelCoordinates coords;
    private LEDNeighborGraph graph;
    private Random random;
    private int fps;
    private int pulseIntervalFrames;
    private int trailLength;
    private int maxPulses;
    private double brightness;
    private List<Pulse> pulses;
    private long framesSinceLastSpawn;

    // Harmonious color palette for pulses
    private static final Color[] PALETTE = {
        new Color(255, 200, 50),   // golden
        new Color(0, 220, 255),    // cyan
        new Color(255, 50, 200),   // magenta
        new Color(100, 255, 100),  // green
        new Color(255, 120, 30),   // orange
        new Color(150, 80, 255),   // purple
        new Color(50, 255, 200),   // teal
        new Color(255, 80, 80),    // coral
    };

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.pulses = new ArrayList<>();
        this.framesSinceLastSpawn = 0;

        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.pulseIntervalFrames = params.has("pulse_interval_frames") ?
            params.get("pulse_interval_frames").getAsInt() : 20;
        this.trailLength = params.has("trail_length") ?
            params.get("trail_length").getAsInt() : 8;
        this.maxPulses = params.has("max_pulses") ?
            params.get("max_pulses").getAsInt() : 5;
        this.brightness = params.has("brightness") ?
            params.get("brightness").getAsDouble() : 1.0;

        // Build neighbor graph
        double minDist = params.has("min_neighbor_distance") ?
            params.get("min_neighbor_distance").getAsDouble() : 5.0;
        double maxDist = params.has("max_neighbor_distance") ?
            params.get("max_neighbor_distance").getAsDouble() : 30.0;
        int maxNeighbors = params.has("max_neighbors") ?
            params.get("max_neighbors").getAsInt() : 6;

        this.graph = new LEDNeighborGraph(coords);
        this.graph.build(minDist, maxDist, maxNeighbors);

        System.out.println("PulseNetworkEffect initialized:");
        System.out.println("  Graph from file: " + graph.isLoadedFromFile());
        System.out.println("  Pulse interval: " + pulseIntervalFrames + " frames");
        System.out.println("  Trail length: " + trailLength);
        System.out.println("  Max pulses: " + maxPulses);
        System.out.println("  Brightness: " + brightness);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        // Spawn new pulses at regular intervals
        framesSinceLastSpawn++;
        if (framesSinceLastSpawn >= pulseIntervalFrames && pulses.size() < maxPulses) {
            spawnPulse();
            framesSinceLastSpawn = 0;
        }

        // Advance all pulses by one BFS step
        for (Pulse pulse : pulses) {
            pulse.advance(graph);
        }

        // Remove dead pulses (empty frontier and trail fully faded)
        pulses.removeIf(Pulse::isDead);

        // Render: accumulate color contributions from all pulses
        double[] totalR = new double[coords.getCount()];
        double[] totalG = new double[coords.getCount()];
        double[] totalB = new double[coords.getCount()];

        for (Pulse pulse : pulses) {
            Color color = pulse.color;
            List<Set<Integer>> trail = pulse.trail;

            // trail[0] is the oldest layer, trail[size-1] is the current frontier
            int layers = trail.size();
            for (int layerIdx = 0; layerIdx < layers; layerIdx++) {
                // Brightness decreases for older layers
                double layerBrightness = (double)(layerIdx + 1) / layers;
                // Apply a curve for smoother falloff
                layerBrightness = layerBrightness * layerBrightness;
                layerBrightness *= brightness;

                for (int led : trail.get(layerIdx)) {
                    totalR[led] += color.getRed() * layerBrightness;
                    totalG[led] += color.getGreen() * layerBrightness;
                    totalB[led] += color.getBlue() * layerBrightness;
                }
            }
        }

        // Build output map with additive blending (clamped to 255)
        Map<Integer, Color> pixels = new HashMap<>();
        for (int i = 0; i < coords.getCount(); i++) {
            if (totalR[i] > 0 || totalG[i] > 0 || totalB[i] > 0) {
                int r = Math.min(255, (int) totalR[i]);
                int g = Math.min(255, (int) totalG[i]);
                int b = Math.min(255, (int) totalB[i]);
                pixels.put(i, new Color(r, g, b));
            }
        }

        return pixels;
    }

    private void spawnPulse() {
        int origin = random.nextInt(coords.getCount());
        // Ensure the origin has neighbors so the pulse can actually travel
        int attempts = 0;
        while (graph.getNeighbors(origin).isEmpty() && attempts < 20) {
            origin = random.nextInt(coords.getCount());
            attempts++;
        }
        Color color = PALETTE[random.nextInt(PALETTE.length)];
        pulses.add(new Pulse(origin, color, trailLength));
    }

    @Override
    public void dispose() {
        pulses.clear();
    }

    @Override
    public String getName() {
        return "Pulse Network";
    }

    @Override
    public int getFPS() {
        return fps;
    }

    /**
     * A single pulse that travels outward through the graph via BFS.
     * Maintains a trail of recent frontier layers for the fade effect.
     */
    private static class Pulse {
        final Color color;
        final int maxTrailLength;
        Set<Integer> frontier;
        Set<Integer> visited;
        /** Trail of frontier layers, oldest first. Last element is the current frontier. */
        List<Set<Integer>> trail;
        boolean frontierExhausted;

        Pulse(int origin, Color color, int maxTrailLength) {
            this.color = color;
            this.maxTrailLength = maxTrailLength;
            this.frontier = new HashSet<>();
            this.frontier.add(origin);
            this.visited = new HashSet<>();
            this.visited.add(origin);
            this.trail = new ArrayList<>();
            this.trail.add(new HashSet<>(frontier));
            this.frontierExhausted = false;
        }

        /**
         * Advance the BFS frontier by one step.
         * Pulses naturally split at junctions (nodes with 3+ neighbors)
         * because BFS expands to ALL unvisited neighbors.
         */
        void advance(LEDNeighborGraph graph) {
            if (frontierExhausted) {
                // Frontier is gone, just let the trail fade out
                if (!trail.isEmpty()) {
                    trail.remove(0);
                }
                return;
            }

            // Expand frontier: collect all unvisited neighbors of current frontier
            Set<Integer> nextFrontier = new HashSet<>();
            for (int led : frontier) {
                for (int neighbor : graph.getNeighbors(led)) {
                    if (!visited.contains(neighbor)) {
                        nextFrontier.add(neighbor);
                    }
                }
            }

            // Mark new frontier as visited
            visited.addAll(nextFrontier);

            if (nextFrontier.isEmpty()) {
                // No more expansion possible
                frontierExhausted = true;
                // Start fading the trail
                if (!trail.isEmpty()) {
                    trail.remove(0);
                }
            } else {
                frontier = nextFrontier;
                trail.add(new HashSet<>(frontier));
                // Trim trail to max length
                while (trail.size() > maxTrailLength) {
                    trail.remove(0);
                }
            }
        }

        boolean isDead() {
            return frontierExhausted && trail.isEmpty();
        }
    }
}
