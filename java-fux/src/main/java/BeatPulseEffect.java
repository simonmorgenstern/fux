import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Beat Pulse — the whole fox flashes on every beat and decays before the next.
 *
 * The envelope is a quick attack followed by an exponential decay across the
 * beat, so it reads as a pulse rather than a strobe and tolerates the few
 * hundred milliseconds of phase error music mode is designed around. Downbeats
 * hit harder and light the diamond in an accent colour; the palette advances
 * one step per bar so long tracks keep moving.
 */
public class BeatPulseEffect implements Effect, BeatAware {

    private static final Color[] PALETTE = {
        new Color(255,  60, 120),  // hot pink
        new Color( 60, 160, 255),  // electric blue
        new Color(255, 170,  40),  // amber
        new Color(120, 255, 160),  // mint
        new Color(180,  90, 255),  // violet
        new Color(255, 240, 120),  // pale gold
    };

    private PixelCoordinates coords;
    private FreeRunningBeatSource fallback;
    private BeatSource beat;
    private Set<Integer> diamond;
    private Set<Integer> eyes;

    private int fps;
    private double attack;          // fraction of the beat spent rising
    private double decay;           // exponential decay constant
    private double minBrightness;
    private double downbeatBoost;
    private int beatsPerBar;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 45;
        this.attack = params.has("attack") ? params.get("attack").getAsDouble() : 0.06;
        this.decay = params.has("decay") ? params.get("decay").getAsDouble() : 4.5;
        this.minBrightness = params.has("min_brightness") ? params.get("min_brightness").getAsDouble() : 0.08;
        this.downbeatBoost = params.has("downbeat_boost") ? params.get("downbeat_boost").getAsDouble() : 1.0;
        this.beatsPerBar = params.has("beats_per_bar") ? params.get("beats_per_bar").getAsInt() : 4;

        double fallbackBpm = params.has("fallback_bpm") ? params.get("fallback_bpm").getAsDouble() : 120.0;
        this.fallback = new FreeRunningBeatSource(fallbackBpm, beatsPerBar);
        this.beat = fallback;

        this.diamond = LEDGroupLoader.loadGroup("diamond");
        this.eyes = LEDGroupLoader.loadGroup("eyes");

        System.out.println("BeatPulseEffect initialized:");
        System.out.println("  Attack: " + attack + ", decay: " + decay);
        System.out.println("  Diamond LEDs: " + diamond.size() + ", eye LEDs: " + eyes.size());
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
        double phase = beatPosition - Math.floor(beatPosition);
        boolean downbeat = Math.floorMod((long) Math.floor(beatPosition), (long) beatsPerBar) == 0;
        double envelope = envelope(phase) * (downbeat ? 1.0 : 1.0 / (1.0 + downbeatBoost * 0.35));
        double brightness = minBrightness + (1.0 - minBrightness) * envelope;

        long bar = Math.floorDiv((long) Math.floor(beatPosition), (long) beatsPerBar);
        Color base = PALETTE[(int) Math.floorMod(bar, PALETTE.length)];
        Color accent = PALETTE[(int) Math.floorMod(bar + 2, PALETTE.length)];

        Color bodyColor = scale(base, brightness);
        // The accent trails the body slightly so the centre reads as the hit.
        Color accentColor = scale(accent, Math.min(1.0, brightness * 1.15));

        for (int i = 0; i < coords.getCount(); i++) {
            if (downbeat && diamond.contains(i)) {
                pixels.put(i, accentColor);
            } else if (eyes.contains(i)) {
                // Eyes stay a touch brighter so the face never disappears.
                pixels.put(i, scale(base, Math.min(1.0, brightness + 0.15)));
            } else {
                pixels.put(i, bodyColor);
            }
        }

        return pixels;
    }

    /** Fast rise over {@code attack}, exponential fall across the rest of the beat. */
    private double envelope(double phase) {
        if (phase < attack) {
            return phase / attack;
        }
        double t = (phase - attack) / (1.0 - attack);
        return Math.exp(-decay * t);
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
        return "Beat Pulse";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
