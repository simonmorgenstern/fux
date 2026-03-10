import java.util.*;

public class LEDNeighborGraph {
    private Map<Integer, List<Integer>> neighbors;
    private PixelCoordinates coords;
    
    public LEDNeighborGraph(PixelCoordinates coords) {
        this.coords = coords;
        this.neighbors = new HashMap<>();
    }
    
    public void build(double minDistance, double maxDistance, int maxNeighbors) {
        System.out.println("Building LED neighbor graph...");
        System.out.println("  Distance range: " + minDistance + " - " + maxDistance);
        System.out.println("  Max neighbors per LED: " + maxNeighbors);
        
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate led = coords.get(i);
            List<LEDDistance> distances = new ArrayList<>();
            
            // Find all LEDs within distance range
            for (int j = 0; j < coords.getCount(); j++) {
                if (i == j) continue;
                
                PixelCoordinate other = coords.get(j);
                double dist = distance(led, other);
                
                if (dist >= minDistance && dist <= maxDistance) {
                    distances.add(new LEDDistance(j, dist));
                }
            }
            
            // Sort by distance and take the closest N
            distances.sort(Comparator.comparingDouble(d -> d.distance));
            
            List<Integer> ledNeighbors = new ArrayList<>();
            for (int k = 0; k < Math.min(maxNeighbors, distances.size()); k++) {
                ledNeighbors.add(distances.get(k).ledIndex);
            }
            
            neighbors.put(i, ledNeighbors);
        }
        
        // Log statistics
        int totalNeighbors = 0;
        int minNeighborCount = Integer.MAX_VALUE;
        int maxNeighborCount = 0;
        
        for (List<Integer> n : neighbors.values()) {
            totalNeighbors += n.size();
            minNeighborCount = Math.min(minNeighborCount, n.size());
            maxNeighborCount = Math.max(maxNeighborCount, n.size());
        }
        
        double avgNeighbors = (double)totalNeighbors / coords.getCount();
        System.out.println("  Graph built: avg " + String.format("%.1f", avgNeighbors) + 
                         " neighbors (min: " + minNeighborCount + ", max: " + maxNeighborCount + ")");
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
