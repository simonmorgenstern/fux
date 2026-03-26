import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PixelCoordinates {
    private List<PixelCoordinate> coordinates;
    private double centerX;
    private double centerY;
    private double maxDistance;
    
    public PixelCoordinates(String jsonFilePath) {
        loadFromJson(jsonFilePath);
        calculateCenter();
        calculateDistances();
    }
    
    /**
     * Load coordinates from file with multiple path fallbacks
     */
    public static PixelCoordinates loadWithFallback() {
        String[] pathsToTry = {
            "/home/pi/fux/assets/pixelCoordinates.json",
            "pixelCoordinates.json",
            "assets/pixelCoordinates.json",
            "../assets/pixelCoordinates.json"
        };
        
        // Try file system paths first
        for (String path : pathsToTry) {
            try {
                return new PixelCoordinates(path);
            } catch (Exception e) {
                // Try next path
            }
        }
        
        // Try classpath as last resort
        try {
            Gson gson = new Gson();
            Type listType = new TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
            java.io.InputStream is = PixelCoordinates.class.getClassLoader().getResourceAsStream("pixelCoordinates.json");
            if (is != null) {
                List<Map<String, Object>> rawCoords = gson.fromJson(new InputStreamReader(is), listType);
                PixelCoordinates coords = new PixelCoordinates(new ArrayList<>(), rawCoords);
                System.out.println("Loaded coordinates from classpath: pixelCoordinates.json");
                return coords;
            }
        } catch (Exception e) {
            // Fall through
        }
        
        System.err.println("Failed to load coordinates from all paths");
        return null;
    }
    
    // Internal constructor for use by loadWithFallback
    private PixelCoordinates(List<PixelCoordinate> coordinates, List<Map<String, Object>> rawCoords) {
        this.coordinates = new ArrayList<>();
        for (Map<String, Object> coord : rawCoords) {
            int index = ((Double) coord.get("index")).intValue();
            double x = (Double) coord.get("x");
            double y = (Double) coord.get("y");
            this.coordinates.add(new PixelCoordinate(index, x, y));
        }
        System.out.println("Loaded " + this.coordinates.size() + " pixel coordinates");
        calculateCenter();
        calculateDistances();
    }
    
    private void loadFromJson(String filePath) {
        try {
            Gson gson = new Gson();
            Type listType = new TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> rawCoords = gson.fromJson(new FileReader(filePath), listType);
            
            coordinates = new ArrayList<>();
            for (Map<String, Object> coord : rawCoords) {
                int index = ((Double) coord.get("index")).intValue();
                double x = (Double) coord.get("x");
                double y = (Double) coord.get("y");
                coordinates.add(new PixelCoordinate(index, x, y));
            }
            
            System.out.println("Loaded " + coordinates.size() + " pixel coordinates");
        } catch (Exception e) {
            System.err.println("Error loading coordinates: " + e.getMessage());
            e.printStackTrace();
            coordinates = new ArrayList<>();
        }
    }
    
    private void calculateCenter() {
        if (coordinates.isEmpty()) {
            centerX = 0;
            centerY = 0;
            return;
        }
        
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        
        for (PixelCoordinate coord : coordinates) {
            minX = Math.min(minX, coord.getX());
            maxX = Math.max(maxX, coord.getX());
            minY = Math.min(minY, coord.getY());
            maxY = Math.max(maxY, coord.getY());
        }
        
        centerX = (minX + maxX) / 2.0;
        centerY = (minY + maxY) / 2.0;
        
        System.out.println("Center calculated: (" + centerX + ", " + centerY + ")");
    }
    
    private void calculateDistances() {
        maxDistance = 0;
        
        for (PixelCoordinate coord : coordinates) {
            double dx = coord.getX() - centerX;
            double dy = coord.getY() - centerY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            coord.setDistanceFromCenter(distance);
            maxDistance = Math.max(maxDistance, distance);
        }
        
        System.out.println("Max distance from center: " + maxDistance);
    }
    
    public int getCount() {
        return coordinates.size();
    }
    
    public PixelCoordinate get(int index) {
        return coordinates.get(index);
    }
    
    public double getCenterX() {
        return centerX;
    }
    
    public double getCenterY() {
        return centerY;
    }
    
    public double getMaxDistance() {
        return maxDistance;
    }
}
