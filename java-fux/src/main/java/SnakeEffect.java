import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class SnakeEffect implements Effect {
    private PixelCoordinates coords;
    private LEDNeighborGraph graph;
    private LinkedList<Integer> snakeBody;
    private int[] lastVisitedAt;
    private int moveCount;
    private int foodLED;
    private Random random;
    private int fps;
    private int moveEveryNFrames;
    private int frameCounter;
    private int maxLength;

    // Colors
    private Color snakeHeadColor;
    private Color foodColor;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;

        this.moveEveryNFrames = params.has("move_every_n_frames") ?
            params.get("move_every_n_frames").getAsInt() : 2;
        this.frameCounter = 0;
        this.maxLength = params.has("max_length") ?
            params.get("max_length").getAsInt() : 30;

        // Build neighbor graph (will load led-connections.json if available)
        double minDist = params.has("min_neighbor_distance") ?
            params.get("min_neighbor_distance").getAsDouble() : 10.0;
        double maxDist = params.has("max_neighbor_distance") ?
            params.get("max_neighbor_distance").getAsDouble() : 30.0;
        int maxNeighbors = params.has("max_neighbors") ?
            params.get("max_neighbors").getAsInt() : 4;

        this.graph = new LEDNeighborGraph(coords);
        this.graph.build(minDist, maxDist, maxNeighbors);

        // Colors
        this.snakeHeadColor = new Color(0, 255, 0);
        this.foodColor = new Color(255, 0, 0);

        if (coords == null || coords.getCount() == 0) {
            throw new RuntimeException("SnakeEffect requires valid LED coordinates");
        }

        this.lastVisitedAt = new int[coords.getCount()];

        // Initialize snake body along a valid path
        this.snakeBody = new LinkedList<>();
        this.moveCount = 0;
        initSnakeBody(4);

        spawnFood();

        System.out.println("SnakeEffect initialized:");
        System.out.println("  Graph from file: " + graph.isLoadedFromFile());
        System.out.println("  Initial length: " + snakeBody.size());
        System.out.println("  Max length: " + maxLength);
    }

    /**
     * Grow the initial snake along valid, non-overlapping neighbors.
     */
    private void initSnakeBody(int length) {
        int start = random.nextInt(coords.getCount());
        snakeBody.add(start);
        lastVisitedAt[start] = 1;

        for (int i = 1; i < length; i++) {
            int head = snakeBody.getLast();
            List<Integer> neighbors = graph.getNeighbors(head);
            // Pick a neighbor not already in the body
            int next = -1;
            List<Integer> shuffled = new ArrayList<>(neighbors);
            Collections.shuffle(shuffled, random);
            for (int n : shuffled) {
                if (!snakeBody.contains(n)) {
                    next = n;
                    break;
                }
            }
            if (next == -1) break; // no valid growth direction
            snakeBody.add(next);
            lastVisitedAt[next] = 1;
        }
    }

    /**
     * Place food at least minGraphDist hops away from the snake head,
     * so the snake has to travel to reach it.
     */
    private void spawnFood() {
        int head = snakeBody.getLast();
        int minDist = 5;

        // BFS to find distances from head
        int[] dist = bfsDistances(head);

        // Collect candidates far enough from head and not on the snake body
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < coords.getCount(); i++) {
            if (dist[i] >= minDist && !snakeBody.contains(i)) {
                candidates.add(i);
            }
        }

        // Fallback: any LED not on the snake
        if (candidates.isEmpty()) {
            for (int i = 0; i < coords.getCount(); i++) {
                if (!snakeBody.contains(i)) {
                    candidates.add(i);
                }
            }
        }

        if (!candidates.isEmpty()) {
            foodLED = candidates.get(random.nextInt(candidates.size()));
        } else {
            // Snake fills the whole graph — just pick random
            foodLED = random.nextInt(coords.getCount());
        }
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        frameCounter++;
        if (frameCounter >= moveEveryNFrames) {
            frameCounter = 0;
            moveSnake();
        }

        // Render snake body with gradient
        int bodySize = snakeBody.size();
        for (int i = 0; i < bodySize; i++) {
            int ledIndex = snakeBody.get(i);
            if (i == bodySize - 1) {
                pixels.put(ledIndex, snakeHeadColor);
            } else {
                double t = (double) i / bodySize;
                int g = (int)(60 + 190 * t);
                pixels.put(ledIndex, new Color(0, g, 0));
            }
        }

        // Pulsing food
        double pulse = 0.5 + 0.5 * Math.sin(timeSeconds * 8);
        int brightness = (int)(255 * pulse);
        pixels.put(foodLED, new Color(brightness, 0, 0));

        return pixels;
    }

    private void moveSnake() {
        moveCount++;
        int head = snakeBody.getLast();
        List<Integer> possibleMoves = graph.getNeighbors(head);

        if (possibleMoves.isEmpty()) return;

        // Filter out all body collisions — no exceptions
        List<Integer> validMoves = new ArrayList<>();
        Set<Integer> bodySet = new HashSet<>(snakeBody);
        for (int move : possibleMoves) {
            if (!bodySet.contains(move)) {
                validMoves.add(move);
            }
        }

        // If stuck, shrink the tail to free up space and retry
        if (validMoves.isEmpty()) {
            if (snakeBody.size() > 2) {
                snakeBody.removeFirst();
            }
            return;
        }

        // Use BFS to find which moves bring us closer to food
        int[] distToFood = bfsDistances(foodLED);

        // Weight: favor moves closer to food, break ties with recency
        double[] weights = new double[validMoves.size()];
        double totalWeight = 0;

        // Find the best (shortest) distance to food among candidates
        int bestFoodDist = Integer.MAX_VALUE;
        for (int move : validMoves) {
            if (distToFood[move] < bestFoodDist) {
                bestFoodDist = distToFood[move];
            }
        }

        for (int i = 0; i < validMoves.size(); i++) {
            int move = validMoves.get(i);
            double weight;

            if (distToFood[move] <= bestFoodDist) {
                // Moves toward food get high base weight
                weight = 100.0;
            } else {
                // Moves away from food get low weight but aren't zero
                weight = 5.0;
            }

            // Add recency bonus: prefer unvisited or long-ago-visited LEDs
            int age = moveCount - lastVisitedAt[move];
            if (lastVisitedAt[move] == 0) {
                age = moveCount + 50;
            }
            weight += age * 0.5;

            weights[i] = weight;
            totalWeight += weight;
        }

        // Weighted random pick
        double roll = random.nextDouble() * totalWeight;
        int nextLED = validMoves.get(validMoves.size() - 1);
        double cumulative = 0;
        for (int i = 0; i < validMoves.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                nextLED = validMoves.get(i);
                break;
            }
        }

        lastVisitedAt[nextLED] = moveCount;
        snakeBody.add(nextLED);

        if (nextLED == foodLED) {
            // Ate food — grow by keeping the tail this turn
            spawnFood();
        } else {
            snakeBody.removeFirst();
        }

        // Cap max length
        while (snakeBody.size() > maxLength) {
            snakeBody.removeFirst();
        }
    }

    /**
     * BFS from source, returns distance array. Unreachable LEDs get Integer.MAX_VALUE.
     */
    private int[] bfsDistances(int source) {
        int[] dist = new int[coords.getCount()];
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
        return "Snake";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
