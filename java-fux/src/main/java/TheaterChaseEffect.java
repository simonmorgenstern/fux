import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

/**
 * Theater Chase effect - Moving segments like a theater marquee
 */
public class TheaterChaseEffect implements Effect {
    private PixelCoordinates coords;
    private int segmentSize;  // LEDs per segment
    private double speed;  // Segments per second
    private List<Color> colors;
    private int fps;
    
    // State
    private int offset;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.segmentSize = params.has("segment_size") ? params.get("segment_size").getAsInt() : 3;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 10.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.offset = 0;
        
        // Parse colors
        this.colors = new ArrayList<>();
        if (params.has("colors")) {
            JsonArray colorsArray = params.getAsJsonArray("colors");
            for (int i = 0; i < colorsArray.size(); i++) {
                String colorName = colorsArray.get(i).getAsString();
                colors.add(parseColor(colorName));
            }
        } else {
            // Default rainbow
            colors.add(new Color(255, 0, 0));      // Red
            colors.add(new Color(255, 128, 0));    // Orange
            colors.add(new Color(255, 255, 0));    // Yellow
            colors.add(new Color(0, 255, 0));      // Green
            colors.add(new Color(0, 128, 255));    // Light blue
            colors.add(new Color(0, 0, 255));      // Blue
            colors.add(new Color(128, 0, 255));    // Purple
        }
        
        System.out.println("TheaterChaseEffect initialized:");
        System.out.println("  Segment size: " + segmentSize + " LEDs");
        System.out.println("  Speed: " + speed + " segments/sec");
        System.out.println("  Colors: " + colors.size());
    }
    
    private Color parseColor(String name) {
        switch (name.toLowerCase()) {
            case "red": return new Color(255, 0, 0);
            case "orange": return new Color(255, 128, 0);
            case "yellow": return new Color(255, 255, 0);
            case "green": return new Color(0, 255, 0);
            case "cyan": return new Color(0, 255, 255);
            case "blue": return new Color(0, 0, 255);
            case "purple": return new Color(128, 0, 255);
            case "magenta": return new Color(255, 0, 255);
            case "white": return Color.WHITE;
            default: return Color.WHITE;
        }
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Update offset (move the chase pattern)
        int framesPerMove = Math.max(1, (int)(fps / speed));
        if (frameNumber % framesPerMove == 0) {
            offset = (offset + 1) % (segmentSize * colors.size());
        }
        
        // Render chase pattern
        for (int i = 0; i < coords.getCount(); i++) {
            // Calculate which segment this LED belongs to
            int position = (i + offset) % (segmentSize * colors.size());
            int segmentIndex = position / segmentSize;
            
            if (segmentIndex < colors.size()) {
                Color color = colors.get(segmentIndex);
                pixels.put(i, color);
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
        return "Theater Chase";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
