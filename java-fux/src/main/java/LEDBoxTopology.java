import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads the closed boxes (cycles) computed from led-connections.json,
 * computes per-box centroids, and identifies horizontally mirrored
 * box pairs around the fox's vertical centerline.
 *
 * Each box is a closed polygon: the perimeter is a list of LED indices
 * in cycle order. Effects can iterate the perimeter, fill the box, or
 * pair boxes with their mirror twin for symmetrical animation.
 */
public class LEDBoxTopology {

    private final List<Box> boxes = new ArrayList<>();
    private final List<Pair> pairs = new ArrayList<>();
    private double midlineX = 230.0;
    private boolean loaded = false;

    public static class Box {
        public final int id;
        public final List<Integer> perimeter; // LED indices in cycle order
        public final Set<Integer> ledSet;     // perimeter as a set for O(1) lookup
        public final double centroidX;
        public final double centroidY;

        Box(int id, List<Integer> perimeter, double cx, double cy) {
            this.id = id;
            this.perimeter = Collections.unmodifiableList(new ArrayList<>(perimeter));
            this.ledSet = Collections.unmodifiableSet(new HashSet<>(perimeter));
            this.centroidX = cx;
            this.centroidY = cy;
        }
    }

    /**
     * A symmetric grouping of boxes: either a mirror pair (left + right) or
     * a self-symmetric box centered on the midline (only `left` populated,
     * `right == null`). Effects should drive both members in lockstep.
     */
    public static class Pair {
        public final Box left;       // left of midline (smaller x)
        public final Box right;      // right of midline, or null if self-symmetric
        public final boolean selfSymmetric;
        public final double distanceFromMidline; // |centroidX - midlineX| of left

        Pair(Box left, Box right, double distanceFromMidline) {
            this.left = left;
            this.right = right;
            this.selfSymmetric = (right == null);
            this.distanceFromMidline = distanceFromMidline;
        }
    }

    public boolean load() {
        Gson gson = new Gson();
        String[] paths = {
            "/home/simon/.openclaw/workspace/fux/led-namer/led-boxes.json",
            "/home/pi/fux/led-namer/led-boxes.json",
            "led-namer/led-boxes.json",
            "../led-namer/led-boxes.json",
            "../../led-namer/led-boxes.json",
            "led-boxes.json"
        };

        for (String path : paths) {
            try {
                FileReader r = new FileReader(path);
                JsonObject root = gson.fromJson(r, JsonObject.class);
                r.close();
                if (root != null && parse(root)) {
                    System.out.println("LEDBoxTopology loaded from: " + path);
                    return true;
                }
            } catch (Exception e) {
                // try next
            }
        }

        // Classpath fallback
        String[] cps = {
            "led-boxes.json",
            "led-namer/led-boxes.json",
            "assets/led-boxes.json"
        };
        for (String cp : cps) {
            try {
                InputStream is = getClass().getClassLoader().getResourceAsStream(cp);
                if (is != null) {
                    JsonObject root = gson.fromJson(new InputStreamReader(is), JsonObject.class);
                    is.close();
                    if (root != null && parse(root)) {
                        System.out.println("LEDBoxTopology loaded from classpath: " + cp);
                        return true;
                    }
                }
            } catch (Exception e) {
                // try next
            }
        }

        System.err.println("LEDBoxTopology: led-boxes.json not found - box effects will be empty");
        return false;
    }

    private boolean parse(JsonObject root) {
        if (!root.has("boxes")) return false;
        if (root.has("midline_x")) {
            midlineX = root.get("midline_x").getAsDouble();
        }
        JsonArray arr = root.getAsJsonArray("boxes");
        boxes.clear();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject bo = arr.get(i).getAsJsonObject();
            int id = bo.get("id").getAsInt();
            JsonArray perim = bo.getAsJsonArray("perimeter");
            List<Integer> leds = new ArrayList<>(perim.size());
            for (int j = 0; j < perim.size(); j++) {
                leds.add(perim.get(j).getAsInt());
            }
            double cx = bo.get("centroid_x").getAsDouble();
            double cy = bo.get("centroid_y").getAsDouble();
            boxes.add(new Box(id, leds, cx, cy));
        }
        computePairs();
        loaded = true;
        System.out.println("  Loaded " + boxes.size() + " boxes, " + pairs.size() + " symmetric groups");
        return true;
    }

    /**
     * Mirror-pair matching in three passes:
     *   1) boxes with centroid within `selfTol` of the midline -> self-symmetric
     *   2) score every (left, right) candidate by mirror-distance + LED-count similarity,
     *      then assign greedily from best to worst (smallest cost wins)
     *   3) anything still unmatched becomes a solo group
     */
    private void computePairs() {
        pairs.clear();
        if (boxes.isEmpty()) return;

        final double selfTol = 18.0;       // ±18 px of midline -> self-symmetric
        final double matchTolX = 60.0;     // mirrored centroid x window
        final double matchTolY = 60.0;     // mirrored centroid y window

        boolean[] used = new boolean[boxes.size()];

        // 1) self-symmetric (centroid on the midline)
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            if (Math.abs(b.centroidX - midlineX) <= selfTol) {
                pairs.add(new Pair(b, null, 0.0));
                used[i] = true;
            }
        }

        // 2) generate all left-right candidate pairs and score them
        List<Integer> lefts = new ArrayList<>();
        List<Integer> rights = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            if (used[i]) continue;
            if (boxes.get(i).centroidX < midlineX) lefts.add(i);
            else rights.add(i);
        }

        List<double[]> candidates = new ArrayList<>(); // [cost, leftIdx, rightIdx]
        for (int li : lefts) {
            Box lb = boxes.get(li);
            double mirrorX = 2 * midlineX - lb.centroidX;
            for (int ri : rights) {
                Box rb = boxes.get(ri);
                double dx = Math.abs(rb.centroidX - mirrorX);
                double dy = Math.abs(rb.centroidY - lb.centroidY);
                if (dx > matchTolX || dy > matchTolY) continue;
                // Cost combines spatial mismatch with LED-count mismatch so a 3-LED
                // triangle won't steal a 17-LED box's true mirror.
                int sizeDiff = Math.abs(lb.perimeter.size() - rb.perimeter.size());
                double cost = dx * dx + dy * dy + sizeDiff * sizeDiff * 4.0;
                candidates.add(new double[] { cost, li, ri });
            }
        }
        candidates.sort((a, b) -> Double.compare(a[0], b[0]));

        for (double[] c : candidates) {
            int li = (int) c[1];
            int ri = (int) c[2];
            if (used[li] || used[ri]) continue;
            Box lb = boxes.get(li);
            pairs.add(new Pair(lb, boxes.get(ri), midlineX - lb.centroidX));
            used[li] = true;
            used[ri] = true;
        }

        // 3) anything still unmatched becomes a solo group
        for (int i = 0; i < boxes.size(); i++) {
            if (!used[i]) {
                Box b = boxes.get(i);
                pairs.add(new Pair(b, null, Math.abs(b.centroidX - midlineX)));
                used[i] = true;
            }
        }

        // Order groups outside-in (farthest from midline first) for predictable iteration
        pairs.sort((a, b) -> Double.compare(b.distanceFromMidline, a.distanceFromMidline));
    }

    public boolean isLoaded() { return loaded; }
    public int boxCount() { return boxes.size(); }
    public int pairCount() { return pairs.size(); }
    public List<Box> getBoxes() { return Collections.unmodifiableList(boxes); }
    public List<Pair> getPairs() { return Collections.unmodifiableList(pairs); }
    public double getMidlineX() { return midlineX; }
}
