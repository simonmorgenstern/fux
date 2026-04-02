import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

/**
 * Color Chase effect - multiple vivid color groups race around the fox's outer contour
 * with pulsing brightness trails. Inner LEDs get a slow ambient color wash.
 */
public class ColorChaseEffect implements Effect {
    private PixelCoordinates coords;
    private int chaseCount;
    private double speed;       // LEDs per second
    private int tailLength;
    private double brightness;
    private int fps;

    // State
    private double headPosition;

    private static final int OUTLINE_COUNT = 91;   // indices 0-90
    private static final int INNER_START = 91;      // indices 91-267

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.chaseCount = params.has("chase_count") ? params.get("chase_count").getAsInt() : 3;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 60.0;
        this.tailLength = params.has("tail_length") ? params.get("tail_length").getAsInt() : 15;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;

        this.headPosition = 0.0;

        System.out.println("ColorChaseEffect initialized:");
        System.out.println("  Chase count: " + chaseCount);
        System.out.println("  Speed: " + speed + " LEDs/sec");
        System.out.println("  Tail length: " + tailLength);
        System.out.println("  Brightness: " + brightness);
        System.out.println("  FPS: " + fps);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        // Advance the head position
        headPosition += speed / fps;
        while (headPosition >= OUTLINE_COUNT) {
            headPosition -= OUTLINE_COUNT;
        }

        // Track the brightest value per outline LED so overlapping groups use the brighter one
        float[] ledBrightness = new float[OUTLINE_COUNT];
        float[] ledHue = new float[OUTLINE_COUNT];

        for (int g = 0; g < chaseCount; g++) {
            float groupHue = (float) g / chaseCount;
            double groupHead = (headPosition + (double) g * OUTLINE_COUNT / chaseCount) % OUTLINE_COUNT;

            for (int t = 0; t < tailLength; t++) {
                int ledIndex = (int) Math.floor(groupHead) - t;
                // Wrap around
                while (ledIndex < 0) {
                    ledIndex += OUTLINE_COUNT;
                }
                ledIndex = ledIndex % OUTLINE_COUNT;

                // Linear fade: 1.0 at head, 0.1 at tail end
                float fadeIntensity = 1.0f - 0.9f * ((float) t / (tailLength - 1));
                float finalBrightness = (float) (brightness * fadeIntensity);

                if (finalBrightness > ledBrightness[ledIndex]) {
                    ledBrightness[ledIndex] = finalBrightness;
                    ledHue[ledIndex] = groupHue;
                }
            }
        }

        // Write outline LEDs
        for (int i = 0; i < OUTLINE_COUNT; i++) {
            if (ledBrightness[i] > 0) {
                pixels.put(i, Color.getHSBColor(ledHue[i], 1.0f, ledBrightness[i]));
            }
        }

        // Inner LEDs (91-267): ambient glow with slowly rotating hue
        float ambientHue = (float) ((timeSeconds * 0.2) % 1.0);
        float ambientBrightness = (float) (brightness * 0.15);
        Color ambientColor = Color.getHSBColor(ambientHue, 1.0f, ambientBrightness);

        int totalLeds = coords.getCount();
        for (int i = INNER_START; i < totalLeds; i++) {
            pixels.put(i, ambientColor);
        }

        return pixels;
    }

    @Override
    public void dispose() {
        // Nothing to clean up
    }

    @Override
    public String getName() {
        return "Color Chase";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
