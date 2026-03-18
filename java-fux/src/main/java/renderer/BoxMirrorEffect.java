package renderer;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class BoxMirrorEffect implements Effect {
    private PixelCoordinates coords;
    private int changeInterval;
    private int fps;
    private Random random;
    
    // Precomputed boxes (quadrants) - for 268 LED fox
    private static final List<int[]> BOXES = Arrays.asList(
        // top-left
        new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65},
        // top-right
        new int[] {66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133},
        // bottom-left
        new int[] {134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200},
        // bottom-right
        new int[] {201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255, 256, 257, 258, 259, 260, 261, 262, 263, 264, 265, 266, 267}
    );
    
    // Mirror mapping (vertical mirror across center)
    private static final Map<Integer, Integer> MIRROR_MAP = new HashMap<>();
    static {
        MIRROR_MAP.put(0, 1);  // top-left <-> top-right
        MIRROR_MAP.put(1, 0);
        MIRROR_MAP.put(2, 3);  // bottom-left <-> bottom-right
        MIRROR_MAP.put(3, 2);
    }
    
    private int currentBox = -1;
    private Color currentColor;
    private long framesSinceChange = 0;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.changeInterval = params.has("changeInterval") ? params.get("changeInterval").getAsInt() : 60;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.random = new Random();
        
        selectNewBox();
        
        System.out.println("BoxMirrorEffect initialized:");
        System.out.println("  Change interval: " + changeInterval + " frames");
    }
    
    private void selectNewBox() {
        // Pick a random box (0-3)
        currentBox = random.nextInt(BOXES.size());
        
        // Generate a random color (full saturation, full brightness)
        float hue = random.nextFloat();
        currentColor = Color.getHSBColor(hue, 1.0f, 1.0f);
        
        framesSinceChange = 0;
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Light up the current box and its mirror
        int[] boxIndices = BOXES.get(currentBox);
        for (int idx : boxIndices) {
            if (idx < coords.getCount()) {
                pixels.put(idx, currentColor);
            }
        }
        
        int mirrorBox = MIRROR_MAP.get(currentBox);
        int[] mirrorIndices = BOXES.get(mirrorBox);
        for (int idx : mirrorIndices) {
            if (idx < coords.getCount()) {
                pixels.put(idx, currentColor);
            }
        }
        
        // Change box periodically
        framesSinceChange++;
        if (framesSinceChange >= changeInterval) {
            selectNewBox();
        }
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Box Mirror";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
