import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class SnakeEffect implements Effect {
    private PixelCoordinates coords;
    private LEDNeighborGraph graph;
    private LinkedList<Integer> snakeBody;
    private int[] lastVisitedAt; // move number when each LED was last visited
    private int moveCount;
    private int foodLED;
    private Random random;
    private int fps;
    private int moveEveryNFrames;
    private int frameCounter;

    // Colors
    private Color snakeHeadColor;
    private Color snakeBodyColor;
    private Color foodColor;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        
        // Snake movement speed (frames per move)
        this.moveEveryNFrames = params.has("move_every_n_frames") ?
            params.get("move_every_n_frames").getAsInt() : 2;
        this.frameCounter = 0;
        
        // Build neighbor graph
        double minDist = params.has("min_neighbor_distance") ? 
            params.get("min_neighbor_distance").getAsDouble() : 10.0;
        double maxDist = params.has("max_neighbor_distance") ? 
            params.get("max_neighbor_distance").getAsDouble() : 30.0;
        int maxNeighbors = params.has("max_neighbors") ? 
            params.get("max_neighbors").getAsInt() : 4;
        
        this.graph = new LEDNeighborGraph(coords);
        this.graph.build(minDist, maxDist, maxNeighbors);
        
        // Colors
        this.snakeHeadColor = new Color(0, 255, 0);      // Bright green
        this.snakeBodyColor = new Color(0, 150, 0);      // Dark green
        this.foodColor = new Color(255, 0, 0);           // Red
        
        // Initialize snake (4 LEDs long, random start position)
        this.snakeBody = new LinkedList<>();
        this.moveCount = 0;

        // Safety check: ensure coordinates are loaded
        if (coords == null || coords.getCount() == 0) {
            System.err.println("SnakeEffect: Cannot initialize - no coordinates available");
            throw new RuntimeException("SnakeEffect requires valid LED coordinates");
        }

        // Track when each LED was last visited (0 = never)
        this.lastVisitedAt = new int[coords.getCount()];

        int startLED = random.nextInt(coords.getCount());
        snakeBody.add(startLED);
        lastVisitedAt[startLED] = 1;
        
        // Grow initial snake by following neighbors
        for (int i = 1; i < 4; i++) {
            int currentHead = snakeBody.getLast();
            List<Integer> neighbors = graph.getNeighbors(currentHead);
            if (!neighbors.isEmpty()) {
                int nextLED = neighbors.get(random.nextInt(neighbors.size()));
                snakeBody.add(nextLED);
            } else {
                // If no neighbors, just duplicate current position
                snakeBody.add(currentHead);
            }
        }
        
        // Spawn initial food
        spawnFood();
        
        System.out.println("SnakeEffect initialized:");
        System.out.println("  Initial length: " + snakeBody.size());
        System.out.println("  Movement speed: every " + moveEveryNFrames + " frames");
        System.out.println("  Food LED: " + foodLED);
    }
    
    private void spawnFood() {
        // Random LED that's not part of snake
        int attempts = 0;
        do {
            foodLED = random.nextInt(coords.getCount());
            attempts++;
        } while (snakeBody.contains(foodLED) && attempts < 100);
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Move snake every N frames
        frameCounter++;
        if (frameCounter >= moveEveryNFrames) {
            frameCounter = 0;
            moveSnake();
        }
        
        // Render snake body (gradient from tail to head)
        int bodySize = snakeBody.size();
        for (int i = 0; i < bodySize; i++) {
            int ledIndex = snakeBody.get(i);
            
            if (i == bodySize - 1) {
                // Head: bright green
                pixels.put(ledIndex, snakeHeadColor);
            } else {
                // Body: gradient based on position
                double intensity = 0.3 + (0.7 * i / (double)bodySize);
                int g = (int)(150 * intensity);
                pixels.put(ledIndex, new Color(0, g, 0));
            }
        }
        
        // Render food (pulsing red)
        double pulse = 0.5 + 0.5 * Math.sin(timeSeconds * 8);
        int foodBrightness = (int)(255 * pulse);
        pixels.put(foodLED, new Color(foodBrightness, 0, 0));
        
        return pixels;
    }
    
    private void moveSnake() {
        moveCount++;
        int currentHead = snakeBody.getLast();
        List<Integer> possibleMoves = graph.getNeighbors(currentHead);

        if (possibleMoves.isEmpty()) {
            return;
        }

        // Remove moves that would collide with snake body (except tail, which will move)
        List<Integer> validMoves = new ArrayList<>();
        for (int move : possibleMoves) {
            if (!snakeBody.contains(move) || move == snakeBody.getFirst()) {
                validMoves.add(move);
            }
        }

        if (validMoves.isEmpty()) {
            validMoves = possibleMoves;
        }

        // Weight each candidate by how long ago it was last visited.
        // LEDs never visited (0) or visited long ago get high weight,
        // pulling the snake toward unexplored interior areas.
        double[] weights = new double[validMoves.size()];
        double totalWeight = 0;
        for (int i = 0; i < validMoves.size(); i++) {
            int led = validMoves.get(i);
            int age = moveCount - lastVisitedAt[led]; // large if visited long ago
            if (lastVisitedAt[led] == 0) {
                age = moveCount + 100; // never visited — strong preference
            }
            weights[i] = age * age; // square to strongly favor least-recently-visited
            totalWeight += weights[i];
        }

        // Weighted random selection
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

        // Move head to new position
        snakeBody.add(nextLED);

        // Check if snake ate food
        if (nextLED == foodLED) {
            spawnFood();
            System.out.println("Snake ate food! Length: " + snakeBody.size() + ", new food: " + foodLED);
        } else {
            snakeBody.removeFirst();
        }
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Snake";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
