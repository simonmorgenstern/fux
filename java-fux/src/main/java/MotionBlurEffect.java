import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class MotionBlurEffect implements Effect {
    private PixelCoordinates coords;
    private int fps;
    
    // Darker color constants for the full-fox flicker
    private static final Color DARK_BLUE = new Color(0, 0, 120);
    private static final Color DARK_RED = new Color(120, 0, 0);
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 60;
        
        System.out.println("MotionBlurEffect initialized:");
        System.out.println("  Total LEDs: " + coords.getCount());
        System.out.println("  FPS: " + fps);
        System.out.println("  Effect: Full-fox flicker (dark blue/red)");
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Simple flicker pattern: 3 frames blue, 3 frames red, repeat
        // Frames 0-2: blue, 3-5: red, 6-8: blue, etc.
        boolean isBluePhase = ((frameNumber / 3) % 2) == 0;
        Color currentColor = isBluePhase ? DARK_BLUE : DARK_RED;
        
        // Light ALL LEDs with the current color
        for (int ledIndex = 0; ledIndex < coords.getCount(); ledIndex++) {
            pixels.put(ledIndex, currentColor);
        }
        
        System.out.println("Frame " + frameNumber + ": " + pixels.size() + " LEDs - " + 
                          (isBluePhase ? "DARK BLUE" : "DARK RED"));
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Motion Blur";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
