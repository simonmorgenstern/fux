package renderer;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class RainbowPulseEffect implements Effect {
    private PixelCoordinates coords;
    private double hueRotationSpeed;
    private double pulseFrequency;
    private double brightnessMin;
    private double brightnessMax;
    private int fps;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.hueRotationSpeed = params.get("hue_rotation_speed").getAsDouble();
        this.pulseFrequency = params.get("pulse_frequency").getAsDouble();
        this.brightnessMin = params.get("brightness_min").getAsDouble();
        this.brightnessMax = params.get("brightness_max").getAsDouble();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        
        System.out.println("RainbowPulseEffect initialized:");
        System.out.println("  Hue rotation speed: " + hueRotationSpeed);
        System.out.println("  Pulse frequency: " + pulseFrequency);
        System.out.println("  Brightness range: " + brightnessMin + " - " + brightnessMax);
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Calculate current hue (rotates over time)
        float currentHue = (float)((frameNumber * hueRotationSpeed) % 360) / 360f;
        
        // Calculate current brightness (pulse using sine wave)
        double pulsePhase = timeSeconds * pulseFrequency * 2 * Math.PI;
        double pulseBrightness = brightnessMin + 
            (brightnessMax - brightnessMin) * (0.5 + 0.5 * Math.sin(pulsePhase));
        
        // Apply same color to all LEDs
        Color color = Color.getHSBColor(currentHue, 1.0f, (float)pulseBrightness);
        
        for (int i = 0; i < coords.getCount(); i++) {
            pixels.put(i, color);
        }
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Rainbow Pulse";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
