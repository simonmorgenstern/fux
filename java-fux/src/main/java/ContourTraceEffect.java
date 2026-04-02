import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

/**
 * Contour Trace effect - a glowing light traces the fox's outline (indices 0-90)
 * with a fading rainbow trail, plus a dimmer secondary trace on inner LEDs.
 */
public class ContourTraceEffect implements Effect {
    private PixelCoordinates coords;
    private double speed;       // LEDs per second
    private int trailLength;
    private double hueShift;
    private double brightness;
    private int fps;

    // Contour boundaries
    private static final int CONTOUR_START = 0;
    private static final int CONTOUR_END = 90;
    private static final int CONTOUR_LENGTH = CONTOUR_END - CONTOUR_START + 1; // 91 LEDs
    private static final int INNER_START = 91;

    // State
    private double headPosition;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 30.0;
        this.trailLength = params.has("trail_length") ? params.get("trail_length").getAsInt() : 20;
        this.hueShift = params.has("hue_shift") ? params.get("hue_shift").getAsDouble() : 0.3;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;

        this.headPosition = 0.0;

        System.out.println("ContourTraceEffect initialized:");
        System.out.println("  Speed: " + speed + " LEDs/sec");
        System.out.println("  Trail length: " + trailLength);
        System.out.println("  Hue shift: " + hueShift);
        System.out.println("  Brightness: " + brightness);
        System.out.println("  FPS: " + fps);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        // Advance head position
        headPosition += speed / fps;
        if (headPosition >= CONTOUR_LENGTH) {
            headPosition -= CONTOUR_LENGTH;
        }

        // Base hue rotates slowly over time
        float baseHue = (float) ((timeSeconds * 0.1) % 1.0);

        // --- Primary trace on outer contour (indices 0-90) ---
        renderTrace(pixels, headPosition, baseHue, (float) brightness, CONTOUR_START, CONTOUR_LENGTH);

        // --- Second head offset by half the contour for symmetry ---
        double secondHead = headPosition + CONTOUR_LENGTH / 2.0;
        if (secondHead >= CONTOUR_LENGTH) {
            secondHead -= CONTOUR_LENGTH;
        }
        renderTrace(pixels, secondHead, baseHue, (float) brightness, CONTOUR_START, CONTOUR_LENGTH);

        // --- Subtle inner trace (indices 91-267) at half speed, 30% brightness ---
        int innerCount = coords.getCount() - INNER_START;
        if (innerCount > 0) {
            double innerHead = (headPosition * 0.5) % innerCount;
            float innerBrightness = (float) (brightness * 0.3);
            renderTrace(pixels, innerHead, baseHue, innerBrightness, INNER_START, innerCount);
        }

        return pixels;
    }

    /**
     * Renders a single trace (head + fading trail) within a segment of LEDs.
     *
     * @param pixels       output map
     * @param head         floating-point head position within the segment (0-based)
     * @param baseHue      hue at the head
     * @param maxBrightness peak brightness for this trace
     * @param startIndex   first LED index of the segment
     * @param segmentLength number of LEDs in the segment
     */
    private void renderTrace(Map<Integer, Color> pixels, double head, float baseHue,
                             float maxBrightness, int startIndex, int segmentLength) {
        int headIdx = (int) head;

        for (int i = 0; i < trailLength; i++) {
            // Walk backwards from head through the loop
            int segPos = headIdx - i;
            if (segPos < 0) {
                segPos += segmentLength;
            }
            int ledIndex = startIndex + segPos;

            if (ledIndex < 0 || ledIndex >= coords.getCount()) {
                continue;
            }

            // Linear fade: 1.0 at head, 0.0 at end of trail
            float fadeIntensity = 1.0f - ((float) i / trailLength);

            // Rainbow hue shifts along the trail
            float hue = (float) ((baseHue + hueShift * ((double) i / trailLength)) % 1.0);

            Color color = Color.getHSBColor(hue, 1.0f, maxBrightness * fadeIntensity);

            // Only overwrite if this pixel is brighter (two traces may overlap)
            if (pixels.containsKey(ledIndex)) {
                Color existing = pixels.get(ledIndex);
                if (colorBrightness(existing) >= colorBrightness(color)) {
                    continue;
                }
            }
            pixels.put(ledIndex, color);
        }
    }

    private float colorBrightness(Color c) {
        return (c.getRed() + c.getGreen() + c.getBlue()) / (3.0f * 255.0f);
    }

    @Override
    public void dispose() {
        // Nothing to clean up
    }

    @Override
    public String getName() {
        return "ContourTrace";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
