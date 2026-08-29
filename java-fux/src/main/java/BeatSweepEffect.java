import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Beat Sweep — a soft horizontal band walks down the fox, one step per beat.
 *
 * The band lands on a new height on every beat and covers the fox exactly once
 * per bar, then reverses so the next bar sweeps back up. Earlier positions stay
 * lit as a decaying tail, which keeps the movement readable even when the phase
 * is off by a fraction of a beat — the point of music mode's design.
 *
 * The sweep is driven straight off LED y-coordinates rather than box topology:
 * the fox's boxes are large overlapping polygons whose centroids say nothing
 * about where their LEDs sit, so they cannot express a spatial gradient. A
 * horizontal band is also symmetric by construction.
 */
public class BeatSweepEffect implements Effect, BeatAware {

    private static final Color[] PALETTE = {
        new Color( 80, 200, 255),  // cyan
        new Color(255, 120,  60),  // ember
        new Color(160, 255, 120),  // lime
        new Color(255,  90, 200),  // magenta
        new Color(255, 220,  90),  // gold
    };

    private PixelCoordinates coords;
    private FreeRunningBeatSource fallback;
    private BeatSource beat;

    private int fps;
    private int tailLength;
    private double tailFalloff;
    private double decay;
    private double bandWidthFactor;
    private double baseBrightness;
    private int beatsPerBar;
    private int bandCount;

    private double minY;
    private double maxY;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 45;
        this.tailLength = params.has("tail_length") ? params.get("tail_length").getAsInt() : 2;
        this.tailFalloff = params.has("tail_falloff") ? params.get("tail_falloff").getAsDouble() : 0.5;
        this.decay = params.has("decay") ? params.get("decay").getAsDouble() : 2.0;
        this.bandWidthFactor = params.has("band_width") ? params.get("band_width").getAsDouble() : 0.7;
        this.baseBrightness = params.has("base_brightness") ? params.get("base_brightness").getAsDouble() : 0.04;
        this.beatsPerBar = Math.max(1, params.has("beats_per_bar") ? params.get("beats_per_bar").getAsInt() : 4);
        // One band per beat by default, so a full sweep of the fox lands on the bar.
        this.bandCount = Math.max(1, params.has("bands") ? params.get("bands").getAsInt() : beatsPerBar);

        double fallbackBpm = params.has("fallback_bpm") ? params.get("fallback_bpm").getAsDouble() : 120.0;
        this.fallback = new FreeRunningBeatSource(fallbackBpm, beatsPerBar);
        this.beat = fallback;

        minY = Double.MAX_VALUE;
        maxY = -Double.MAX_VALUE;
        for (int i = 0; i < coords.getCount(); i++) {
            double y = coords.get(i).getY();
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        System.out.println("BeatSweepEffect initialized:");
        System.out.println("  Bands: " + bandCount + " over y " + minY + ".." + maxY + ", tail: " + tailLength);
    }

    @Override
    public void setBeatSource(BeatSource source) {
        if (source != null) {
            this.beat = source;
        }
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        // Without a live clock the tempo runs off the frame time, not the wall
        // clock, so offline preview renders still animate.
        if (beat == fallback) {
            fallback.setRenderTime(timeSeconds);
        }

        double beatPosition = beat.getBeatPosition();
        long beatIndex = (long) Math.floor(beatPosition);
        double phase = beatPosition - beatIndex;
        Color color = PALETTE[(int) Math.floorMod(beatIndex / beatsPerBar, (long) PALETTE.length)];

        double span = Math.max(1.0, maxY - minY);
        double width = Math.max(1.0, span / bandCount * bandWidthFactor);

        // Each tail step is one beat older: dimmer, and one band further back.
        double[] centers = new double[tailLength + 1];
        double[] weights = new double[tailLength + 1];
        for (int step = 0; step <= tailLength; step++) {
            long hitBeat = beatIndex - step;
            centers[step] = minY + span * bandPosition(hitBeat);
            weights[step] = Math.exp(-decay * (phase + step) / (tailLength + 1.0))
                * Math.pow(tailFalloff, step);
        }

        for (int led = 0; led < coords.getCount(); led++) {
            double y = coords.get(led).getY();
            double brightness = baseBrightness;
            for (int step = 0; step <= tailLength; step++) {
                double distance = (y - centers[step]) / width;
                brightness += weights[step] * Math.exp(-distance * distance);
            }
            pixels.put(led, scale(color, brightness));
        }

        return pixels;
    }

    /**
     * Where the band sits for a given beat, as a 0..1 fraction of the fox's
     * height. Every other bar runs backwards so the sweep bounces rather than
     * snapping back to the top.
     */
    private double bandPosition(long beatIndex) {
        int band = (int) Math.floorMod(beatIndex, (long) beatsPerBar) % bandCount;
        boolean reverse = Math.floorMod(Math.floorDiv(beatIndex, (long) beatsPerBar), 2L) == 1;
        if (reverse) {
            band = bandCount - 1 - band;
        }
        return (band + 0.5) / bandCount;
    }

    private Color scale(Color color, double factor) {
        double f = Math.max(0.0, Math.min(1.0, factor));
        return new Color(
            (int) (color.getRed() * f),
            (int) (color.getGreen() * f),
            (int) (color.getBlue() * f)
        );
    }

    @Override
    public void dispose() {
        // Nothing to clean up
    }

    @Override
    public String getName() {
        return "Beat Sweep";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
