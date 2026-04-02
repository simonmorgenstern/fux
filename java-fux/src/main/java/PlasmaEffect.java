import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class PlasmaEffect implements Effect {
    private PixelCoordinates coords;
    private double speed;
    private double scale;
    private float saturation;
    private float brightness;
    private int fps;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 1.0;
        this.scale = params.has("scale") ? params.get("scale").getAsDouble() : 0.01;
        this.saturation = params.has("saturation") ? params.get("saturation").getAsFloat() : 0.9f;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsFloat() : 1.0f;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;

        System.out.println("PlasmaEffect initialized:");
        System.out.println("  Speed: " + speed);
        System.out.println("  Scale: " + scale);
        System.out.println("  Saturation: " + saturation);
        System.out.println("  Brightness: " + brightness);
        System.out.println("  LEDs: " + coords.getCount());
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        double time = timeSeconds;
        double cx = coords.getCenterX();
        double cy = coords.getCenterY();

        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate p = coords.get(i);
            double x = p.getX();
            double y = p.getY();

            // Four overlapping sine wave components
            double v1 = Math.sin(x * scale + time * speed);
            double v2 = Math.sin(y * scale * 1.3 + time * speed * 0.7);
            double v3 = Math.sin((x + y) * scale * 0.7 + time * speed * 1.3);
            double dx = x - cx;
            double dy = y - cy;
            double v4 = Math.sin(Math.sqrt(dx * dx + dy * dy) * scale * 2.0 + time * speed * 0.5);

            // Average to [-1, 1], then map to [0, 1] for hue
            double plasma = (v1 + v2 + v3 + v4) / 4.0;
            float hue = (float) ((plasma + 1.0) / 2.0);

            pixels.put(i, Color.getHSBColor(hue, saturation, brightness));
        }

        return pixels;
    }

    @Override
    public void dispose() {
        // Nothing to clean up
    }

    @Override
    public String getName() {
        return "Plasma";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
