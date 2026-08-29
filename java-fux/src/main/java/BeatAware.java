/**
 * Marker for effects that render against a tempo instead of a fixed frame timer.
 *
 * Beat-aware effects must install a {@link FreeRunningBeatSource} fallback in
 * {@code initialize()} so they still render standalone (GIF previews, queue and
 * idle modes). {@link EffectEngine} calls {@link #setBeatSource} in MUSIC mode
 * to swap in the live {@link BeatClock}.
 */
public interface BeatAware {
    void setBeatSource(BeatSource source);
}
