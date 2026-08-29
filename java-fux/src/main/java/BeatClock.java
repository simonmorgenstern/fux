/**
 * Reconstructs a beat grid for the track Spotify is currently playing.
 *
 * Spotify's polling loop only reports {@code progress_ms} every couple of
 * seconds, which is far too coarse to render off directly. This clock anchors
 * a monotonic {@link System#nanoTime()} reading to the last reported position
 * and interpolates between polls, so effects see a smooth, continuously
 * advancing phase instead of a 2-second staircase.
 *
 * Three layers of phase handling, as designed:
 *   1. Naive phase — beat 1 is assumed to sit at progress 0. BPM gives us the
 *      period, nothing free gives us the true downbeat.
 *   2. Manual offset — a per-track millisecond nudge dialled in by ear and then
 *      remembered forever (see {@link MusicSyncService}).
 *   3. Interpolation — each poll softly corrects the anchor instead of snapping
 *      it, so API latency jitter does not make the animation stutter. A large
 *      error (seek, track change, pause) re-anchors hard.
 */
public class BeatClock implements BeatSource {

    /** Position errors above this are treated as a seek, not as latency jitter. */
    private static final double HARD_RESYNC_MS = 750.0;
    /** Fraction of the residual error absorbed per poll when tracking smoothly. */
    private static final double SOFT_CORRECTION = 0.15;
    /** Tempo used for rendering when the track's BPM is unknown. */
    private static final double FALLBACK_BPM = 120.0;

    private String trackId;
    private double bpm;              // 0 when unknown
    private boolean playing;
    private long offsetMs;
    private int beatsPerBar = 4;

    private long anchorNanos;
    private double anchorPositionMs;
    private boolean anchored;

    public BeatClock() {
        this.anchorNanos = System.nanoTime();
    }

    /**
     * Feed a playback position observed from Spotify.
     *
     * @param trackId         Spotify track id, or null when nothing is playing
     * @param progressMs      reported playback position
     * @param playing         whether playback is running
     * @param observedAtNanos {@link System#nanoTime()} estimate of when that
     *                        position was actually true (mid-flight of the request)
     */
    public synchronized void anchor(String trackId, double progressMs, boolean playing, long observedAtNanos) {
        boolean trackChanged = this.trackId == null
            ? trackId != null
            : !this.trackId.equals(trackId);
        boolean transportChanged = this.playing != playing;

        if (trackChanged) {
            this.trackId = trackId;
            this.bpm = 0;
            this.offsetMs = 0;
        }

        this.playing = playing;

        if (!anchored || trackChanged || transportChanged) {
            hardAnchor(progressMs, observedAtNanos);
            return;
        }

        double predicted = predictPositionMs(observedAtNanos);
        double error = progressMs - predicted;

        if (Math.abs(error) > HARD_RESYNC_MS) {
            hardAnchor(progressMs, observedAtNanos);
        } else {
            // Re-base onto the observation instant while only absorbing part of
            // the error, so a single laggy response cannot yank the phase.
            anchorPositionMs = predicted + error * SOFT_CORRECTION;
            anchorNanos = observedAtNanos;
        }
    }

    /** Drop the anchor entirely (nothing playing / mode left). */
    public synchronized void clear() {
        trackId = null;
        bpm = 0;
        playing = false;
        offsetMs = 0;
        anchored = false;
        anchorPositionMs = 0;
        anchorNanos = System.nanoTime();
    }

    private void hardAnchor(double progressMs, long observedAtNanos) {
        anchorPositionMs = progressMs;
        anchorNanos = observedAtNanos;
        anchored = true;
    }

    private double predictPositionMs(long atNanos) {
        if (!playing) {
            return anchorPositionMs;
        }
        return anchorPositionMs + (atNanos - anchorNanos) / 1_000_000.0;
    }

    /** Interpolated playback position in milliseconds. */
    public synchronized double getPositionMs() {
        return predictPositionMs(System.nanoTime());
    }

    /** Apply a tempo, ignored if it arrives after the track already changed. */
    public synchronized void setBpm(String forTrackId, double bpm) {
        if (forTrackId != null && !forTrackId.equals(trackId)) {
            return;
        }
        this.bpm = bpm > 0 ? bpm : 0;
    }

    public synchronized void setBeatsPerBar(int beatsPerBar) {
        this.beatsPerBar = Math.max(1, beatsPerBar);
    }

    /** Millisecond phase correction; positive values push the beat grid later. */
    public synchronized void setOffsetMs(long offsetMs) {
        this.offsetMs = offsetMs;
    }

    public synchronized long getOffsetMs() {
        return offsetMs;
    }

    public synchronized String getTrackId() {
        return trackId;
    }

    public synchronized boolean isPlaying() {
        return playing;
    }

    // MARK: - BeatSource

    @Override
    public synchronized boolean isSynced() {
        return anchored && playing && bpm > 0;
    }

    @Override
    public synchronized double getBpm() {
        return bpm > 0 ? bpm : FALLBACK_BPM;
    }

    /** Raw tempo: 0 when no BPM could be found for this track. */
    public synchronized double getKnownBpm() {
        return bpm;
    }

    @Override
    public synchronized double getBeatPosition() {
        double effectiveMs = predictPositionMs(System.nanoTime()) - offsetMs;
        return effectiveMs * getBpm() / 60000.0;
    }

    @Override
    public synchronized int getBeatsPerBar() {
        return beatsPerBar;
    }

    @Override
    public double getPositionSeconds() {
        return getPositionMs() / 1000.0;
    }
}
