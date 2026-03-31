import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * PreviewRenderer renders effect frames to in-memory pixel grid for GIF generation.
 * Renders only the first 268 LEDs (main strip) to a configurable resolution canvas.
 */
public class PreviewRenderer {
    private static final int MAIN_LED_COUNT = 268;
    
    private final PixelCoordinates coordinates;
    private final int pixelSize;
    private final int canvasWidth;
    private final int canvasHeight;
    private final double scaleX;
    private final double scaleY;
    private final double offsetX;
    private final double offsetY;
    
    /**
     * Initialize the renderer with a coordinate system and pixel size.
     * @param pixelSize Size of each rendered pixel (8-16 recommended)
     */
    public PreviewRenderer(int pixelSize) {
        this.pixelSize = pixelSize;
        
        // Load coordinates using shared fallback logic
        this.coordinates = PixelCoordinates.loadWithFallback();
        
        if (coordinates != null && coordinates.getCount() > 0) {
            // Calculate canvas dimensions based on coordinate bounds
            // Find bounds of the first 268 LEDs
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

            for (int i = 0; i < Math.min(MAIN_LED_COUNT, coordinates.getCount()); i++) {
                PixelCoordinate coord = coordinates.get(i);
                minX = Math.min(minX, coord.getX());
                maxX = Math.max(maxX, coord.getX());
                minY = Math.min(minY, coord.getY());
                maxY = Math.max(maxY, coord.getY());
            }

            double width = maxX - minX;
            double height = maxY - minY;

            // Guard against degenerate bounds
            if (width <= 0) width = 1;
            if (height <= 0) height = 1;
            
            // Add 10% padding on each side
            double padding = 0.1;
            minX -= width * padding;
            minY -= height * padding;
            width *= (1 + 2 * padding);
            height *= (1 + 2 * padding);
            
            this.canvasWidth = (int) (width / pixelSize) + 1;
            this.canvasHeight = (int) (height / pixelSize) + 1;
            this.scaleX = width / canvasWidth;
            this.scaleY = height / canvasHeight;
            this.offsetX = minX;
            this.offsetY = minY;
            
            System.out.println("PreviewRenderer canvas: " + canvasWidth + "x" + canvasHeight + 
                " pixels (size=" + pixelSize + "), bounds: (" + minX + "," + minY + ") to (" + 
                (minX + width) + "," + (minY + height) + ")");
        } else {
            this.canvasWidth = 16;
            this.canvasHeight = 16;
            this.scaleX = 1.0;
            this.scaleY = 1.0;
            this.offsetX = 0;
            this.offsetY = 0;
            System.err.println("Warning: Using default canvas size due to missing coordinates");
        }
    }
    
    /**
     * Render a single frame from an effect to a BufferedImage.
     */
    public BufferedImage renderFrame(Effect effect, long frameNumber, double timeSeconds) {
        BufferedImage image = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        
        // Start with black background
        for (int y = 0; y < canvasHeight; y++) {
            for (int x = 0; x < canvasWidth; x++) {
                image.setRGB(x, y, Color.BLACK.getRGB());
            }
        }
        
        if (coordinates == null) {
            return image;
        }
        
        // Get frame data from effect
        Map<Integer, Color> pixels = effect.renderFrame(frameNumber, timeSeconds);
        
        // Render pixels - map LED coordinates to canvas
        for (Map.Entry<Integer, Color> entry : pixels.entrySet()) {
            int ledIndex = entry.getKey();
            Color color = entry.getValue();
            
            // Only render first 268 LEDs (main strip)
            if (ledIndex >= MAIN_LED_COUNT || ledIndex >= coordinates.getCount()) {
                continue;
            }
            
            PixelCoordinate coord = coordinates.get(ledIndex);
            if (coord == null) {
                continue;
            }
            
            // Map LED coordinate to canvas position
            int canvasX = (int) ((coord.getX() - offsetX) / scaleX);
            int canvasY = (int) ((coord.getY() - offsetY) / scaleY);
            
            // Clamp to canvas bounds
            canvasX = Math.max(0, Math.min(canvasWidth - 1, canvasX));
            canvasY = Math.max(0, Math.min(canvasHeight - 1, canvasY));
            
            // Set pixel with anti-aliasing by filling a small area
            // Smaller pixelSize = larger canvas = need bigger radius to fill it
            // Use inverse scaling: radius proportional to 1/pixelSize
            int radius = Math.max(1, Math.round(8.0f / pixelSize));
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    int px = canvasX + dx;
                    int py = canvasY + dy;
                    if (px >= 0 && px < canvasWidth && py >= 0 && py < canvasHeight) {
                        image.setRGB(px, py, color.getRGB());
                    }
                }
            }
        }
        
        return image;
    }
    
    /**
     * Render a sequence of frames for an effect.
     */
    public List<BufferedImage> renderFrames(Effect effect, int frameCount, int fps) {
        List<BufferedImage> frames = new ArrayList<>();
        
        for (int i = 0; i < frameCount; i++) {
            double timeSeconds = i / (double) fps;
            BufferedImage frame = renderFrame(effect, i, timeSeconds);
            frames.add(frame);
        }
        
        return frames;
    }
    
    public int getCanvasWidth() {
        return canvasWidth;
    }
    
    public int getCanvasHeight() {
        return canvasHeight;
    }
    
    public PixelCoordinates getCoordinates() {
        return coordinates;
    }
}
