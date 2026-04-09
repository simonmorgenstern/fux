import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class ForestFireEffect implements Effect {
    private static final int EMPTY = 0;
    private static final int TREE = 1;
    private static final int BURNING = 2;
    private static final int COOLING = 3;

    private static final int COOLING_FRAMES = 6;

    private PixelCoordinates coords;
    private LEDNeighborGraph graph;
    private Random random;
    private int fps;

    private double growProb;
    private double spreadProb;
    private double lightningProb;
    private int burnFrames;
    private double brightness;

    // Per-LED state (double-buffered)
    private int[] state;
    private int[] nextState;
    private int[] burnTimer;
    private int[] nextBurnTimer;
    private int[] coolTimer;
    private int[] nextCoolTimer;

    // Slight green hue variation per tree LED (assigned on transition to TREE)
    private float[] treeHue;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 15;

        this.growProb = params.has("grow_prob") ? params.get("grow_prob").getAsDouble() : 0.03;
        this.spreadProb = params.has("spread_prob") ? params.get("spread_prob").getAsDouble() : 0.5;
        this.lightningProb = params.has("lightning_prob") ? params.get("lightning_prob").getAsDouble() : 0.002;
        this.burnFrames = params.has("burn_frames") ? params.get("burn_frames").getAsInt() : 8;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;

        // Build neighbor graph
        double minDist = params.has("min_neighbor_distance") ?
            params.get("min_neighbor_distance").getAsDouble() : 5.0;
        double maxDist = params.has("max_neighbor_distance") ?
            params.get("max_neighbor_distance").getAsDouble() : 30.0;
        int maxNeighbors = params.has("max_neighbors") ?
            params.get("max_neighbors").getAsInt() : 6;

        this.graph = new LEDNeighborGraph(coords);
        this.graph.build(minDist, maxDist, maxNeighbors);

        int count = coords.getCount();
        state = new int[count];
        nextState = new int[count];
        burnTimer = new int[count];
        nextBurnTimer = new int[count];
        coolTimer = new int[count];
        nextCoolTimer = new int[count];
        treeHue = new float[count];

        // Initialize: ~60% trees, rest empty
        for (int i = 0; i < count; i++) {
            if (random.nextDouble() < 0.6) {
                state[i] = TREE;
                treeHue[i] = 0.28f + random.nextFloat() * 0.10f;
            } else {
                state[i] = EMPTY;
            }
            burnTimer[i] = 0;
            coolTimer[i] = 0;
        }

        System.out.println("ForestFireEffect initialized:");
        System.out.println("  LEDs: " + count);
        System.out.println("  grow_prob=" + growProb + " spread_prob=" + spreadProb +
            " lightning_prob=" + lightningProb + " burn_frames=" + burnFrames);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        int count = coords.getCount();

        // Compute next state from current state
        for (int i = 0; i < count; i++) {
            switch (state[i]) {
                case EMPTY:
                    // EMPTY -> TREE with grow probability
                    if (random.nextDouble() < growProb) {
                        nextState[i] = TREE;
                        treeHue[i] = 0.28f + random.nextFloat() * 0.10f;
                    } else {
                        nextState[i] = EMPTY;
                    }
                    nextBurnTimer[i] = 0;
                    nextCoolTimer[i] = 0;
                    break;

                case TREE:
                    // Check if any neighbor is burning
                    boolean neighborBurning = false;
                    List<Integer> neighbors = graph.getNeighbors(i);
                    for (int n : neighbors) {
                        if (state[n] == BURNING) {
                            neighborBurning = true;
                            break;
                        }
                    }

                    if (neighborBurning && random.nextDouble() < spreadProb) {
                        // Catch fire from neighbor
                        nextState[i] = BURNING;
                        nextBurnTimer[i] = burnFrames;
                        nextCoolTimer[i] = 0;
                    } else if (random.nextDouble() < lightningProb) {
                        // Spontaneous lightning strike
                        nextState[i] = BURNING;
                        nextBurnTimer[i] = burnFrames;
                        nextCoolTimer[i] = 0;
                    } else {
                        nextState[i] = TREE;
                        nextBurnTimer[i] = 0;
                        nextCoolTimer[i] = 0;
                    }
                    break;

                case BURNING:
                    if (burnTimer[i] <= 1) {
                        // Done burning, transition to cooling
                        nextState[i] = COOLING;
                        nextBurnTimer[i] = 0;
                        nextCoolTimer[i] = COOLING_FRAMES;
                    } else {
                        // Continue burning
                        nextState[i] = BURNING;
                        nextBurnTimer[i] = burnTimer[i] - 1;
                        nextCoolTimer[i] = 0;
                    }
                    break;

                case COOLING:
                    if (coolTimer[i] <= 1) {
                        // Done cooling, become empty
                        nextState[i] = EMPTY;
                        nextBurnTimer[i] = 0;
                        nextCoolTimer[i] = 0;
                    } else {
                        nextState[i] = COOLING;
                        nextBurnTimer[i] = 0;
                        nextCoolTimer[i] = coolTimer[i] - 1;
                    }
                    break;
            }
        }

        // Swap buffers
        int[] tmpState = state;
        state = nextState;
        nextState = tmpState;

        int[] tmpBurn = burnTimer;
        burnTimer = nextBurnTimer;
        nextBurnTimer = tmpBurn;

        int[] tmpCool = coolTimer;
        coolTimer = nextCoolTimer;
        nextCoolTimer = tmpCool;

        // Render colors from current state
        Map<Integer, Color> pixels = new HashMap<>();
        for (int i = 0; i < count; i++) {
            Color c;
            switch (state[i]) {
                case TREE:
                    // Dark green with slight hue variation
                    c = Color.getHSBColor(treeHue[i], 0.9f, (float)(0.3 * brightness));
                    break;

                case BURNING:
                    // Animate yellow -> orange -> red based on burn timer
                    float burnProgress = 1.0f - ((float) burnTimer[i] / burnFrames);
                    // burnProgress: 0 = just caught fire (yellow), 1 = about to go out (red)
                    // Hue: 0.15 (yellow) -> 0.08 (orange) -> 0.0 (red)
                    float hue = 0.15f - burnProgress * 0.15f;
                    float sat = 1.0f;
                    float bri = (float)(brightness * (0.7 + 0.3 * (1.0 - burnProgress)));
                    c = Color.getHSBColor(hue, sat, bri);
                    break;

                case COOLING:
                    // Dim red fading to black
                    float coolProgress = 1.0f - ((float) coolTimer[i] / COOLING_FRAMES);
                    // coolProgress: 0 = just started cooling, 1 = about to go empty
                    float coolBri = (float)(0.3 * brightness * (1.0 - coolProgress));
                    c = Color.getHSBColor(0.0f, 1.0f, coolBri);
                    break;

                default: // EMPTY
                    c = Color.BLACK;
                    break;
            }
            pixels.put(i, c);
        }

        return pixels;
    }

    @Override
    public void dispose() {
        state = null;
        nextState = null;
        burnTimer = null;
        nextBurnTimer = null;
        coolTimer = null;
        nextCoolTimer = null;
        treeHue = null;
    }

    @Override
    public String getName() {
        return "Forest Fire";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
