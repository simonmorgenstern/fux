import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class WaveCollapseEffect implements Effect {
    private PixelCoordinates coords;
    private double waveSpeed;
    private int waveCount;
    private double waveWidth;
    private double brightness;
    private int fps;

    // Ear tip positions
    private double rightEarX, rightEarY;
    private double leftEarX, leftEarY;

    // Precomputed distances from each LED to each ear tip
    private double[] distToRightEar;
    private double[] distToLeftEar;
    private double maxPossibleDistance;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.waveSpeed = params.has("wave_speed") ? params.get("wave_speed").getAsDouble() : 80.0;
        this.waveCount = params.has("wave_count") ? params.get("wave_count").getAsInt() : 3;
        this.waveWidth = params.has("wave_width") ? params.get("wave_width").getAsDouble() : 40.0;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;

        // Find the two ear tips: the two LEDs with lowest Y that are far apart in X
        int rightEarIndex = -1;
        int leftEarIndex = -1;
        double rightEarMinY = Double.MAX_VALUE;
        double leftEarMinY = Double.MAX_VALUE;

        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate p = coords.get(i);
            if (p.getX() > 230) {
                if (p.getY() < rightEarMinY) {
                    rightEarMinY = p.getY();
                    rightEarIndex = i;
                }
            } else {
                if (p.getY() < leftEarMinY) {
                    leftEarMinY = p.getY();
                    leftEarIndex = i;
                }
            }
        }

        rightEarX = coords.get(rightEarIndex).getX();
        rightEarY = coords.get(rightEarIndex).getY();
        leftEarX = coords.get(leftEarIndex).getX();
        leftEarY = coords.get(leftEarIndex).getY();

        // Precompute distances
        int count = coords.getCount();
        distToRightEar = new double[count];
        distToLeftEar = new double[count];
        maxPossibleDistance = 0;

        for (int i = 0; i < count; i++) {
            PixelCoordinate p = coords.get(i);
            double dx1 = p.getX() - rightEarX;
            double dy1 = p.getY() - rightEarY;
            distToRightEar[i] = Math.sqrt(dx1 * dx1 + dy1 * dy1);

            double dx2 = p.getX() - leftEarX;
            double dy2 = p.getY() - leftEarY;
            distToLeftEar[i] = Math.sqrt(dx2 * dx2 + dy2 * dy2);

            maxPossibleDistance = Math.max(maxPossibleDistance, Math.max(distToRightEar[i], distToLeftEar[i]));
        }

        System.out.println("WaveCollapseEffect initialized:");
        System.out.println("  Right ear tip: index " + rightEarIndex + " (" + rightEarX + ", " + rightEarY + ")");
        System.out.println("  Left ear tip: index " + leftEarIndex + " (" + leftEarX + ", " + leftEarY + ")");
        System.out.println("  Max distance: " + maxPossibleDistance);
        System.out.println("  Wave speed: " + waveSpeed);
        System.out.println("  Wave count: " + waveCount);
        System.out.println("  Wave width: " + waveWidth);
        System.out.println("  Brightness: " + brightness);
        System.out.println("  FPS: " + fps);
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        // Total cycle distance for wave wrapping
        double cycleDistance = maxPossibleDistance + waveWidth;
        // Spacing between staggered wave fronts
        double waveStagger = cycleDistance / waveCount;

        for (int ledIndex = 0; ledIndex < coords.getCount(); ledIndex++) {
            double rightIntensity = 0.0;
            double leftIntensity = 0.0;

            // Check each wave front from the right ear
            for (int w = 0; w < waveCount; w++) {
                double frontDist = (timeSeconds * waveSpeed + w * waveStagger) % cycleDistance;
                double distFromWave = Math.abs(distToRightEar[ledIndex] - frontDist);
                if (distFromWave < waveWidth / 2.0) {
                    double intensity = Math.cos(Math.PI * distFromWave / waveWidth);
                    rightIntensity = Math.max(rightIntensity, intensity);
                }
            }

            // Check each wave front from the left ear
            for (int w = 0; w < waveCount; w++) {
                double frontDist = (timeSeconds * waveSpeed + w * waveStagger) % cycleDistance;
                double distFromWave = Math.abs(distToLeftEar[ledIndex] - frontDist);
                if (distFromWave < waveWidth / 2.0) {
                    double intensity = Math.cos(Math.PI * distFromWave / waveWidth);
                    leftIntensity = Math.max(leftIntensity, intensity);
                }
            }

            if (rightIntensity <= 0 && leftIntensity <= 0) {
                continue;
            }

            float hue;
            float saturation;
            float bright;

            boolean bothActive = rightIntensity > 0 && leftIntensity > 0;

            if (bothActive) {
                // Interference: blend hues and boost brightness, shift toward white
                double totalIntensity = (rightIntensity + leftIntensity) * 1.5;
                // Weighted hue blend between cyan (0.5) and magenta (0.85)
                double hueBlend = (rightIntensity * 0.85 + leftIntensity * 0.5) / (rightIntensity + leftIntensity);
                hue = (float) hueBlend;
                // Reduce saturation toward white as overlap increases
                double overlapStrength = Math.min(rightIntensity, leftIntensity);
                saturation = (float) Math.max(0.0, 1.0 - overlapStrength * 0.6);
                bright = (float) Math.min(1.0, totalIntensity * brightness);
            } else if (rightIntensity > 0) {
                hue = 0.85f;
                saturation = 1.0f;
                bright = (float) Math.min(1.0, rightIntensity * brightness);
            } else {
                hue = 0.5f;
                saturation = 1.0f;
                bright = (float) Math.min(1.0, leftIntensity * brightness);
            }

            Color color = Color.getHSBColor(hue, saturation, bright);

            if (color.getRed() > 5 || color.getGreen() > 5 || color.getBlue() > 5) {
                pixels.put(ledIndex, color);
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
        return "Wave Collapse";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
