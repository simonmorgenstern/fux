import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.awt.Color;
import java.util.*;

public class FloodFillEffect implements Effect {
    private PixelCoordinates coords;
    private LEDNeighborGraph graph;
    private Random random;
    private int fps;
    private int numColors;
    private int ticksPerStep;

    // State
    private Color[] ledColors;           // assigned color per LED (null = unclaimed)
    private List<Set<Integer>> frontiers; // active frontier per color source
    private List<Color> colors;
    private int filledCount;
    private long lastStepFrame;
    private boolean fillComplete;
    private long fillCompleteFrame;
    private int holdFrames;

    private static final Color[] PALETTE = {
        new Color(255, 30, 60),    // red
        new Color(0, 200, 255),    // cyan
        new Color(255, 200, 0),    // gold
        new Color(100, 255, 80),   // green
        new Color(200, 50, 255),   // purple
        new Color(255, 120, 30),   // orange
        new Color(50, 255, 200),   // teal
        new Color(255, 80, 180),   // pink
    };

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 20;
        this.numColors = params.has("num_colors") ? params.get("num_colors").getAsInt() : 0;
        this.ticksPerStep = params.has("ticks_per_step") ? params.get("ticks_per_step").getAsInt() : 3;
        this.holdFrames = params.has("hold_frames") ? params.get("hold_frames").getAsInt() : 40;

        // Build neighbor graph
        this.graph = new LEDNeighborGraph(coords);
        this.graph.build(5.0, 30.0, 6);

        reset();

        System.out.println("FloodFillEffect initialized:");
        System.out.println("  Colors: " + colors.size());
        System.out.println("  Ticks per step: " + ticksPerStep);
        System.out.println("  Graph from file: " + graph.isLoadedFromFile());
    }

    private void reset() {
        int total = coords.getCount();
        this.ledColors = new Color[total];
        this.frontiers = new ArrayList<>();
        this.colors = new ArrayList<>();
        this.filledCount = 0;
        this.lastStepFrame = -ticksPerStep;
        this.fillComplete = false;
        this.fillCompleteFrame = 0;

        // Pick 3-5 colors
        int count = numColors > 0 ? numColors : 3 + random.nextInt(3);
        List<Color> shuffled = new ArrayList<>(Arrays.asList(PALETTE));
        Collections.shuffle(shuffled, random);

        // Pick distinct starting LEDs
        Set<Integer> usedOrigins = new HashSet<>();
        for (int i = 0; i < count; i++) {
            colors.add(shuffled.get(i % shuffled.size()));

            int origin = pickOrigin(usedOrigins);
            usedOrigins.add(origin);

            ledColors[origin] = colors.get(i);
            filledCount++;

            Set<Integer> frontier = new HashSet<>();
            frontier.add(origin);
            frontiers.add(frontier);
        }
    }

    private int pickOrigin(Set<Integer> used) {
        int total = coords.getCount();
        int attempts = 0;
        while (attempts < 200) {
            int led = random.nextInt(total);
            if (!used.contains(led) && !graph.getNeighbors(led).isEmpty()) {
                return led;
            }
            attempts++;
        }
        // Fallback: first unused LED with neighbors
        for (int i = 0; i < total; i++) {
            if (!used.contains(i) && !graph.getNeighbors(i).isEmpty()) return i;
        }
        return 0;
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        // If fill is complete, hold then reset
        if (fillComplete) {
            if (frameNumber - fillCompleteFrame >= holdFrames) {
                reset();
            }
        } else {
            // Advance one BFS step every ticksPerStep frames
            if (frameNumber - lastStepFrame >= ticksPerStep) {
                advanceStep();
                lastStepFrame = frameNumber;

                if (filledCount >= coords.getCount() || allFrontiersEmpty()) {
                    fillComplete = true;
                    fillCompleteFrame = frameNumber;
                }
            }
        }

        // Render current state
        Map<Integer, Color> pixels = new HashMap<>();
        for (int i = 0; i < coords.getCount(); i++) {
            if (ledColors[i] != null) {
                pixels.put(i, ledColors[i]);
            }
        }
        return pixels;
    }

    private void advanceStep() {
        // All colors expand simultaneously — collect new claims first, then apply
        // If two colors try to claim the same LED in the same step, first one wins (random order)
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < frontiers.size(); i++) order.add(i);
        Collections.shuffle(order, random);

        List<Set<Integer>> newFrontiers = new ArrayList<>();
        for (int i = 0; i < frontiers.size(); i++) {
            newFrontiers.add(new HashSet<>());
        }

        for (int idx : order) {
            Set<Integer> frontier = frontiers.get(idx);
            Color color = colors.get(idx);
            Set<Integer> nextFrontier = newFrontiers.get(idx);

            for (int led : frontier) {
                for (int neighbor : graph.getNeighbors(led)) {
                    if (ledColors[neighbor] == null) {
                        ledColors[neighbor] = color;
                        filledCount++;
                        nextFrontier.add(neighbor);
                    }
                }
            }
        }

        for (int i = 0; i < frontiers.size(); i++) {
            frontiers.set(i, newFrontiers.get(i));
        }
    }

    private boolean allFrontiersEmpty() {
        for (Set<Integer> f : frontiers) {
            if (!f.isEmpty()) return false;
        }
        return true;
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
