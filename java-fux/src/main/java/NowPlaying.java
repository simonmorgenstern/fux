/**
 * Immutable snapshot of what Spotify reported as currently playing.
 * {@code observedAtNanos} is a {@link System#nanoTime()} estimate of the instant
 * {@code progressMs} was actually true, so {@link BeatClock} can anchor to it
 * without inheriting the request's round-trip latency.
 */
public class NowPlaying {
    public final String trackId;
    public final String title;
    public final String artist;
    public final long durationMs;
    public final long progressMs;
    public final boolean playing;
    public final long observedAtNanos;

    public NowPlaying(String trackId, String title, String artist,
                      long durationMs, long progressMs, boolean playing, long observedAtNanos) {
        this.trackId = trackId;
        this.title = title;
        this.artist = artist;
        this.durationMs = durationMs;
        this.progressMs = progressMs;
        this.playing = playing;
        this.observedAtNanos = observedAtNanos;
    }

    /** Sentinel for "Spotify has nothing playing" (HTTP 204). */
    public static NowPlaying nothing() {
        return new NowPlaying(null, null, null, 0, 0, false, System.nanoTime());
    }

    public boolean hasTrack() {
        return trackId != null;
    }

    public boolean isSameTrack(NowPlaying other) {
        if (other == null) {
            return false;
        }
        return trackId == null ? other.trackId == null : trackId.equals(other.trackId);
    }

    @Override
    public String toString() {
        if (!hasTrack()) {
            return "(nothing playing)";
        }
        return artist + " — " + title + " [" + trackId + "]";
    }
}
