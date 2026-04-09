import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class FloodFillEffect implements Effect {
    private PixelCoordinates coords;
    private LEDNeighborGraph graph;
    private Random random;
    private int fps;
    private int numSources;
    private int spreadSpeed;
    private double brightness;
    private int totalLEDs;

    // Flood state
    private int[] ownerMap;        // which flood owns each LED (-1 = unclaimed)
    private int[] generationMap;   // at what generation each LED was claimed
    private boolean[] isBorder;    // true if LED borders a different flood
    private List<Queue<Integer>> frontiers; // BFS frontier per flood
    private Color[] floodColors;
    private int currentGeneration;
    private int claimedCount;

    // Cycle state
    private enum Phase { FILLING, HOLD, FADE_OUT }
    private Phase phase;
    private double phaseStartTime;
    private static final double HOLD_DURATION = 1.5;
    private static final double FADE_DURATION = 1.0;

    // Vivid palette
    private static final Color[] PALETTE = {
        new Color(255, 0, 80),    // hot pink
        new Color(0, 200, 255),   // cyan
        new Color(255, 180, 0),   // amber
        new Color(100, 255, 0),   // lime
        new Color(180, 0, 255),   // purple
        new Color(0, 255, 120),   // mint
        new Color(255, 60, 0),    // red-orange
        new Color(0, 100, 255),   // blue
    };

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 20;
        this.numSources = params.has("num_sources") ? params.get("num_sources").getAsInt() : 4;
        this.spreadSpeed = params.has("spread_speed") ? params.get("spread_speed").getAsInt() : 2;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;
        this.totalLEDs = coords.getCount();

        // Build neighbor graph
        double minDist = params.has("min_neighbor_distance") ?
            params.get("min_neighbor_distance").getAsDouble() : 10.0;
        double maxDist = params.has("max_neighbor_distance") ?
            params.get("max_neighbor_distance").getAsDouble() : 30.0;
        int maxNeighbors = params.has("max_neighbors") ?
            params.get("max_neighbors").getAsInt() : 6;

        this.graph = new LEDNeighborGraph(coords);
        this.graph.build(minDist, maxDist, maxNeighbors);

        startNewCycle();

        System.out.println("FloodFillEffect initialized:");
        System.out.println("  Sources: " + numSources);
        System.out.println("  Spread speed: " + spreadSpeed);
        System.out.println("  Graph from file: " + graph.isLoadedFromFile());
    }

    private void startNewCycle() {
        ownerMap = new int[totalLEDs];
        generationMap = new int[totalLEDs];
        isBorder = new boolean[totalLEDs];
        Arrays.fill(ownerMap, -1);
        Arrays.fill(generationMap, -1);

        frontiers = new ArrayList<>();
        floodColors = new Color[numSources];
        currentGeneration = 0;
        claimedCount = 0;
        phase = Phase.FILLING;
        phaseStartTime = -1;

        // Pick distinct colors from palette
        List<Integer> colorIndices = new ArrayList<>();
        for (int i = 0; i < PALETTE.length; i++) colorIndices.add(i);
        Collections.shuffle(colorIndices, random);

        // Pick seed LEDs spread apart using random selection
        List<Integer> seeds = pickSpreadSeeds(numSources);

        for (int i = 0; i < numSources; i++) {
            floodColors[i] = PALETTE[colorIndices.get(i % PALETTE.length)];
            int seed = seeds.get(i);
            ownerMap[seed] = i;
            generationMap[seed] = 0;
            claimedCount++;

            Queue<Integer> frontier = new LinkedList<>();
            frontier.add(seed);
            frontiers.add(frontier);
        }
    }

    /**
     * Pick N seed LEDs that are reasonably spread apart.
     * Uses iterative farthest-point sampling via BFS distances.
     */
    private List<Integer> pickSpreadSeeds(int count) {
        List<Integer> seeds = new ArrayList<>();
        // First seed is random
        seeds.add(random.nextInt(totalLEDs));

        for (int i = 1; i < count; i++) {
            // For each candidate, find min distance to existing seeds
            // Pick the candidate with max of those min distances
            int bestLED = -1;
            int bestMinDist = -1;

            // Sample a subset to keep it fast
            int sampleSize = Math.min(50, totalLEDs);
            List<Integer> candidates = new ArrayList<>();
            for (int c = 0; c < sampleSize; c++) {
                candidates.add(random.nextInt(totalLEDs));
            }

            // Precompute BFS from each existing seed
            int[][] seedDists = new int[seeds.size()][];
            for (int s = 0; s < seeds.size(); s++) {
                seedDists[s] = bfsDistances(seeds.get(s));
            }

            for (int cand : candidates) {
                if (seeds.contains(cand)) continue;
                int minDist = Integer.MAX_VALUE;
                for (int s = 0; s < seeds.size(); s++) {
                    if (seedDists[s][cand] < minDist) {
                        minDist = seedDists[s][cand];
                    }
                }
                if (minDist > bestMinDist) {
                    bestMinDist = minDist;
                    bestLED = cand;
                }
            }

            seeds.add(bestLED != -1 ? bestLED : random.nextInt(totalLEDs));
        }
        return seeds;
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        switch (phase) {
            case FILLING:
                expandFloods();
                updateBorders();
                if (claimedCount >= totalLEDs || allFrontiersEmpty()) {
                    phase = Phase.HOLD;
                    phaseStartTime = timeSeconds;
                }
                renderFillingFrame(pixels, timeSeconds);
                break;

            case HOLD:
                if (timeSeconds - phaseStartTime >= HOLD_DURATION) {
                    phase = Phase.FADE_OUT;
                    phaseStartTime = timeSeconds;
                }
                renderFillingFrame(pixels, timeSeconds);
                break;

            case FADE_OUT:
                double fadeProgress = (timeSeconds - phaseStartTime) / FADE_DURATION;
                if (fadeProgress >= 1.0) {
                    startNewCycle();
                    renderFillingFrame(pixels, timeSeconds);
                } else {
                    renderFadeOutFrame(pixels, timeSeconds, 1.0 - fadeProgress);
                }
                break;
        }

        return pixels;
    }

    private void expandFloods() {
        // Each flood expands 'spreadSpeed' LEDs from its frontier per frame
        for (int floodIdx = 0; floodIdx < numSources; floodIdx++) {
            Queue<Integer> frontier = frontiers.get(floodIdx);
            int expansions = 0;
            int frontierSize = frontier.size();

            // Process up to spreadSpeed LEDs from this flood's frontier
            Queue<Integer> nextFrontier = new LinkedList<>();
            while (!frontier.isEmpty() && expansions < spreadSpeed * Math.max(1, frontierSize / 3)) {
                int current = frontier.poll();
                List<Integer> neighbors = graph.getNeighbors(current);
                Collections.shuffle(neighbors, random);

                for (int neighbor : neighbors) {
                    if (ownerMap[neighbor] == -1) {
                        ownerMap[neighbor] = floodIdx;
                        generationMap[neighbor] = currentGeneration + 1;
                        claimedCount++;
                        nextFrontier.add(neighbor);
                        expansions++;
                    }
                }
            }
            // Re-add unprocessed items and new frontier
            while (!frontier.isEmpty()) {
                nextFrontier.add(frontier.poll());
            }
            frontiers.set(floodIdx, nextFrontier);
        }
        currentGeneration++;
    }

    private void updateBorders() {
        Arrays.fill(isBorder, false);
        for (int i = 0; i < totalLEDs; i++) {
            if (ownerMap[i] == -1) continue;
            for (int neighbor : graph.getNeighbors(i)) {
                if (ownerMap[neighbor] != -1 && ownerMap[neighbor] != ownerMap[i]) {
                    isBorder[i] = true;
                    break;
                }
            }
        }
    }

    private boolean allFrontiersEmpty() {
        for (Queue<Integer> f : frontiers) {
            if (!f.isEmpty()) return false;
        }
        return true;
    }

    private void renderFillingFrame(Map<Integer, Color> pixels, double timeSeconds) {
        int maxGen = Math.max(1, currentGeneration);

        for (int i = 0; i < totalLEDs; i++) {
            if (ownerMap[i] == -1) {
                // Unclaimed: dim dark blue
                pixels.put(i, applyBrightness(new Color(5, 5, 15)));
                continue;
            }

            Color baseColor = floodColors[ownerMap[i]];

            if (isBorder[i]) {
                // Border shimmer: oscillate between white/gold and the flood color
                double shimmer = 0.5 + 0.5 * Math.sin(timeSeconds * 10.0 + i * 0.7);
                Color borderGlow = blendColors(baseColor, new Color(255, 240, 180), shimmer);
                // Flash effect for freshly contested borders
                int age = currentGeneration - generationMap[i];
                if (age < 3) {
                    // Bright white flash for newly formed borders
                    double flashIntensity = 1.0 - (age / 3.0);
                    borderGlow = blendColors(borderGlow, Color.WHITE, flashIntensity * 0.7);
                }
                pixels.put(i, applyBrightness(borderGlow));
            } else {
                // Fade-in based on generation (newer = slightly dimmer, fading in)
                double genRatio = (double) generationMap[i] / maxGen;
                double fadeIn = Math.min(1.0, 1.0 - genRatio * 0.3);
                Color dimmed = scaleColor(baseColor, fadeIn);
                pixels.put(i, applyBrightness(dimmed));
            }
        }
    }

    private void renderFadeOutFrame(Map<Integer, Color> pixels, double timeSeconds, double fadeMultiplier) {
        int maxGen = Math.max(1, currentGeneration);

        for (int i = 0; i < totalLEDs; i++) {
            if (ownerMap[i] == -1) {
                pixels.put(i, Color.BLACK);
                continue;
            }

            Color baseColor = floodColors[ownerMap[i]];

            if (isBorder[i]) {
                double shimmer = 0.5 + 0.5 * Math.sin(timeSeconds * 10.0 + i * 0.7);
                Color borderGlow = blendColors(baseColor, new Color(255, 240, 180), shimmer);
                pixels.put(i, applyBrightness(scaleColor(borderGlow, fadeMultiplier)));
            } else {
                pixels.put(i, applyBrightness(scaleColor(baseColor, fadeMultiplier)));
            }
        }
    }

    private Color blendColors(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int)(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int)(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(clamp(r), clamp(g), clamp(bl));
    }

    private Color scaleColor(Color c, double scale) {
        return new Color(
            clamp((int)(c.getRed() * scale)),
            clamp((int)(c.getGreen() * scale)),
            clamp((int)(c.getBlue() * scale))
        );
    }

    private Color applyBrightness(Color c) {
        if (brightness >= 1.0) return c;
        return scaleColor(c, brightness);
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private int[] bfsDistances(int source) {
        int[] dist = new int[totalLEDs];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[source] = 0;
        Queue<Integer> queue = new LinkedList<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            int curr = queue.poll();
            for (int neighbor : graph.getNeighbors(curr)) {
                if (dist[neighbor] == Integer.MAX_VALUE) {
                    dist[neighbor] = dist[curr] + 1;
                    queue.add(neighbor);
                }
            }
        }
        return dist;
    }

    @Override
    public void dispose() {}

    @Override
    public String getName() {
        return "Flood Fill";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
