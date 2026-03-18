import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class RainEffect implements Effect {
    private PixelCoordinates coords;
    private List<Raindrop> raindrops;
    private Random random;
    private int dropCount;
    private double speedMin;
    private double speedMax;
    private int trailLength;
    private List<Color> colors;
    private int fps;
    private double maxY;
    private double minY;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        
        this.dropCount = params.get("drop_count").getAsInt();
        this.speedMin = params.get("speed_min").getAsDouble();
        this.speedMax = params.get("speed_max").getAsDouble();
        this.trailLength = params.get("trail_length").getAsInt();
        
        // Find Y range by iterating through all coordinates
        this.minY = Double.MAX_VALUE;
        this.maxY = Double.MIN_VALUE;
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate coord = coords.get(i);
            minY = Math.min(minY, coord.getY());
            maxY = Math.max(maxY, coord.getY());
        }
        
        // Parse colors
        this.colors = new ArrayList<>();
        JsonArray colorsArray = params.getAsJsonArray("colors");
        for (int i = 0; i < colorsArray.size(); i++) {
            String colorName = colorsArray.get(i).getAsString();
            colors.add(parseColor(colorName));
        }
        
        // Spawn initial raindrops
        this.raindrops = new ArrayList<>();
        for (int i = 0; i < dropCount; i++) {
            raindrops.add(spawnRaindrop());
        }
        
        System.out.println("RainEffect initialized:");
        System.out.println("  Drop count: " + dropCount);
        System.out.println("  Speed range: " + speedMin + " - " + speedMax);
        System.out.println("  Trail length: " + trailLength);
        System.out.println("  Y range: " + minY + " - " + maxY);
        System.out.println("  Colors: " + colorsArray);
    }
    
    private Color parseColor(String name) {
        switch (name.toLowerCase()) {
            case "blue": return new Color(0, 100, 255);
            case "cyan": return new Color(0, 200, 255);
            case "light_blue": return new Color(100, 180, 255);
            case "white": return Color.WHITE;
            default: return new Color(0, 150, 255);
        }
    }
    
    private Raindrop spawnRaindrop() {
        // Random X position across the fox - pick a random LED's X coordinate
        PixelCoordinate randomCoord = coords.get(random.nextInt(coords.getCount()));
        double x = randomCoord.getX();
        // Start at top (minY is top, maxY is bottom on your fox)
        double y = minY;
        double speed = speedMin + random.nextDouble() * (speedMax - speedMin);
        Color color = colors.get(random.nextInt(colors.size()));
        
        return new Raindrop(x, y, speed, color);
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Update and render each raindrop
        for (int i = 0; i < raindrops.size(); i++) {
            Raindrop drop = raindrops.get(i);
            
            // Move raindrop down (increase Y = down on the fox)
            drop.y += drop.speed;
            
            // If reached bottom, respawn at top
            if (drop.y > maxY) {
                raindrops.set(i, spawnRaindrop());
                continue;
            }
            
            // Render raindrop trail
            for (int j = 0; j < coords.getCount(); j++) {
                PixelCoordinate led = coords.get(j);
                
                // Check if LED is close to raindrop X position
                double dx = led.getX() - drop.x;
                if (Math.abs(dx) > 25) continue; // Too far horizontally
                
                // Check if LED is in the vertical trail
                double dy = led.getY() - drop.y;
                
                // Trail extends UPWARD (negative Y direction) from the head
                if (dy < -50 || dy > 5) continue; // Not in trail range
                
                // Calculate distance from raindrop center
                double distance = Math.sqrt(dx * dx + dy * dy);
                
                if (distance < 25) {
                    // Calculate brightness based on Y distance (head=bright, tail=dim)
                    double trailPosition = Math.abs(dy) / 50.0; // 0.0 at head, 1.0 at tail
                    double intensity = 1.0 - trailPosition;
                    
                    // Also fade based on X distance from center
                    intensity *= Math.max(0, 1.0 - (Math.abs(dx) / 25.0));
                    
                    if (intensity > 0.1) {
                        int r = (int)(drop.color.getRed() * intensity);
                        int g = (int)(drop.color.getGreen() * intensity);
                        int b = (int)(drop.color.getBlue() * intensity);
                        
                        Color currentColor = pixels.get(j);
                        if (currentColor == null) {
                            pixels.put(j, new Color(r, g, b));
                        } else {
                            // Blend if multiple drops hit same LED
                            int newR = Math.min(255, currentColor.getRed() + r);
                            int newG = Math.min(255, currentColor.getGreen() + g);
                            int newB = Math.min(255, currentColor.getBlue() + b);
                            pixels.put(j, new Color(newR, newG, newB));
                        }
                    }
                }
            }
        }
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        raindrops.clear();
    }
    
    @Override
    public String getName() {
        return "Rain";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
    
    private static class Raindrop {
        double x;
        double y;
        double speed;
        Color color;
        
        Raindrop(double x, double y, double speed, Color color) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.color = color;
        }
    }
}
