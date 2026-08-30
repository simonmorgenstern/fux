import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Center Pulse — concentric waves of color expand outward from a set of
 * "center" LEDs using BFS graph distance.  The waves follow the fox's
 * actual wiring topology, so they curve around corners and branch at
 * junctions — unlike coordinate-based radial effects.
 *
 * Mirror symmetry is guaranteed because the center LEDs sit on or near
 * the midline, and their BFS distance rings are naturally symmetric.
 * Per-LED mirror enforcement via LEDMirrorMap ensures any tiny graph
 * asymmetry is corrected.
 *
 * In MUSIC mode {@link EffectEngine} installs the live {@link BeatClock}: a
 * wave is then launched on every beat and sized to cross the whole fox in
 * {@code wave_span_beats} beats, instead of spawning on a seconds timer.  With
 * no clock the original timing is kept, so queue, idle and preview renders are
 * unchanged.
 */
public class CenterPulseEffect implements Effect, BeatAware {
    private PixelCoordinates coords;
    private LEDMirrorMap mirrorMap;
    private int fps;
    private double waveSpeed;       // graph-hops per second
    private double waveWidth;       // width of wave in hops
    private double spawnIntervalSec;
    private double brightness;
    private double waveSpanBeats;   // beats a wave takes to cross the fox
    private BeatSource beat;        // null unless music mode installed a clock
    private long lastSpawnedBeat = Long.MIN_VALUE;

    private int[] dist;             // BFS distance from center for each LED
    private int maxDist;
    private double timeSinceSpawn;
    private List<Wave> waves;
    private Random random;

    private static final Color[] PALETTE = {
        new Color(255,  80, 120),   // rose
        new Color(255, 160,  60),   // amber
        new Color( 60, 220, 255),   // cyan
        new Color(160,  80, 255),   // violet
        new Color( 80, 255, 160),   // mint
        new Color(255, 220,  60),   // gold
    };

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.waveSpeed = params.has("wave_speed")
            ? params.get("wave_speed").getAsDouble() : 8.0;
        this.waveWidth = params.has("wave_width")
            ? params.get("wave_width").getAsDouble() : 5.0;
        this.spawnIntervalSec = params.has("spawn_interval_sec")
            ? params.get("spawn_interval_sec").getAsDouble() : 1.2;
        this.brightness = params.has("brightness")
            ? params.get("brightness").getAsDouble() : 1.0;
        this.waveSpanBeats = params.has("wave_span_beats")
            ? Math.max(0.25, params.get("wave_span_beats").getAsDouble()) : 2.0;

        // Center LED indices — configurable, default to the fox's visual center
        int[] centerLeds;
        if (params.has("center_leds")) {
            JsonArray arr = params.getAsJsonArray("center_leds");
            centerLeds = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) centerLeds[i] = arr.get(i).getAsInt();
        } else {
            centerLeds = new int[]{145, 146, 183, 184, 213};
        }

        // Load mirror map
        this.mirrorMap = new LEDMirrorMap();
        this.mirrorMap.load();

        // Build neighbor graph and BFS from center
        LEDNeighborGraph graph = new LEDNeighborGraph(coords);
        graph.build(0, 50, 8);
        bfsFromCenter(graph, centerLeds, coords.getCount());

        this.timeSinceSpawn = Double.MAX_VALUE; // spawn immediately
        this.waves = new ArrayList<>();
        this.random = new Random();

        System.out.println("CenterPulseEffect initialized:");
        System.out.println("  Center LEDs: " + centerLeds.length + ", max BFS dist: " + maxDist);
        System.out.println("  Wave speed: " + waveSpeed + " hops/sec, width: " + waveWidth);
    }

    private void bfsFromCenter(LEDNeighborGraph graph, int[] seeds, int n) {
        dist = new int[n];
        java.util.Arrays.fill(dist, -1);
        Queue<Integer> queue = new LinkedList<>();
        for (int s : seeds) {
            if (s >= 0 && s < n) {
                dist[s] = 0;
                queue.add(s);
            }
        }
        maxDist = 0;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v : graph.getNeighbors(u)) {
                if (v >= 0 && v < n && dist[v] < 0) {
                    dist[v] = dist[u] + 1;
                    if (dist[v] > maxDist) maxDist = dist[v];
                    queue.add(v);
                }
            }
        }
    }

    @Override
    public void setBeatSource(BeatSource source) {
        if (source != null) {
            this.beat = source;
            // A different grid means the waves in flight belong to nothing.
            waves.clear();
            lastSpawnedBeat = Long.MIN_VALUE;
        }
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        if (beat != null) {
            updateWavesOnBeat();
        } else {
            updateWavesOnTimer();
        }

        int n = coords.getCount();
        double[] r = new double[n];
        double[] g = new double[n];
        double[] b = new double[n];

        for (int i = 0; i < n; i++) {
            if (dist[i] < 0) continue; // unreachable LED
            double d = dist[i];
            for (Wave w : waves) {
                double delta = Math.abs(w.radius - d);
                if (delta > waveWidth) continue;
                double env = 0.5 + 0.5 * Math.cos(Math.PI * delta / waveWidth);
                env *= brightness;
                r[i] += w.color.getRed()   * env;
                g[i] += w.color.getGreen() * env;
                b[i] += w.color.getBlue()  * env;
            }
        }

        // Mirror enforcement: for each LED, ensure its mirror has the same color.
        // Take the max of the two to handle any graph asymmetry.
        for (int i = 0; i < n; i++) {
            int m = mirrorMap.getMirror(i);
            if (m != i && m >= 0 && m < n) {
                double mr = Math.max(r[i], r[m]);
                double mg = Math.max(g[i], g[m]);
                double mb = Math.max(b[i], b[m]);
                r[i] = mr; r[m] = mr;
                g[i] = mg; g[m] = mg;
                b[i] = mb; b[m] = mb;
            }
        }

        Map<Integer, Color> pixels = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (r[i] > 0 || g[i] > 0 || b[i] > 0) {
                pixels.put(i, new Color(
                    Math.min(255, (int) r[i]),
                    Math.min(255, (int) g[i]),
                    Math.min(255, (int) b[i])
                ));
            }
        }
        return pixels;
    }

    /** Free-running behaviour: a wave every spawn_interval_sec, advanced per frame. */
    private void updateWavesOnTimer() {
        double dt = 1.0 / fps;
        timeSinceSpawn += dt;

        if (timeSinceSpawn >= spawnIntervalSec) {
            waves.add(new Wave(PALETTE[random.nextInt(PALETTE.length)], 0));
            timeSinceSpawn = 0;
        }
        for (Wave w : waves) w.radius += waveSpeed * dt;
        // Remove waves that have fully passed the outermost ring
        Iterator<Wave> it = waves.iterator();
        while (it.hasNext()) {
            if (it.next().radius > maxDist + waveWidth) it.remove();
        }
    }

    /**
     * Music behaviour: one wave per beat, its radius read straight off the beat
     * grid so a pause holds the picture and a seek does not leave ghosts behind.
     */
    private void updateWavesOnBeat() {
        double beatPosition = beat.getBeatPosition();
        long beatIndex = (long) Math.floor(beatPosition);

        if (beatIndex != lastSpawnedBeat) {
            // A small forward gap gets its missed waves; a jump (seek, track
            // change) starts over rather than launching dozens at once.
            if (lastSpawnedBeat != Long.MIN_VALUE && beatIndex > lastSpawnedBeat
                    && beatIndex - lastSpawnedBeat <= 4) {
                for (long b = lastSpawnedBeat + 1; b <= beatIndex; b++) {
                    spawnWaveForBeat(b);
                }
            } else {
                waves.clear();
                spawnWaveForBeat(beatIndex);
            }
            lastSpawnedBeat = beatIndex;
        }

        // Sized so a wave crosses the whole fox in wave_span_beats beats.
        double hopsPerBeat = (maxDist + waveWidth) / waveSpanBeats;
        Iterator<Wave> it = waves.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            w.radius = (beatPosition - w.bornAtBeat) * hopsPerBeat;
            if (w.radius < 0 || w.radius > maxDist + waveWidth) it.remove();
        }
    }

    /** Colours cycle with the beat counter so consecutive waves stay distinct. */
    private void spawnWaveForBeat(long beatIndex) {
        Color color = PALETTE[(int) Math.floorMod(beatIndex, PALETTE.length)];
        waves.add(new Wave(color, beatIndex));
    }

    @Override public void dispose() { if (waves != null) waves.clear(); }
    @Override public String getName() { return "Center Pulse"; }
    @Override public int getFPS() { return fps; }

    private static class Wave {
        final Color color;
        final double bornAtBeat; // beat the wave was launched on (music mode)
        double radius; // current distance in BFS hops from center
        Wave(Color color, double bornAtBeat) {
            this.color = color;
            this.bornAtBeat = bornAtBeat;
            this.radius = 0;
        }
    }
}
