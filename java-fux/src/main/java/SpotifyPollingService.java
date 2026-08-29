import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Background poller for {@code GET /v1/me/player/currently-playing}.
 *
 * Runs on its own daemon thread while MUSIC mode is active. Sends the previous
 * response's ETag as {@code If-None-Match} so quiet periods cost a cheap 304,
 * and reports every result to a listener. The polling interval is deliberately
 * coarse (~2s): {@link BeatClock} interpolates between updates, so the API is
 * only ever used to correct drift and notice track changes.
 */
public class SpotifyPollingService implements Runnable {

    private static final String CURRENTLY_PLAYING_URL =
        "https://api.spotify.com/v1/me/player/currently-playing";
    private static final int TIMEOUT_MS = 8000;
    private static final long DEFAULT_INTERVAL_MS = 2000;
    private static final long MAX_BACKOFF_MS = 30000;

    public interface Listener {
        /** Called after every successful poll, including "nothing playing". */
        void onNowPlaying(NowPlaying nowPlaying);

        /** Called when polling cannot continue; message is user-facing. */
        void onPollError(String message);
    }

    private final SpotifyAuthService auth;
    private final Listener listener;
    private final Gson gson = new Gson();
    private final long intervalMs;

    private volatile boolean running;
    private Thread thread;
    private String etag;
    private long backoffMs;
    /**
     * The instant, halfway through the last request, that the returned progress
     * is assumed to describe. Removes roughly half the round-trip from the anchor.
     */
    private long lastRoundTripMidpointNanos = System.nanoTime();

    public SpotifyPollingService(SpotifyAuthService auth, Listener listener) {
        this(auth, listener, DEFAULT_INTERVAL_MS);
    }

    public SpotifyPollingService(SpotifyAuthService auth, Listener listener, long intervalMs) {
        this.auth = auth;
        this.listener = listener;
        this.intervalMs = Math.max(500, intervalMs);
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        etag = null;
        backoffMs = 0;
        thread = new Thread(this, "spotify-poll");
        thread.setDaemon(true);
        thread.start();
        System.out.println("Spotify polling started (every " + intervalMs + "ms)");
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        System.out.println("Spotify polling stopped");
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        while (running) {
            long sleepMs = intervalMs;
            try {
                sleepMs = pollOnce();
            } catch (Exception e) {
                backoffMs = nextBackoff();
                sleepMs = backoffMs;
                listener.onPollError("Spotify poll failed: " + e.getMessage());
            }
            try {
                Thread.sleep(Math.max(200, sleepMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Performs one poll and returns how long to wait before the next one. */
    private long pollOnce() throws Exception {
        String token = auth.getAccessToken();
        if (token == null) {
            listener.onPollError(auth.isAuthorized()
                ? "Could not refresh the Spotify access token"
                : "Spotify is not connected — open /api/spotify/login once");
            return Math.max(intervalMs, 5000);
        }

        SimpleHttp.Response response = requestCurrentlyPlaying(token);

        if (response.status == 401) {
            // Token rejected mid-flight; refresh once and retry immediately.
            String refreshed = auth.refreshAccessToken();
            if (refreshed == null) {
                listener.onPollError("Spotify rejected the access token — reconnect at /api/spotify/login");
                return Math.max(intervalMs, 5000);
            }
            response = requestCurrentlyPlaying(refreshed);
        }

        if (response.status == 429) {
            long wait = Math.max(response.retryAfterSeconds, 1) * 1000L;
            System.out.println("Spotify rate limited, backing off " + wait + "ms");
            return wait;
        }

        if (response.status == 204) {
            // Nothing is playing (or a private session).
            etag = null;
            backoffMs = 0;
            listener.onNowPlaying(NowPlaying.nothing());
            return intervalMs;
        }

        if (response.status == 304) {
            // Unchanged since the last poll; the beat clock keeps interpolating.
            backoffMs = 0;
            return intervalMs;
        }

        if (!response.isOk()) {
            backoffMs = nextBackoff();
            listener.onPollError("Spotify returned HTTP " + response.status);
            return backoffMs;
        }

        backoffMs = 0;
        if (response.etag != null) {
            etag = response.etag;
        }

        NowPlaying nowPlaying = parse(response.body);
        if (nowPlaying != null) {
            listener.onNowPlaying(nowPlaying);
        }
        return intervalMs;
    }

    private SimpleHttp.Response requestCurrentlyPlaying(String token) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        if (etag != null) {
            headers.put("If-None-Match", etag);
        }

        long startNanos = System.nanoTime();
        SimpleHttp.Response response = SimpleHttp.get(CURRENTLY_PLAYING_URL, headers, TIMEOUT_MS);
        lastRoundTripMidpointNanos = startNanos + (System.nanoTime() - startNanos) / 2;
        return response;
    }

    private NowPlaying parse(String body) {
        JsonObject json = gson.fromJson(body, JsonObject.class);
        if (json == null) {
            return null;
        }

        boolean playing = json.has("is_playing") && json.get("is_playing").getAsBoolean();
        long progressMs = json.has("progress_ms") && !json.get("progress_ms").isJsonNull()
            ? json.get("progress_ms").getAsLong() : 0;

        JsonObject item = json.has("item") && json.get("item").isJsonObject()
            ? json.getAsJsonObject("item") : null;
        if (item == null) {
            // Podcasts and local files can come back without a track item.
            return NowPlaying.nothing();
        }

        String trackId = item.has("id") && !item.get("id").isJsonNull()
            ? item.get("id").getAsString() : null;
        String title = item.has("name") ? item.get("name").getAsString() : "";
        long durationMs = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0;

        StringBuilder artists = new StringBuilder();
        if (item.has("artists") && item.get("artists").isJsonArray()) {
            JsonArray array = item.getAsJsonArray("artists");
            for (int i = 0; i < array.size(); i++) {
                JsonObject artist = array.get(i).getAsJsonObject();
                if (artists.length() > 0) {
                    artists.append(", ");
                }
                artists.append(artist.get("name").getAsString());
            }
        }

        if (trackId == null) {
            // Local files have no Spotify id, so BPM lookup and caching cannot
            // key on one; treat them as "nothing" for sync purposes.
            return NowPlaying.nothing();
        }

        return new NowPlaying(trackId, title, artists.toString(), durationMs, progressMs,
            playing, lastRoundTripMidpointNanos);
    }

    private long nextBackoff() {
        long next = backoffMs == 0 ? intervalMs * 2 : backoffMs * 2;
        return Math.min(next, MAX_BACKOFF_MS);
    }
}
