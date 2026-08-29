import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Beat Sparkle — a burst of sparks on every beat over a slow breathing wash.
 *
 * Each beat drops a handful of sparks at random LEDs, mirrored across the fox's
 * centerline so the bursts stay symmetric, then fades them over the following
 * beat or two. Downbeats throw more sparks in an accent colour. Because the
 * sparks are spawned per beat index rather than per frame, a paused clock
 * simply freezes the picture instead of piling up new bursts.
 */
public class BeatSparkleEffect implements Effect, BeatAware {

    private static final Color[] PALETTE = {
        new Color(255, 255, 220),  // warm white
        new Color(150, 220, 255),  // ice
        new Color(255, 200, 130),  // candle
        new Color(210, 170, 255),  // lilac
    };

    private static class Spark {
        int led;
        double bornAtBeat;
        double lifeBeats;
        Color color;
    }

    private PixelCoordinates coords;
    private LEDMirrorMap mirrors;
    private FreeRunningBeatSource fallback;
    private BeatSource beat;
    private final List<Spark> sparks = new ArrayList<>();
    private Random random;
    private long lastSpawnedBeat = Long.MIN_VALUE;

    private int fps;
    private int sparksPerBeat;
    private double downbeatMultiplier;
    private double sparkLifeBeats;
    private int[] washColor;
    private double washBrightness;
    private int beatsPerBar;

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 45;
        this.sparksPerBeat = params.has("sparks_per_beat") ? params.get("sparks_per_beat").getAsInt() : 10;
        this.downbeatMultiplier = params.has("downbeat_multiplier")
            ? params.get("downbeat_multiplier").getAsDouble() : 2.0;
        this.sparkLifeBeats = params.has("spark_life_beats")
            ? params.get("spark_life_beats").getAsDouble() : 1.4;
        this.washBrightness = params.has("wash_brightness") ? params.get("wash_brightness").getAsDouble() : 0.12;
        this.beatsPerBar = params.has("beats_per_bar") ? params.get("beats_per_bar").getAsInt() : 4;

        this.washColor = new int[] {20, 30, 70};
        if (params.has("wash_color")) {
            com.google.gson.JsonArray rgb = params.getAsJsonArray("wash_color");
            for (int i = 0; i < 3 && i < rgb.size(); i++) {
                washColor[i] = rgb.get(i).getAsInt();
            }
        }

        double fallbackBpm = params.has("fallback_bpm") ? params.get("fallback_bpm").getAsDouble() : 120.0;
        this.fallback = new FreeRunningBeatSource(fallbackBpm, beatsPerBar);
        this.beat = fallback;

        this.mirrors = new LEDMirrorMap();
        this.mirrors.load();

        System.out.println("BeatSparkleEffect initialized:");
        System.out.println("  Sparks/beat: " + sparksPerBeat + ", life: " + sparkLifeBeats + " beats");
        System.out.println("  Mirror map loaded: " + mirrors.isLoaded());
    }

    @Override
    public void setBeatSource(BeatSource source) {
        if (source != null) {
            this.beat = source;
            // A different clock means a different grid; drop stale sparks.
            sparks.clear();
            lastSpawnedBeat = Long.MIN_VALUE;
        }
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        // Without a live clock the tempo runs off the frame time, not the wall
        // clock, so offline preview renders still animate.
        if (beat == fallback) {
            fallback.setRenderTime(timeSeconds);
        }

        double beatPosition = beat.getBeatPosition();
        long beatIndex = (long) Math.floor(beatPosition);

        if (beatIndex != lastSpawnedBeat) {
            // Jumping backwards or far forwards (seek, track change) resets rather
            // than spawning a burst for every beat in between.
            if (lastSpawnedBeat != Long.MIN_VALUE && beatIndex > lastSpawnedBeat
                    && beatIndex - lastSpawnedBeat <= 4) {
                for (long b = lastSpawnedBeat + 1; b <= beatIndex; b++) {
                    spawnBurst(b);
                }
            } else {
                sparks.clear();
                spawnBurst(beatIndex);
            }
            lastSpawnedBeat = beatIndex;
        }

        Map<Integer, Color> pixels = new HashMap<>();

        // Slow wash that swells towards the downbeat and drops away after it.
        double barPhase = beat.getBarPhase();
        double washPulse = 0.6 + 0.4 * Math.cos(barPhase * 2 * Math.PI);
        Color wash = new Color(
            clamp(washColor[0] * washBrightness * washPulse),
            clamp(washColor[1] * washBrightness * washPulse),
            clamp(washColor[2] * washBrightness * washPulse)
        );
        for (int i = 0; i < coords.getCount(); i++) {
            pixels.put(i, wash);
        }

        Iterator<Spark> iterator = sparks.iterator();
        while (iterator.hasNext()) {
            Spark spark = iterator.next();
            double age = (beatPosition - spark.bornAtBeat) / spark.lifeBeats;
            if (age < 0 || age >= 1.0) {
                iterator.remove();
                continue;
            }
            double brightness = (1.0 - age) * (1.0 - age); // quadratic fade-out
            Color existing = pixels.get(spark.led);
            Color lit = new Color(
                clamp(Math.max(existing.getRed(), spark.color.getRed() * brightness)),
                clamp(Math.max(existing.getGreen(), spark.color.getGreen() * brightness)),
                clamp(Math.max(existing.getBlue(), spark.color.getBlue() * brightness))
            );
            pixels.put(spark.led, lit);
        }

        return pixels;
    }

    private void spawnBurst(long beatIndex) {
        int inBar = (int) Math.floorMod(beatIndex, (long) Math.max(1, beatsPerBar));
        boolean downbeat = inBar == 0;
        int count = (int) Math.round(sparksPerBeat * (downbeat ? downbeatMultiplier : 1.0));
        Color color = downbeat
            ? PALETTE[(int) Math.floorMod(beatIndex / Math.max(1, beatsPerBar), PALETTE.length)]
            : PALETTE[0];

        for (int i = 0; i < count; i++) {
            int led = random.nextInt(coords.getCount());
            addSpark(led, beatIndex, color);

            // Mirror it so bursts read as symmetric rather than noisy.
            if (mirrors.isLoaded() && mirrors.isAssigned(led)) {
                int mirrored = mirrors.getMirror(led);
                if (mirrored >= 0 && mirrored != led && mirrored < coords.getCount()) {
                    addSpark(mirrored, beatIndex, color);
                }
            }
        }
    }

    private void addSpark(int led, long beatIndex, Color color) {
        Spark spark = new Spark();
        spark.led = led;
        spark.bornAtBeat = beatIndex;
        spark.lifeBeats = sparkLifeBeats * (0.7 + 0.6 * random.nextDouble());
        spark.color = color;
        sparks.add(spark);
    }

    private int clamp(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    @Override
    public void dispose() {
        sparks.clear();
    }

    @Override
    public String getName() {
        return "Beat Sparkle";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
