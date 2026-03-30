import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

/**
 * Twinkle effect - Random stars twinkling and fading
 */
public class TwinkleEffect implements Effect {
    private PixelCoordinates coords;
    private int density;  // Number of active twinkles
    private double fadeSpeed;
    private List<Color> colors;
    private int fps;
    private Random random;
    
    // Twinkle state: LED index -> brightness (0.0 to 1.0)
    private Map<Integer, TwinkleStar> stars;
    
    private static class TwinkleStar {
        Color color;
        double brightness;
        double fadeRate;  // How fast it fades (positive = fading out, negative = brightening)
        
        TwinkleStar(Color color) {
            this.color = color;
            this.brightness = 0.0;
            this.fadeRate = 0.0;
        }
    }
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.density = params.has("density") ? params.get("density").getAsInt() : 20;
        this.fadeSpeed = params.has("fade_speed") ? params.get("fade_speed").getAsDouble() : 0.03;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.random = new Random();
        this.stars = new HashMap<>();
        
        // Parse colors
        this.colors = new ArrayList<>();
        if (params.has("colors")) {
            JsonArray colorsArray = params.getAsJsonArray("colors");
            for (int i = 0; i < colorsArray.size(); i++) {
                String colorName = colorsArray.get(i).getAsString();
                colors.add(parseColor(colorName));
            }
        } else {
            // Default: cool white stars
            colors.add(new Color(255, 255, 255));
            colors.add(new Color(200, 220, 255));
            colors.add(new Color(255, 240, 200));
        }
        
        System.out.println("TwinkleEffect initialized:");
        System.out.println("  Density: " + density + " active stars");
        System.out.println("  Fade speed: " + fadeSpeed);
        System.out.println("  Colors: " + colors.size());
    }
    
    private Color parseColor(String name) {
        switch (name.toLowerCase()) {
            case "white": return Color.WHITE;
            case "warm_white": return new Color(255, 240, 200);
            case "cool_white": return new Color(200, 220, 255);
            case "blue": return new Color(100, 150, 255);
            case "yellow": return new Color(255, 255, 150);
            case "red": return new Color(255, 100, 100);
            case "green": return new Color(100, 255, 100);
            case "purple": return new Color(200, 100, 255);
            default: return Color.WHITE;
        }
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Update existing stars
        Iterator<Map.Entry<Integer, TwinkleStar>> iter = stars.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, TwinkleStar> entry = iter.next();
            TwinkleStar star = entry.getValue();
            
            // Update brightness
            star.brightness += star.fadeRate;
            
            // Check if star has faded out
            if (star.brightness <= 0.0) {
                iter.remove();
                continue;
            }
            
            // Clamp brightness
            if (star.brightness > 1.0) {
                star.brightness = 1.0;
                // Start fading out
                star.fadeRate = -fadeSpeed * (0.5 + random.nextDouble() * 0.5);
            }
            
            // Render star
            int led = entry.getKey();
            Color fadedColor = new Color(
                (int)(star.color.getRed() * star.brightness),
                (int)(star.color.getGreen() * star.brightness),
                (int)(star.color.getBlue() * star.brightness)
            );
            pixels.put(led, fadedColor);
        }
        
        // Spawn new stars to maintain density
        while (stars.size() < density) {
            int ledIndex = random.nextInt(coords.getCount());
            
            // Don't spawn on already-active LED
            if (stars.containsKey(ledIndex)) {
                continue;
            }
            
            TwinkleStar newStar = new TwinkleStar(colors.get(random.nextInt(colors.size())));
            newStar.brightness = 0.0;
            // Brighten at varying speeds
            newStar.fadeRate = fadeSpeed * (0.5 + random.nextDouble() * 1.5);
            
            stars.put(ledIndex, newStar);
        }
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        stars.clear();
    }
    
    @Override
    public String getName() {
        return "Twinkle";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
