import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class TestFillEffect implements Effect {
    private PixelCoordinates coords;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        System.out.println("TestFillEffect: coords.getCount() = " + coords.getCount());
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Fill ALL pixels with white
        for (int i = 0; i < coords.getCount(); i++) {
            pixels.put(i, Color.WHITE);
        }
        
        System.out.println("TestFillEffect rendered " + pixels.size() + " pixels");
        return pixels;
    }
    
    @Override
    public void dispose() {}
    
    @Override
    public String getName() { return "Test Fill"; }
    
    @Override
    public int getFPS() { return 30; }
}
