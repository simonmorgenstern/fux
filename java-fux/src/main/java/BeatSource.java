/**
 * A tempo reference an effect can render against.
 *
 * Implementations are either locked to real music ({@link BeatClock}, driven by
 * Spotify playback position) or free running ({@link FreeRunningBeatSource},
 * used for GIF previews and whenever no tempo is known).
 *
 * All phase values are continuous: effects should read them every frame rather
 * than trying to detect beat edges themselves.
 */
public interface BeatSource {

    /** True when the tempo comes from an actual track rather than a fallback. */
    boolean isSynced();

    /** Tempo in beats per minute. Always > 0 so effects can divide by it. */
    double getBpm();

    /**
     * Continuous position on the beat grid: the integer part is the beat
     * counter, the fraction is the position within that beat. Read this
     * (rather than index and phase separately) when both matter, so a beat
     * boundary crossing mid-frame cannot split them.
     */
    double getBeatPosition();

    /** Beats per bar, used for accenting downbeats. */
    int getBeatsPerBar();

    /** Position within the current beat, 0.0 at the beat, approaching 1.0 before the next. */
    default double getBeatPhase() {
        double position = getBeatPosition();
        return position - Math.floor(position);
    }

    /** Monotonic beat counter since the start of the track (negative before an offset nudge). */
    default long getBeatIndex() {
        return (long) Math.floor(getBeatPosition());
    }

    /** Playback position in seconds, for effects that want a slow continuous drift. */
    double getPositionSeconds();

    /** Position within the current bar, 0.0 on the downbeat. */
    default double getBarPhase() {
        int perBar = Math.max(1, getBeatsPerBar());
        double position = getBeatPosition();
        double inBar = position - Math.floor(position / perBar) * perBar;
        return inBar / perBar;
    }

    /** Index of the beat within its bar (0 == downbeat). */
    default int getBeatInBar() {
        int perBar = Math.max(1, getBeatsPerBar());
        return (int) Math.floorMod(getBeatIndex(), (long) perBar);
    }

    /** True while the current beat is the first of a bar. */
    default boolean isDownbeat() {
        return getBeatInBar() == 0;
    }

    /** Bar counter since the start of the track. */
    default long getBarIndex() {
        int perBar = Math.max(1, getBeatsPerBar());
        return Math.floorDiv(getBeatIndex(), (long) perBar);
    }
}
