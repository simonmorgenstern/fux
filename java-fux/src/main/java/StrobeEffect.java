import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Strobe — full-body colour flashes.
 *
 * Free running it flashes at a fixed {@code flash_rate} in Hz. In MUSIC mode
 * {@link EffectEngine} installs the live {@link BeatClock} and the flash grid
 * switches to {@code flashes_per_beat}, so every hit lands on the beat instead
 * of drifting against it. Without a clock the original wall-clock timing is
 * kept, so queue, idle and preview renders are unchanged.
 */
public class StrobeEffect implements Effect, BeatAware {
    private PixelCoordinates coords;
    private double flashRate;
    private double onRatio;
    private double whiteChance;
    private double brightness;
    private int fps;
    private double flashesPerBeat;
    private BeatSource beat;   // null unless music mode installed a clock
    private Random random;

    // Track current flash color and which cycle we're in
    private Color currentColor;
    private long lastCycleIndex;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.flashRate = params.has("flash_rate") ? params.get("flash_rate").getAsDouble() : 4.0;
        this.onRatio = params.has("on_ratio") ? params.get("on_ratio").getAsDouble() : 0.3;
        this.whiteChance = params.has("white_chance") ? params.get("white_chance").getAsDouble() : 0.15;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.flashesPerBeat = params.has("flashes_per_beat")
            ? Math.max(0.25, params.get("flashes_per_beat").getAsDouble()) : 1.0;
        this.random = new Random();
        this.lastCycleIndex = Long.MIN_VALUE;
        this.currentColor = pickNewColor();

        System.out.println("StrobeEffect initialized:");
        System.out.println("  Flash rate: " + flashRate + " Hz");
        System.out.println("  On ratio: " + onRatio);
        System.out.println("  White chance: " + whiteChance);
        System.out.println("  Brightness: " + brightness);
        System.out.println("  LEDs: " + coords.getCount());
    }

    @Override
    public void setBeatSource(BeatSource source) {
        if (source != null) {
            this.beat = source;
            // A different grid means the cycle counter restarts elsewhere.
            this.lastCycleIndex = Long.MIN_VALUE;
        }
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        // Determine which cycle we're in and position within cycle. On a live
        // clock the cycle is a beat (or a subdivision of one) rather than a
        // fixed number of seconds.
        double cycleExact = beat != null
            ? beat.getBeatPosition() * flashesPerBeat
            : timeSeconds * flashRate;
        long cycleIndex = (long) Math.floor(cycleExact);
        double cyclePos = cycleExact - cycleIndex;

        // New cycle: pick a new color
        if (cycleIndex != lastCycleIndex) {
            lastCycleIndex = cycleIndex;
            currentColor = pickNewColor();
        }

        // If we're in the dark phase, return empty map (all LEDs off)
        if (cyclePos >= onRatio) {
            return pixels;
        }

        // We're in the ON phase — apply a slight brightness ramp (peak at start, fade toward end)
        double rampFactor = 1.0 - (cyclePos / onRatio) * 0.3; // fades from 1.0 to 0.7 across the on phase
        double effectiveBrightness = brightness * rampFactor;

        int r = Math.min(255, Math.max(0, (int) (currentColor.getRed() * effectiveBrightness)));
        int g = Math.min(255, Math.max(0, (int) (currentColor.getGreen() * effectiveBrightness)));
        int b = Math.min(255, Math.max(0, (int) (currentColor.getBlue() * effectiveBrightness)));
        Color frameColor = new Color(r, g, b);

        // Light ALL LEDs with the current color
        for (int i = 0; i < coords.getCount(); i++) {
            pixels.put(coords.get(i).getIndex(), frameColor);
        }

        return pixels;
    }

    private Color pickNewColor() {
        if (random.nextDouble() < whiteChance) {
            return Color.WHITE;
        }
        // Random fully saturated color from the full HSB spectrum
        float hue = random.nextFloat();
        return Color.getHSBColor(hue, 1.0f, 1.0f);
    }

    @Override
    public void dispose() {
        // Nothing to clean up
    }

    @Override
    public String getName() {
        return "Strobe";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
