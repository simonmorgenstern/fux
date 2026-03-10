import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.Map;

public interface Effect {
    /**
     * Initialize effect with parameters and coordinate system
     */
    void initialize(JsonObject params, PixelCoordinates coords);
    
    /**
     * Render a single frame
     * @param frameNumber Frame counter (0, 1, 2, ...)
     * @param timeSeconds Time in seconds since effect started
     * @return Map of LED index -> Color
     */
    Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds);
    
    /**
     * Cleanup resources
     */
    void dispose();
    
    /**
     * Get effect name
     */
    String getName();
    
    /**
     * Get target FPS
     */
    int getFPS();
}
