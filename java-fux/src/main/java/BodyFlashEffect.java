import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class BodyFlashEffect implements Effect {
    private PixelCoordinates coords;
    private int fps;
    private double flashSpeed;
    private double brightness;
    private Random random;

    // 6 body zones
    private static final int ZONE_RIGHT_EAR = 0;
    private static final int ZONE_LEFT_EAR = 1;
    private static final int ZONE_HEAD = 2;
    private static final int ZONE_UPPER_BODY = 3;
    private static final int ZONE_LOWER_BODY = 4;
    private static final int ZONE_TAIL = 5;
    private static final int ZONE_COUNT = 6;

    private int[][] zoneLeds; // zoneLeds[zone] = array of LED indices
    private float[] zoneHues; // current hue per zone
    private double[] zoneOffsets; // timing offset per zone (0..1)
    private double[] zoneLastChangeTime; // last color change time per zone

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.flashSpeed = params.has("flash_speed") ? params.get("flash_speed").getAsDouble() : 3.0;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;

        // Classify LEDs into zones
        List<List<Integer>> zones = new ArrayList<>();
        for (int z = 0; z < ZONE_COUNT; z++) {
            zones.add(new ArrayList<>());
        }

        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate p = coords.get(i);
            double x = p.getX();
            double y = p.getY();
            int zone = classifyZone(i, x, y);
            zones.get(zone).add(i);
        }

        zoneLeds = new int[ZONE_COUNT][];
        for (int z = 0; z < ZONE_COUNT; z++) {
            List<Integer> list = zones.get(z);
            zoneLeds[z] = new int[list.size()];
            for (int j = 0; j < list.size(); j++) {
                zoneLeds[z][j] = list.get(j);
            }
        }

        // Initialize hues and timing offsets
        zoneHues = new float[ZONE_COUNT];
        zoneOffsets = new double[ZONE_COUNT];
        zoneLastChangeTime = new double[ZONE_COUNT];

        for (int z = 0; z < ZONE_COUNT; z++) {
            zoneHues[z] = random.nextFloat();
            zoneOffsets[z] = (double) z / ZONE_COUNT; // stagger evenly
            zoneLastChangeTime[z] = -zoneOffsets[z] / flashSpeed; // offset start times
        }

        System.out.println("BodyFlashEffect initialized:");
        System.out.println("  Flash speed: " + flashSpeed);
        System.out.println("  Brightness: " + brightness);
        for (int z = 0; z < ZONE_COUNT; z++) {
            System.out.println("  Zone " + z + ": " + zoneLeds[z].length + " LEDs");
        }
    }

    private int classifyZone(int index, double x, double y) {
        // Right ear: top right area
        if (y < 50 && x > 400) {
            return ZONE_RIGHT_EAR;
        }
        // Left ear: top left area
        if (y < 50 && x < 70) {
            return ZONE_LEFT_EAR;
        }
        // Head/face: upper area excluding ears
        if (y < 200) {
            return ZONE_HEAD;
        }
        // Upper body
        if (y < 350) {
            return ZONE_UPPER_BODY;
        }
        // Lower body/legs
        if (y < 500) {
            return ZONE_LOWER_BODY;
        }
        // Tail
        return ZONE_TAIL;
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        double cycleDuration = 1.0 / flashSpeed;

        for (int z = 0; z < ZONE_COUNT; z++) {
            // Time since last color change for this zone
            double elapsed = timeSeconds - zoneLastChangeTime[z];

            // Check if it's time for a new color
            if (elapsed >= cycleDuration) {
                // Pick a new hue with at least 60 degrees (1/6 of hue circle) contrast
                float oldHue = zoneHues[z];
                float newHue;
                float hueDist;
                do {
                    newHue = random.nextFloat();
                    hueDist = Math.abs(newHue - oldHue);
                    if (hueDist > 0.5f) hueDist = 1.0f - hueDist;
                } while (hueDist < 1.0f / 6.0f);

                zoneHues[z] = newHue;
                zoneLastChangeTime[z] = timeSeconds;
                elapsed = 0;
            }

            // Calculate pulse intensity within the cycle
            double progress = elapsed / cycleDuration; // 0..1
            double pulseIntensity;

            if (progress < 0.2) {
                // Ramp up quickly (first 20%)
                pulseIntensity = progress / 0.2;
            } else if (progress < 0.5) {
                // Hold at peak (20% to 50%)
                pulseIntensity = 1.0;
            } else {
                // Fade down (50% to 100%)
                pulseIntensity = 1.0 - (progress - 0.5) / 0.5;
            }

            float b = (float) (brightness * pulseIntensity);
            b = Math.max(0.0f, Math.min(1.0f, b));

            Color color = Color.getHSBColor(zoneHues[z], 1.0f, b);

            // Apply color to all LEDs in this zone
            for (int led : zoneLeds[z]) {
                pixels.put(led, color);
            }
        }

        return pixels;
    }

    @Override
    public void dispose() {
        // Nothing to clean up
    }

    @Override
    public String getName() {
        return "Body Flash";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
