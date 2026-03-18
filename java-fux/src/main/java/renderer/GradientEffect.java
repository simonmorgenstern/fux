package renderer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class GradientEffect implements Effect {
    private PixelCoordinates coords;
    private String gradientAxis;  // "horizontal", "vertical", "radial"
    private double[] colorRange;  // HSV hue values
    private double speed;
    private double saturation;
    private double brightness;
    private int fps;
    
    // Coordinate bounds
    private double minX, maxX, minY, maxY;
    private double centerX, centerY, maxRadius;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.gradientAxis = params.has("gradient_axis") ? 
            params.get("gradient_axis").getAsString() : "horizontal";
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 20.0;
        this.saturation = params.has("saturation") ? params.get("saturation").getAsDouble() : 1.0;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 0.8;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 33;
        
        // Parse color range
        if (params.has("color_range")) {
            JsonArray colorArray = params.getAsJsonArray("color_range");
            colorRange = new double[colorArray.size()];
            for (int i = 0; i < colorArray.size(); i++) {
                colorRange[i] = colorArray.get(i).getAsDouble();
            }
        } else {
            // Default rainbow
            colorRange = new double[]{0, 60, 120, 180, 240, 300, 360};
        }
        
        // Calculate coordinate bounds
        this.minX = Double.MAX_VALUE;
        this.maxX = Double.MIN_VALUE;
        this.minY = Double.MAX_VALUE;
        this.maxY = Double.MIN_VALUE;
        
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate coord = coords.get(i);
            minX = Math.min(minX, coord.getX());
            maxX = Math.max(maxX, coord.getX());
            minY = Math.min(minY, coord.getY());
            maxY = Math.max(maxY, coord.getY());
        }
        
        this.centerX = (minX + maxX) / 2.0;
        this.centerY = (minY + maxY) / 2.0;
        
        // Calculate max radius for radial gradient
        this.maxRadius = 0;
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate coord = coords.get(i);
            double dx = coord.getX() - centerX;
            double dy = coord.getY() - centerY;
            double radius = Math.sqrt(dx * dx + dy * dy);
            maxRadius = Math.max(maxRadius, radius);
        }
        
        System.out.println("GradientEffect initialized:");
        System.out.println("  Gradient axis: " + gradientAxis);
        System.out.println("  Speed: " + speed);
        System.out.println("  Color range: " + colorRange.length + " hues");
        System.out.println("  X range: " + minX + " - " + maxX);
        System.out.println("  Y range: " + minY + " - " + maxY);
        System.out.println("  Center: (" + centerX + ", " + centerY + ")");
        System.out.println("  Max radius: " + maxRadius);
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Time-based offset for gradient movement (wraps around 0-1)
        double timeOffset = (timeSeconds * speed / 100.0) % 1.0;
        
        for (int ledIndex = 0; ledIndex < coords.getCount(); ledIndex++) {
            PixelCoordinate led = coords.get(ledIndex);
            
            // Calculate position along gradient axis (0-1)
            double position = 0;
            
            switch (gradientAxis) {
                case "horizontal":
                    position = (led.getX() - minX) / (maxX - minX);
                    break;
                case "vertical":
                    position = (led.getY() - minY) / (maxY - minY);
                    break;
                case "radial":
                    double dx = led.getX() - centerX;
                    double dy = led.getY() - centerY;
                    double radius = Math.sqrt(dx * dx + dy * dy);
                    position = radius / maxRadius;
                    break;
                default:
                    position = (led.getX() - minX) / (maxX - minX);
            }
            
            // Add time offset and wrap around
            position = (position + timeOffset) % 1.0;
            
            // Map position to hue value via interpolation
            double hue = interpolateColorRange(position);
            
            // Convert HSV to RGB
            Color color = hsvToRgb(hue, saturation, brightness);
            
            pixels.put(ledIndex, color);
        }
        
        return pixels;
    }
    
    /**
     * Interpolate hue value from color range based on position (0-1)
     */
    private double interpolateColorRange(double position) {
        if (colorRange.length == 1) {
            return colorRange[0];
        }
        
        // Scale position to color range index
        double scaledPos = position * (colorRange.length - 1);
        int index = (int) scaledPos;
        double fraction = scaledPos - index;
        
        // Get two adjacent hues
        double hue1 = colorRange[index];
        double hue2 = colorRange[Math.min(index + 1, colorRange.length - 1)];
        
        // Linear interpolation
        return hue1 + (hue2 - hue1) * fraction;
    }
    
    /**
     * Convert HSV to RGB
     * @param h Hue (0-360)
     * @param s Saturation (0-1)
     * @param v Value/Brightness (0-1)
     */
    private Color hsvToRgb(double h, double s, double v) {
        // Normalize hue to 0-1
        h = (h % 360.0) / 360.0;
        
        double r, g, b;
        
        if (s == 0) {
            // Grayscale
            r = g = b = v;
        } else {
            double var_h = h * 6.0;
            if (var_h == 6.0) var_h = 0.0;
            
            int var_i = (int) var_h;
            double var_1 = v * (1.0 - s);
            double var_2 = v * (1.0 - s * (var_h - var_i));
            double var_3 = v * (1.0 - s * (1.0 - (var_h - var_i)));
            
            switch (var_i) {
                case 0:  r = v;     g = var_3; b = var_1; break;
                case 1:  r = var_2; g = v;     b = var_1; break;
                case 2:  r = var_1; g = v;     b = var_3; break;
                case 3:  r = var_1; g = var_2; b = v;     break;
                case 4:  r = var_3; g = var_1; b = v;     break;
                default: r = v;     g = var_1; b = var_2; break;
            }
        }
        
        return new Color(
            (int) Math.round(r * 255),
            (int) Math.round(g * 255),
            (int) Math.round(b * 255)
        );
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Gradient Flow";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
