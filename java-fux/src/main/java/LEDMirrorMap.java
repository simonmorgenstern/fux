import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads per-LED mirror pairings from led-mirrors.json.
 *
 * Each LED is either:
 *   - paired with another LED (left ↔ right across the vertical midline), or
 *   - self-mirrored (sits on or near the midline, no partner), or
 *   - unassigned (not in the file at all — treated as having no mirror).
 *
 * Effects can call {@link #getMirror(int)} to find the mirror of any LED,
 * and {@link #getDistanceFromMidline(int, PixelCoordinates)} to compute
 * how far an LED sits from the axis of symmetry.
 */
public class LEDMirrorMap {

    /** LED index → mirror LED index.  Self-mirror: maps to itself. */
    private final Map<Integer, Integer> mirrors = new HashMap<>();
    private double midlineX = 230.0;
    private boolean loaded = false;

    public boolean load() {
        Gson gson = new Gson();
        String[] paths = {
            "/home/simon/.openclaw/workspace/fux/led-namer/led-mirrors.json",
            "/home/pi/fux/led-namer/led-mirrors.json",
            "led-namer/led-mirrors.json",
            "../led-namer/led-mirrors.json",
            "../../led-namer/led-mirrors.json",
            "led-mirrors.json"
        };

        for (String path : paths) {
            try {
                FileReader r = new FileReader(path);
                JsonObject root = gson.fromJson(r, JsonObject.class);
                r.close();
                if (root != null && parse(root)) {
                    System.out.println("LEDMirrorMap loaded from: " + path);
                    return true;
                }
            } catch (Exception e) {
                // try next
            }
        }

        // Classpath fallback
        String[] cps = {
            "led-mirrors.json",
            "led-namer/led-mirrors.json",
            "assets/led-mirrors.json"
        };
        for (String cp : cps) {
            try {
                InputStream is = getClass().getClassLoader().getResourceAsStream(cp);
                if (is != null) {
                    JsonObject root = gson.fromJson(new InputStreamReader(is), JsonObject.class);
                    is.close();
                    if (root != null && parse(root)) {
                        System.out.println("LEDMirrorMap loaded from classpath: " + cp);
                        return true;
                    }
                }
            } catch (Exception e) {
                // try next
            }
        }

        System.err.println("LEDMirrorMap: led-mirrors.json not found — mirror effects will run without symmetry");
        return false;
    }

    private boolean parse(JsonObject root) {
        if (!root.has("pairs")) return false;
        if (root.has("midline_x")) {
            midlineX = root.get("midline_x").getAsDouble();
        }
        JsonArray arr = root.getAsJsonArray("pairs");
        mirrors.clear();
        for (int i = 0; i < arr.size(); i++) {
            JsonArray pair = arr.get(i).getAsJsonArray();
            int a = pair.get(0).getAsInt();
            int b = pair.get(1).getAsInt();
            mirrors.put(a, b);
            mirrors.put(b, a);
        }
        loaded = true;
        System.out.println("  Loaded " + arr.size() + " mirror pairs (" + mirrors.size() + " LED entries)");
        return true;
    }

    // ---- Public API ----

    public boolean isLoaded() { return loaded; }
    public double getMidlineX() { return midlineX; }

    /**
     * Return the mirror partner of the given LED, or the LED itself if no
     * mirror is known (graceful fallback — effect still runs, just without
     * symmetry for that LED).
     */
    public int getMirror(int ledIndex) {
        Integer m = mirrors.get(ledIndex);
        return m != null ? m : ledIndex;
    }

    /** True if this LED is explicitly paired with itself (sits on the midline). */
    public boolean isSelfMirror(int ledIndex) {
        Integer m = mirrors.get(ledIndex);
        return m != null && m == ledIndex;
    }

    /** True if this LED has any mirror assignment (paired or self). */
    public boolean isAssigned(int ledIndex) {
        return mirrors.containsKey(ledIndex);
    }

    /** Return the full mirror map (unmodifiable). */
    public Map<Integer, Integer> getAll() {
        return Collections.unmodifiableMap(mirrors);
    }

    /**
     * Horizontal distance of the LED from the vertical midline.
     * Mirror pairs always return the same value.
     */
    public double getDistanceFromMidline(int ledIndex, PixelCoordinates coords) {
        PixelCoordinate pc = coords.get(ledIndex);
        if (pc == null) return 0;
        return Math.abs(pc.getX() - midlineX);
    }

    /**
     * Normalised horizontal distance [0..1] where 0 = on the midline,
     * 1 = at the outermost edge.  {@code maxHalfWidth} should be the
     * largest |x - midlineX| across all LEDs (compute once and cache).
     */
    public double getNormalizedDistance(int ledIndex, PixelCoordinates coords, double maxHalfWidth) {
        PixelCoordinate pc = coords.get(ledIndex);
        if (pc == null || maxHalfWidth <= 0) return 0;
        return Math.min(1.0, Math.abs(pc.getX() - midlineX) / maxHalfWidth);
    }

    /** Convenience: compute the max half-width once from all LEDs. */
    public double computeMaxHalfWidth(PixelCoordinates coords) {
        double max = 0;
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate pc = coords.get(i);
            if (pc != null) max = Math.max(max, Math.abs(pc.getX() - midlineX));
        }
        return max;
    }
}
