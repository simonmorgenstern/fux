import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class LEDNeighborGraph {
    private Map<Integer, List<Integer>> neighbors;
    private PixelCoordinates coords;
    private boolean loadedFromFile = false;

    public LEDNeighborGraph(PixelCoordinates coords) {
        this.coords = coords;
        this.neighbors = new HashMap<>();
    }

    /**
     * Try to load explicit connections from led-connections.json.
     * If found, uses those. Otherwise falls back to distance-based graph.
     */
    public void build(double minDistance, double maxDistance, int maxNeighbors) {
        if (loadFromFile()) {
            System.out.println("LED neighbor graph loaded from led-connections.json");
            logStats();
            return;
        }

        System.out.println("No led-connections.json found, falling back to distance-based graph");
        buildFromDistance(minDistance, maxDistance, maxNeighbors);
    }

    /**
     * Load connections from led-connections.json.
     * Format: [[0,1], [1,2], [2,3], ...]
     * Each edge is bidirectional.
     */
    private boolean loadFromFile() {
        Gson gson = new Gson();

        // File system paths
        String[] paths = {
            "/home/simon/.openclaw/workspace/fux/led-namer/led-connections.json",
            "/home/pi/fux/led-namer/led-connections.json",
            "led-namer/led-connections.json",
            "../led-namer/led-connections.json",
            "../../led-namer/led-connections.json",
            "led-connections.json"
        };

        for (String path : paths) {
            try {
                FileReader reader = new FileReader(path);
                JsonArray arr = gson.fromJson(reader, JsonArray.class);
                reader.close();
                if (arr != null && arr.size() > 0) {
                    parseEdges(arr);
                    System.out.println("  Loaded from: " + path);
                    loadedFromFile = true;
                    return true;
                }
            } catch (Exception e) {
                // try next
            }
        }

        // Classpath fallback
        String[] classpathPaths = {
            "led-connections.json",
            "led-namer/led-connections.json",
            "assets/led-connections.json"
        };
        for (String cp : classpathPaths) {
            try {
                InputStream is = getClass().getClassLoader().getResourceAsStream(cp);
                if (is != null) {
                    JsonArray arr = gson.fromJson(new InputStreamReader(is), JsonArray.class);
                    is.close();
                    if (arr != null && arr.size() > 0) {
                        parseEdges(arr);
                        System.out.println("  Loaded from classpath: " + cp);
                        loadedFromFile = true;
                        return true;
                    }
                }
            } catch (Exception e) {
                // try next
            }
        }

        return false;
    }

    private void parseEdges(JsonArray edges) {
        neighbors.clear();
        // Initialize empty lists for all LEDs
        for (int i = 0; i < coords.getCount(); i++) {
            neighbors.put(i, new ArrayList<>());
        }

        int edgeCount = 0;
        for (JsonElement elem : edges) {
            JsonArray edge = elem.getAsJsonArray();
            int a = edge.get(0).getAsInt();
            int b = edge.get(1).getAsInt();
            // Bidirectional
            neighbors.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
            neighbors.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
            edgeCount++;
        }
        System.out.println("  Parsed " + edgeCount + " connections");
    }

    public boolean isLoadedFromFile() {
        return loadedFromFile;
    }

    private void buildFromDistance(double minDistance, double maxDistance, int maxNeighbors) {
        System.out.println("Building LED neighbor graph from distance...");
        System.out.println("  Distance range: " + minDistance + " - " + maxDistance);
        System.out.println("  Max neighbors per LED: " + maxNeighbors);

        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate led = coords.get(i);
            List<LEDDistance> distances = new ArrayList<>();

            for (int j = 0; j < coords.getCount(); j++) {
                if (i == j) continue;
                PixelCoordinate other = coords.get(j);
                double dist = distance(led, other);
                if (dist >= minDistance && dist <= maxDistance) {
                    distances.add(new LEDDistance(j, dist));
                }
            }

            distances.sort(Comparator.comparingDouble(d -> d.distance));

            List<Integer> ledNeighbors = new ArrayList<>();
            for (int k = 0; k < Math.min(maxNeighbors, distances.size()); k++) {
                ledNeighbors.add(distances.get(k).ledIndex);
            }

            neighbors.put(i, ledNeighbors);
        }

        logStats();
    }

    private void logStats() {
        int totalNeighbors = 0;
        int minNeighborCount = Integer.MAX_VALUE;
        int maxNeighborCount = 0;
        int disconnected = 0;

        for (int i = 0; i < coords.getCount(); i++) {
            List<Integer> n = neighbors.getOrDefault(i, Collections.emptyList());
            totalNeighbors += n.size();
            minNeighborCount = Math.min(minNeighborCount, n.size());
            maxNeighborCount = Math.max(maxNeighborCount, n.size());
            if (n.isEmpty()) disconnected++;
        }

        double avgNeighbors = (double) totalNeighbors / coords.getCount();
        System.out.println("  Graph stats: avg " + String.format("%.1f", avgNeighbors) +
                " neighbors (min: " + minNeighborCount + ", max: " + maxNeighborCount + ")");
        if (disconnected > 0) {
            System.out.println("  WARNING: " + disconnected + " LEDs have no connections!");
        }
    }

    public List<Integer> getNeighbors(int ledIndex) {
        return neighbors.getOrDefault(ledIndex, new ArrayList<>());
    }

    private double distance(PixelCoordinate a, PixelCoordinate b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static class LEDDistance {
        int ledIndex;
        double distance;

        LEDDistance(int ledIndex, double distance) {
            this.ledIndex = ledIndex;
            this.distance = distance;
        }
    }
}
