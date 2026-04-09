import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class MeteorShowerEffect implements Effect {
    private PixelCoordinates coords;
    private List<Meteor> meteors;
    private Random random;
    private int meteorCount;
    private double speedMin;
    private double speedMax;
    private int trailLength;
    private List<Color> colors;
    private int fps;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        
        this.meteorCount = params.get("meteor_count").getAsInt();
        this.speedMin = params.get("speed_min").getAsDouble();
        this.speedMax = params.get("speed_max").getAsDouble();
        this.trailLength = params.get("trail_length").getAsInt();
        
        // Parse colors
        this.colors = new ArrayList<>();
        JsonArray colorsArray = params.getAsJsonArray("colors");
        for (int i = 0; i < colorsArray.size(); i++) {
            String colorName = colorsArray.get(i).getAsString();
            colors.add(parseColor(colorName));
        }
        
        // Spawn initial meteors
        this.meteors = new ArrayList<>();
        for (int i = 0; i < meteorCount; i++) {
            meteors.add(spawnMeteor());
        }
        
        System.out.println("MeteorShowerEffect initialized:");
        System.out.println("  Meteor count: " + meteorCount);
        System.out.println("  Speed range: " + speedMin + " - " + speedMax);
        System.out.println("  Trail length: " + trailLength + " LEDs");
        System.out.println("  Colors: " + colorsArray);
    }
    
    private Color parseColor(String name) {
        switch (name.toLowerCase()) {
            case "white": return Color.WHITE;
            case "blue": return new Color(100, 150, 255);
            case "gold": return new Color(255, 215, 0);
            case "cyan": return Color.CYAN;
            case "purple": return new Color(200, 100, 255);
            default: return Color.WHITE;
        }
    }
    
    private Meteor spawnMeteor() {
        int startLED = random.nextInt(coords.getCount());
        int targetLED = random.nextInt(coords.getCount());
        double speed = speedMin + random.nextDouble() * (speedMax - speedMin);
        Color color = colors.get(random.nextInt(colors.size()));
        
        return new Meteor(startLED, targetLED, speed, color);
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Update and render each meteor
        for (int i = 0; i < meteors.size(); i++) {
            Meteor meteor = meteors.get(i);
            
            // Move meteor
            meteor.progress += meteor.speed;
            
            // If reached target, respawn
            if (meteor.progress >= 1.0) {
                meteors.set(i, spawnMeteor());
                continue;
            }
            
            // Calculate current position in 2D space
            PixelCoordinate start = coords.get(meteor.startLED);
            PixelCoordinate target = coords.get(meteor.targetLED);
            
            double currentX = start.getX() + (target.getX() - start.getX()) * meteor.progress;
            double currentY = start.getY() + (target.getY() - start.getY()) * meteor.progress;
            
            // Render meteor trail
            // Find LEDs close to the meteor path and light them up
            for (int j = 0; j < coords.getCount(); j++) {
                PixelCoordinate led = coords.get(j);
                
                // Calculate distance from LED to current meteor position
                double dx = led.getX() - currentX;
                double dy = led.getY() - currentY;
                double distance = Math.sqrt(dx * dx + dy * dy);
                
                // Check if LED is in the trail
                for (int t = 0; t < trailLength; t++) {
                    double trailPos = meteor.progress - (t * 0.015); // Trail positions
                    
                    if (trailPos < 0) break; // Trail hasn't started yet
                    
                    double trailX = start.getX() + (target.getX() - start.getX()) * trailPos;
                    double trailY = start.getY() + (target.getY() - start.getY()) * trailPos;
                    
                    double trailDx = led.getX() - trailX;
                    double trailDy = led.getY() - trailY;
                    double trailDist = Math.sqrt(trailDx * trailDx + trailDy * trailDy);
                    
                    // LED is close to this trail position
                    if (trailDist < 25) { // Within 25 pixels
                        // Calculate brightness based on position in trail (head=bright, tail=dim)
                        double intensity = 1.0 - ((double)t / trailLength);
                        // Also fade based on distance from path
                        intensity *= Math.max(0, 1.0 - (trailDist / 25.0));
                        
                        if (intensity > 0.1) {
                            int r = (int)(meteor.color.getRed() * intensity);
                            int g = (int)(meteor.color.getGreen() * intensity);
                            int b = (int)(meteor.color.getBlue() * intensity);
                            
                            Color currentColor = pixels.get(j);
                            if (currentColor == null) {
                                pixels.put(j, new Color(r, g, b));
                            } else {
                                // Blend if multiple meteors hit same LED
                                int newR = Math.min(255, currentColor.getRed() + r);
                                int newG = Math.min(255, currentColor.getGreen() + g);
                                int newB = Math.min(255, currentColor.getBlue() + b);
                                pixels.put(j, new Color(newR, newG, newB));
                            }
                        }
                        break; // Only count LED once per meteor
                    }
                }
            }
        }
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        meteors.clear();
    }
    
    @Override
    public String getName() {
        return "Meteor Shower";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
    
    private static class Meteor {
        int startLED;
        int targetLED;
        double progress; // 0.0 to 1.0
        double speed;    // Progress increment per frame
        Color color;
        
        Meteor(int startLED, int targetLED, double speed, Color color) {
            this.startLED = startLED;
            this.targetLED = targetLED;
            this.progress = 0.0;
            this.speed = speed;
            this.color = color;
        }
    }
}
