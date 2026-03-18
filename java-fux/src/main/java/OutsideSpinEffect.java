import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.io.FileReader;
import java.util.*;

public class OutsideSpinEffect implements Effect {
    private PixelCoordinates coords;
    private double speed;
    private int fps;
    private List<Integer> outsideLEDs;
    private Map<Integer, Double> ledAngles;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        
        // Load LED groups
        Set<Integer> outsideSet = loadLEDGroup("outside");
        this.outsideLEDs = new ArrayList<>(outsideSet);
        
        // Calculate angles for each LED based on their coordinates
        this.ledAngles = new HashMap<>();
        calculateLEDAngles();
        
        System.out.println("OutsideSpinEffect initialized:");
        System.out.println("  Speed: " + speed);
        System.out.println("  Outside LEDs: " + outsideLEDs.size());
        System.out.println("  Calculated angles for positioning");
    }
    
    private Set<Integer> loadLEDGroup(String groupName) {
        Set<Integer> leds = new HashSet<>();
        try {
            // Read LED groups file
            Gson gson = new Gson();
            FileReader reader = new FileReader("../led-namer/led-groups.json");
            JsonArray groups = gson.fromJson(reader, JsonArray.class);
            reader.close();
            
            // Find the group
            for (int i = 0; i < groups.size(); i++) {
                JsonObject group = groups.get(i).getAsJsonObject();
                if (group.get("name").getAsString().equals(groupName)) {
                    JsonArray ranges = group.getAsJsonArray("ranges");
                    
                    // Parse ranges
                    for (int j = 0; j < ranges.size(); j++) {
                        JsonObject range = ranges.get(j).getAsJsonObject();
                        int start = range.get("start").getAsInt();
                        int end = range.get("end").getAsInt();
                        
                        for (int led = start; led <= end; led++) {
                            leds.add(led);
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading LED group '" + groupName + "': " + e.getMessage());
        }
        return leds;
    }
    
    private void calculateLEDAngles() {
        // Find center point of outside ring
        double centerX = 0, centerY = 0;
        int count = 0;
        
        for (int led : outsideLEDs) {
            if (led < coords.getCount()) {
                PixelCoordinate coord = coords.get(led);
                centerX += coord.getX();
                centerY += coord.getY();
                count++;
            }
        }
        
        if (count > 0) {
            centerX /= count;
            centerY /= count;
            
            // Calculate angle for each LED
            for (int led : outsideLEDs) {
                if (led < coords.getCount()) {
                    PixelCoordinate coord = coords.get(led);
                    double dx = coord.getX() - centerX;
                    double dy = coord.getY() - centerY;
                    
                    // Calculate angle in radians (0 to 2*PI)
                    double angle = Math.atan2(dy, dx);
                    if (angle < 0) angle += 2 * Math.PI;
                    
                    ledAngles.put(led, angle);
                }
            }
        }
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Spinning rainbow effect
        // Full rotation takes 2 seconds at speed=1
        double rotationTime = 2.0 / speed;
        double rotationPhase = (timeSeconds % rotationTime) / rotationTime;  // 0 to 1
        double rotationAngle = rotationPhase * 2 * Math.PI;  // 0 to 2*PI
        
        // Number of color segments around the ring
        int numSegments = 3;
        double segmentWidth = Math.PI / 4;  // Width of each colored segment (45 degrees)
        
        for (int led : outsideLEDs) {
            if (ledAngles.containsKey(led)) {
                double ledAngle = ledAngles.get(led);
                
                // Calculate angle relative to rotating segments
                double relativeAngle = (ledAngle - rotationAngle) % (2 * Math.PI);
                if (relativeAngle < 0) relativeAngle += 2 * Math.PI;
                
                // Determine which segment this LED belongs to
                double segmentPosition = (relativeAngle / (2 * Math.PI)) * numSegments;
                int segmentIndex = (int)segmentPosition;
                double segmentFraction = segmentPosition - segmentIndex;
                
                // Calculate intensity (peak in middle of segment, fade at edges)
                double intensity = 0;
                if (segmentFraction < 0.3) {
                    intensity = segmentFraction / 0.3;  // Fade in
                } else if (segmentFraction > 0.7) {
                    intensity = (1.0 - segmentFraction) / 0.3;  // Fade out
                } else {
                    intensity = 1.0;  // Full brightness in middle
                }
                
                // Select color based on segment
                Color baseColor;
                switch (segmentIndex % 3) {
                    case 0:
                        baseColor = new Color(255, 0, 0);  // Red
                        break;
                    case 1:
                        baseColor = new Color(0, 255, 0);  // Green
                        break;
                    default:
                        baseColor = new Color(0, 0, 255);  // Blue
                        break;
                }
                
                // Apply intensity
                if (intensity > 0.1) {
                    Color ledColor = new Color(
                        (int)(baseColor.getRed() * intensity),
                        (int)(baseColor.getGreen() * intensity),
                        (int)(baseColor.getBlue() * intensity)
                    );
                    pixels.put(led, ledColor);
                }
            }
        }
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Outside Spin";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
