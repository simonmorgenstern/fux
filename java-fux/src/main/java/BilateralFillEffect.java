import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class BilateralFillEffect implements Effect {
    private PixelCoordinates coords;
    private Random random = new Random();
    
    // Effect parameters
    private double fillSpeed; // units per second
    private double brightness;
    private double holdTime; // seconds
    private double fadeOutTime; // seconds
    private String fillMode; // "horizontal", "vertical", "diagonal", "random"
    private boolean flipInsideOut; // if true, fill from center outward
    private int fps;
    
    // State
    private double cycleStartTime = 0;
    private int currentHue;
    private String currentMode;
    private boolean currentFlipped;
    private enum Phase { FILLING, HOLDING, FADING }
    private Phase currentPhase = Phase.FILLING;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fillSpeed = params.get("fill_speed").getAsDouble();
        this.brightness = params.get("brightness").getAsDouble();
        this.holdTime = params.get("hold_time").getAsDouble() / 1000.0; // ms to seconds
        this.fadeOutTime = params.get("fade_out_time").getAsDouble() / 1000.0; // ms to seconds
        this.fillMode = params.get("fill_mode").getAsString();
        this.flipInsideOut = params.has("flip_inside_out") ? params.get("flip_inside_out").getAsBoolean() : false;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 33;
        
        this.currentHue = random.nextInt(360);
        this.currentMode = getRandomMode();
        this.currentFlipped = flipInsideOut ? random.nextBoolean() : false;
        
        System.out.println("BilateralFillEffect initialized:");
        System.out.println("  Fill speed: " + fillSpeed);
        System.out.println("  Brightness: " + brightness);
        System.out.println("  Hold time: " + holdTime + "s");
        System.out.println("  Fade time: " + fadeOutTime + "s");
        System.out.println("  Fill mode: " + fillMode);
        System.out.println("  Flip inside/out: " + flipInsideOut);
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Calculate time within current cycle
        double cycleTime = timeSeconds - cycleStartTime;
        double fillDuration = 100.0 / fillSpeed; // Time to fill completely
        
        // Determine phase
        if (cycleTime < fillDuration) {
            currentPhase = Phase.FILLING;
        } else if (cycleTime < fillDuration + holdTime) {
            currentPhase = Phase.HOLDING;
        } else if (cycleTime < fillDuration + holdTime + fadeOutTime) {
            currentPhase = Phase.FADING;
        } else {
            // Start new cycle
            cycleStartTime = timeSeconds;
            currentHue = random.nextInt(360);
            currentMode = getRandomMode();
            currentFlipped = flipInsideOut ? random.nextBoolean() : false;
            currentPhase = Phase.FILLING;
            cycleTime = 0;
        }
        
        // Calculate progress and brightness based on phase
        double progress;
        double currentBrightness;
        
        switch (currentPhase) {
            case FILLING:
                progress = (cycleTime / fillDuration) * 100.0;
                currentBrightness = brightness;
                break;
            case HOLDING:
                progress = 100.0;
                currentBrightness = brightness;
                break;
            case FADING:
                progress = 100.0;
                double fadeProgress = (cycleTime - fillDuration - holdTime) / fadeOutTime;
                currentBrightness = brightness * (1.0 - fadeProgress);
                break;
            default:
                progress = 0;
                currentBrightness = 0;
        }
        
        // Find coordinate range for current mode
        double minCoord = Double.MAX_VALUE;
        double maxCoord = Double.MIN_VALUE;
        
        for (int i = 0; i < coords.getCount(); i++) {
            double coord = getCoordinate(coords.get(i), currentMode);
            minCoord = Math.min(minCoord, coord);
            maxCoord = Math.max(maxCoord, coord);
        }
        
        double range = maxCoord - minCoord;
        double center = (minCoord + maxCoord) / 2.0;
        double fillDistance = (range / 2.0) * (progress / 100.0);
        
        // Create color
        Color color = Color.getHSBColor(currentHue / 360f, 1.0f, (float)currentBrightness);
        
        // Apply bilateral fill
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate p = coords.get(i);
            double coord = getCoordinate(p, currentMode);
            double distFromCenter = Math.abs(coord - center);
            
            boolean shouldLight;
            if (currentFlipped) {
                // Inside-out: light pixels within distance from center
                shouldLight = distFromCenter <= fillDistance;
            } else {
                // Outside-in: light pixels beyond the unfilled distance
                double maxDist = range / 2.0;
                shouldLight = distFromCenter >= (maxDist - fillDistance);
            }
            
            if (shouldLight) {
                pixels.put(i, color);
            }
        }
        
        return pixels;
    }
    
    private double getCoordinate(PixelCoordinate p, String mode) {
        switch (mode) {
            case "horizontal": return p.getX();
            case "vertical": return p.getY();
            case "diagonal": return p.getX() + p.getY();
            case "diagonal_reverse": return p.getX() - p.getY();
            default: return p.getX();
        }
    }
    
    private String getRandomMode() {
        if (!fillMode.equals("random")) {
            return fillMode;
        }
        
        String[] modes = {"horizontal", "vertical", "diagonal", "diagonal_reverse"};
        return modes[random.nextInt(modes.length)];
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Bilateral Fill";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
