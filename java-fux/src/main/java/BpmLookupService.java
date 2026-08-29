import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Finds a track's tempo without Spotify's audio-features endpoint, which is no
 * longer available to apps registered after November 2024.
 *
 * Order of resolution:
 *   1. The permanent local cache in {@code ~/.fux/bpm_cache.json}, keyed by
 *      Spotify track id. "Unknown" is cached too, so a track with no data
 *      anywhere is looked up once and never again.
 *   2. Deezer's public API (no auth): search by artist and title, pick the
 *      closest match by duration, then read {@code bpm} off the track record.
 *   3. GetSongBPM, if an API key is configured (getSongBpmApiKey in
 *      ~/.fux/spotify.json, or GETSONGBPM_API_KEY).
 *
 * Lookups run on a single background thread so a slow API can never stall the
 * render loop; the result arrives via {@link Callback}.
 */
public class BpmLookupService {

    private static final int TIMEOUT_MS = 8000;
    /** Beyond this difference the Deezer hit is assumed to be a different cut. */
    private static final long MAX_DURATION_DELTA_MS = 12000;

    public interface Callback {
        void onBpmResolved(String trackId, double bpm, String source);
    }

    /** One cached tempo. {@code bpm == 0} means "looked up, nothing found". */
    private static class CacheEntry {
        double bpm;
        String source;
        String artist;
        String title;
        long resolvedAt;
    }

    private final Gson gson = new Gson();
    private final Map<String, CacheEntry> cache = new HashMap<>();
    private final ExecutorService executor;
    private final String getSongBpmApiKey;

    public BpmLookupService() {
        JsonObject config = FuxConfig.readJson(FuxConfig.file("spotify.json"));
        this.getSongBpmApiKey = FuxConfig.setting(config, "getSongBpmApiKey", "GETSONGBPM_API_KEY", null);
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bpm-lookup");
            thread.setDaemon(true);
            return thread;
        });
        loadCache();
    }

    /**
     * Resolves the tempo for a track, from cache if possible.
     * The callback fires immediately on a cache hit, otherwise off-thread.
     *
     * @return the cached BPM (0 if unknown), or -1 if a lookup was started
     */
    public double resolve(final NowPlaying track, final Callback callback) {
        if (track == null || !track.hasTrack()) {
            return 0;
        }

        CacheEntry cached;
        synchronized (cache) {
            cached = cache.get(track.trackId);
        }
        if (cached != null) {
            callback.onBpmResolved(track.trackId, cached.bpm, cached.source);
            return cached.bpm;
        }

        executor.submit(() -> {
            CacheEntry entry = lookup(track);
            synchronized (cache) {
                cache.put(track.trackId, entry);
            }
            saveCache();
            System.out.println("BPM for " + track + ": "
                + (entry.bpm > 0 ? entry.bpm + " (" + entry.source + ")" : "unknown"));
            callback.onBpmResolved(track.trackId, entry.bpm, entry.source);
        });
        return -1;
    }

    /** Overrides (or seeds) the tempo for a track and remembers it. */
    public void override(String trackId, String artist, String title, double bpm) {
        if (trackId == null) {
            return;
        }
        CacheEntry entry = new CacheEntry();
        entry.bpm = bpm > 0 ? bpm : 0;
        entry.source = "manual";
        entry.artist = artist;
        entry.title = title;
        entry.resolvedAt = System.currentTimeMillis();
        synchronized (cache) {
            cache.put(trackId, entry);
        }
        saveCache();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private CacheEntry lookup(NowPlaying track) {
        CacheEntry entry = new CacheEntry();
        entry.artist = track.artist;
        entry.title = track.title;
        entry.resolvedAt = System.currentTimeMillis();

        try {
            double bpm = lookupDeezer(track);
            if (bpm > 0) {
                entry.bpm = bpm;
                entry.source = "deezer";
                return entry;
            }
        } catch (Exception e) {
            System.err.println("Deezer BPM lookup failed: " + e.getMessage());
        }

        if (getSongBpmApiKey != null) {
            try {
                double bpm = lookupGetSongBpm(track);
                if (bpm > 0) {
                    entry.bpm = bpm;
                    entry.source = "getsongbpm";
                    return entry;
                }
            } catch (Exception e) {
                System.err.println("GetSongBPM lookup failed: " + e.getMessage());
            }
        }

        entry.bpm = 0;
        entry.source = "unknown";
        return entry;
    }

    /**
     * Deezer search returns no tempo, so this is a two-step lookup: find the
     * best matching track id, then read {@code bpm} from the track record.
     *
     * The query is free text ("artist title"): Deezer's field syntax
     * ({@code artist:"..." track:"..."}) returns nothing at all. Free text
     * pulls in tribute and karaoke cuts, so hits are filtered by artist name
     * before the closest one by duration wins.
     */
    private double lookupDeezer(NowPlaying track) throws Exception {
        String query = (track.artist == null ? "" : track.artist + " ")
            + (track.title == null ? "" : track.title);
        if (query.trim().isEmpty()) {
            return 0;
        }
        String url = "https://api.deezer.com/search?limit=10&q=" + SimpleHttp.urlEncode(query.trim());

        SimpleHttp.Response response = SimpleHttp.get(url, null, TIMEOUT_MS);
        if (!response.isOk()) {
            return 0;
        }

        JsonObject json = gson.fromJson(response.body, JsonObject.class);
        if (json == null || !json.has("data")) {
            return 0;
        }

        JsonArray results = json.getAsJsonArray("data");
        long bestId = -1;
        long bestDelta = Long.MAX_VALUE;
        for (int i = 0; i < results.size(); i++) {
            JsonObject hit = results.get(i).getAsJsonObject();
            if (!hit.has("id") || !hit.has("duration")) {
                continue;
            }
            if (!artistMatches(track.artist, hit)) {
                continue;
            }
            long durationMs = hit.get("duration").getAsLong() * 1000L;
            long delta = Math.abs(durationMs - track.durationMs);
            if (track.durationMs > 0 && delta > MAX_DURATION_DELTA_MS) {
                continue;
            }
            if (delta < bestDelta) {
                bestDelta = delta;
                bestId = hit.get("id").getAsLong();
            }
        }

        if (bestId < 0) {
            return 0;
        }

        SimpleHttp.Response detail = SimpleHttp.get("https://api.deezer.com/track/" + bestId, null, TIMEOUT_MS);
        if (!detail.isOk()) {
            return 0;
        }
        JsonObject trackJson = gson.fromJson(detail.body, JsonObject.class);
        if (trackJson == null || !trackJson.has("bpm")) {
            return 0;
        }
        return trackJson.get("bpm").getAsDouble();
    }

    /**
     * True when a Deezer hit is by the artist we are actually looking for.
     * Matching is loose in both directions so "Daft Punk" still matches a
     * Spotify credit of "Daft Punk, Pharrell Williams" — but it is enough to
     * throw out the tribute and karaoke cuts a free-text search drags in.
     */
    private boolean artistMatches(String wanted, JsonObject hit) {
        String normalizedWanted = normalize(wanted);
        if (normalizedWanted.isEmpty()) {
            return true;
        }
        if (!hit.has("artist") || !hit.get("artist").isJsonObject()) {
            return false;
        }
        JsonObject artist = hit.getAsJsonObject("artist");
        if (!artist.has("name")) {
            return false;
        }
        String found = normalize(artist.get("name").getAsString());
        if (found.isEmpty()) {
            return false;
        }
        return normalizedWanted.contains(found) || found.contains(normalizedWanted);
    }

    /** Lowercase, punctuation stripped, whitespace collapsed. */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
    }

    private double lookupGetSongBpm(NowPlaying track) throws Exception {
        String lookup = "song:" + track.title + " artist:" + track.artist;
        String url = "https://api.getsong.co/search/?api_key=" + SimpleHttp.urlEncode(getSongBpmApiKey)
            + "&type=both&lookup=" + SimpleHttp.urlEncode(lookup);

        SimpleHttp.Response response = SimpleHttp.get(url, null, TIMEOUT_MS);
        if (!response.isOk()) {
            return 0;
        }

        JsonObject json = gson.fromJson(response.body, JsonObject.class);
        if (json == null || !json.has("search") || !json.get("search").isJsonArray()) {
            return 0;
        }
        JsonArray results = json.getAsJsonArray("search");
        for (int i = 0; i < results.size(); i++) {
            JsonObject hit = results.get(i).getAsJsonObject();
            if (hit.has("tempo") && !hit.get("tempo").isJsonNull()) {
                try {
                    double bpm = Double.parseDouble(hit.get("tempo").getAsString());
                    if (bpm > 0) {
                        return bpm;
                    }
                } catch (NumberFormatException ignored) {
                    // Try the next hit
                }
            }
        }
        return 0;
    }

    // MARK: - Persistence

    private File cacheFile() {
        return FuxConfig.file("bpm_cache.json");
    }

    private void loadCache() {
        JsonObject stored = FuxConfig.readJson(cacheFile());
        if (stored == null) {
            return;
        }
        for (Map.Entry<String, com.google.gson.JsonElement> entry : stored.entrySet()) {
            try {
                CacheEntry parsed = gson.fromJson(entry.getValue(), CacheEntry.class);
                if (parsed != null) {
                    cache.put(entry.getKey(), parsed);
                }
            } catch (Exception ignored) {
                // Skip malformed entries
            }
        }
        System.out.println("Loaded " + cache.size() + " cached BPM entries");
    }

    private void saveCache() {
        Map<String, CacheEntry> snapshot;
        synchronized (cache) {
            snapshot = new HashMap<>(cache);
        }
        FuxConfig.writeJson(cacheFile(), snapshot);
    }
}
