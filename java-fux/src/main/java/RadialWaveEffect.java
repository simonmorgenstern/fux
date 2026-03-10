import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class RadialWaveEffect implements Effect {
    private PixelCoordinates coords;
    private double waveSpacing;
    private double waveWidth;
    private double waveSpeed;
    private int[] colorsHSV;
    private double brightness;
    private int fps;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.waveSpacing = params.get("wave_spacing").getAsDouble();
        this.waveWidth = params.get("wave_width").getAsDouble();
        this.waveSpeed = params.get("wave_speed").getAsDouble();
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 33;
        
        // Load color array
        JsonArray colorsArray = params.getAsJsonArray("colors_hsv");
        this.colorsHSV = new int[colorsArray.size()];
        for (int i = 0; i < colorsArray.size(); i++) {
            this.colorsHSV[i] = colorsArray.get(i).getAsInt();
        }
        
        System.out.println("RadialWaveEffect initialized:");
        System.out.println("  Wave spacing: " + waveSpacing);
        System.out.println("  Wave width: " + waveWidth);
        System.out.println("  Wave speed: " + waveSpeed);
        System.out.println("  Colors: " + colorsHSV.length);
        System.out.println("  FPS: " + fps);
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Calculate animation offset (how far waves have traveled)
        double animationOffset = frameNumber * waveSpeed;
        
        // For each LED
        for (int ledIndex = 0; ledIndex < coords.getCount(); ledIndex++) {
            PixelCoordinate led = coords.get(ledIndex);
            double distance = led.getDistanceFromCenter();
            
            // Check multiple waves dynamically based on animation offset
            // Calculate which waves could possibly be visible
            int firstWave = (int) Math.floor((animationOffset - coords.getMaxDistance() - waveWidth) / waveSpacing);
            int lastWave = (int) Math.ceil((animationOffset + waveWidth) / waveSpacing);
            
            for (int waveNum = firstWave; waveNum <= lastWave; waveNum++) {
                double waveOffset = waveNum * waveSpacing;
                double waveRadius = animationOffset - waveOffset;
                
                // Skip if wave is nowhere near this LED
                if (waveRadius < -waveWidth/2) {
                    continue;
                }
                if (waveRadius > coords.getMaxDistance() + waveWidth/2) {
                    continue;
                }
                
                double distFromWave = Math.abs(distance - waveRadius);
                
                // LED is within wave band
                if (distFromWave < waveWidth / 2.0) {
                    // Calculate intensity (1.0 at wave center, 0.0 at edge)
                    double intensity = 1.0 - (distFromWave / (waveWidth / 2.0));
                    intensity *= brightness;
                    
                    // Get color for this wave
                    int colorIndex = Math.abs(waveNum) % colorsHSV.length;
                    int hue = colorsHSV[colorIndex];
                    
                    // Convert HSV to RGB
                    Color color = Color.getHSBColor(hue / 360f, 1f, (float)intensity);
                    
                    // Only add if bright enough
                    if (color.getRed() > 5 || color.getGreen() > 5 || color.getBlue() > 5) {
                        pixels.put(ledIndex, color);
                        // Break after first wave hit (don't overlay multiple waves)
                        break;
                    }
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
        return "Radial Wave";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
