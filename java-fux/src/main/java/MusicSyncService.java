import com.google.gson.JsonObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Ties music mode together: polls Spotify for what is playing, resolves the
 * track's tempo, and keeps a {@link BeatClock} anchored so effects have a beat
 * grid to render against.
 *
 * BPM only gives the beat *period*, never the phase — nothing free tells us
 * where the true downbeat sits. Beat 1 is therefore assumed at progress 0, and
 * a per-track millisecond offset (nudged by ear with {@code MUSIC_OFFSET:±ms})
 * is remembered in {@code ~/.fux/beat_offsets.json} and reapplied every time
 * that track comes round again.
 */
public class MusicSyncService implements SpotifyPollingService.Listener {

    /** Beat nudge applied by a single {@code MUSIC_OFFSET:+} / {@code -} command. */
    public static final long DEFAULT_NUDGE_MS = 25;

    public interface Listener {
        /** A different track started playing (or playback stopped). */
        void onTrackChanged(NowPlaying track);

        /** Anything worth re-broadcasting changed: tempo, transport, or an error. */
        void onMusicStateChanged();
    }

    /** Serializable snapshot embedded in {@link StateMessage}. */
    public static class MusicState {
        public boolean configured;
        public boolean authorized;
        public boolean polling;
        public boolean playing;
        public boolean synced;
        public String trackId;
        public String title;
        public String artist;
        public Double bpm;
        public String bpmSource;
        public long offsetMs;
        public double beatPhase;
        public double positionSeconds;
        public String error;
    }

    private final SpotifyAuthService auth;
    private final BpmLookupService bpmLookup;
    private final BeatClock beatClock = new BeatClock();
    private final SpotifyPollingService poller;
    private final Map<String, Long> offsets = new HashMap<>();

    private volatile Listener listener;
    private volatile NowPlaying current = NowPlaying.nothing();
    private volatile String bpmSource;
    private volatile String lastError;
    private volatile boolean running;
    private volatile long lastPlaybackConfirmation;
    public boolean hasFreshPlayback() {
        return running && System.nanoTime() - lastPlaybackConfirmation < 15_000_000_000L;
    }
    @Override public void onPlaybackConfirmed() { lastPlaybackConfirmation = System.nanoTime(); lastError = null; }

    public MusicSyncService() {
        this.auth = new SpotifyAuthService();
        this.bpmLookup = new BpmLookupService();
        this.poller = new SpotifyPollingService(auth, this);
        loadOffsets();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public SpotifyAuthService getAuth() {
        return auth;
    }

    public BeatClock getBeatClock() {
        return beatClock;
    }

    public NowPlaying getNowPlaying() {
        return current;
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        if (!auth.isConfigured()) {
            lastError = "Spotify client id/secret missing — create ~/.fux/spotify.json";
            System.err.println(lastError);
        } else if (!auth.isAuthorized()) {
            lastError = "Spotify not connected — open http://<fux>:8080/api/spotify/login once";
            System.err.println(lastError);
        } else {
            lastError = null;
        }
        running = true;
        poller.start();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        poller.stop();
        beatClock.clear();
        current = NowPlaying.nothing();
        bpmSource = null;
    }

    public void shutdown() {
        stop();
        bpmLookup.shutdown();
    }

    // MARK: - Polling callbacks

    @Override
    public void onNowPlaying(NowPlaying nowPlaying) {
        onPlaybackConfirmed();
        NowPlaying previous = current;
        boolean trackChanged = !nowPlaying.isSameTrack(previous);
        current = nowPlaying;

        if (!nowPlaying.hasTrack()) {
            beatClock.clear();
            bpmSource = null;
            if (trackChanged) {
                notifyTrackChanged(nowPlaying);
            }
            notifyStateChanged();
            return;
        }

        beatClock.anchor(nowPlaying.trackId, nowPlaying.progressMs, nowPlaying.playing,
            nowPlaying.observedAtNanos);

        if (trackChanged) {
            lastError = null;
            bpmSource = null;
            beatClock.setOffsetMs(storedOffset(nowPlaying.trackId));
            System.out.println("Now playing: " + nowPlaying);
            resolveBpm(nowPlaying);
            notifyTrackChanged(nowPlaying);
        } else if (previous.playing != nowPlaying.playing) {
            notifyStateChanged();
        }
    }

    @Override
    public void onPollError(String message) {
        // A persistent problem (no credentials, no network) repeats every poll;
        // only report it when it actually changes so logs and clients stay quiet.
        if (message.equals(lastError)) {
            return;
        }
        lastError = message;
        System.err.println("Music mode: " + message);
        notifyStateChanged();
    }

    private void resolveBpm(final NowPlaying track) {
        bpmLookup.resolve(track, (trackId, bpm, source) -> {
            beatClock.setBpm(trackId, bpm);
            bpmSource = source;
            if (bpm <= 0) {
                System.out.println("No BPM for " + track + " — falling back to an unsynced effect");
            }
            notifyStateChanged();
        });
    }

    // MARK: - Manual calibration

    /** Nudges the beat grid for the current track and remembers the correction. */
    public long nudgeOffset(long deltaMs) {
        return setOffset(beatClock.getOffsetMs() + deltaMs);
    }

    /** Sets an absolute offset in milliseconds for the current track. */
    public long setOffset(long offsetMs) {
        beatClock.setOffsetMs(offsetMs);
        String trackId = beatClock.getTrackId();
        if (trackId != null) {
            synchronized (offsets) {
                if (offsetMs == 0) {
                    offsets.remove(trackId);
                } else {
                    offsets.put(trackId, offsetMs);
                }
            }
            saveOffsets();
        }
        notifyStateChanged();
        return offsetMs;
    }

    public long resetOffset() {
        return setOffset(0);
    }

    /** Manually pins the tempo of the current track, overriding the lookup. */
    public void setBpmOverride(double bpm) {
        NowPlaying track = current;
        if (!track.hasTrack()) {
            return;
        }
        bpmLookup.override(track.trackId, track.artist, track.title, bpm);
        beatClock.setBpm(track.trackId, bpm);
        bpmSource = "manual";
        notifyStateChanged();
    }

    // MARK: - State snapshot

    public MusicState snapshot() {
        MusicState state = new MusicState();
        NowPlaying track = current;

        state.configured = auth.isConfigured();
        state.authorized = auth.isAuthorized();
        state.polling = running;
        state.playing = track.hasTrack() && track.playing && hasFreshPlayback();
        state.synced = beatClock.isSynced() && hasFreshPlayback();
        state.trackId = track.trackId;
        state.title = track.title;
        state.artist = track.artist;

        double knownBpm = beatClock.getKnownBpm();
        state.bpm = knownBpm > 0 ? knownBpm : null;
        state.bpmSource = bpmSource;
        state.offsetMs = beatClock.getOffsetMs();
        state.beatPhase = beatClock.getBeatPhase();
        state.positionSeconds = beatClock.getPositionSeconds();
        state.error = lastError;
        return state;
    }

    private void notifyTrackChanged(NowPlaying track) {
        Listener target = listener;
        if (target != null) {
            target.onTrackChanged(track);
        }
    }

    private void notifyStateChanged() {
        Listener target = listener;
        if (target != null) {
            target.onMusicStateChanged();
        }
    }

    // MARK: - Offset persistence

    private long storedOffset(String trackId) {
        synchronized (offsets) {
            Long stored = offsets.get(trackId);
            return stored == null ? 0 : stored;
        }
    }

    private File offsetsFile() {
        return FuxConfig.file("beat_offsets.json");
    }

    private void loadOffsets() {
        JsonObject stored = FuxConfig.readJson(offsetsFile());
        if (stored == null) {
            return;
        }
        for (Map.Entry<String, com.google.gson.JsonElement> entry : stored.entrySet()) {
            try {
                offsets.put(entry.getKey(), entry.getValue().getAsLong());
            } catch (Exception ignored) {
                // Skip malformed entries
            }
        }
        System.out.println("Loaded " + offsets.size() + " beat offsets");
    }

    private void saveOffsets() {
        Map<String, Long> snapshot;
        synchronized (offsets) {
            snapshot = new HashMap<>(offsets);
        }
        FuxConfig.writeJson(offsetsFile(), snapshot);
    }
}
