/**
 * A beat source with no external reference: it just runs at a fixed tempo off
 * the render time the effect is handed. Used for GIF previews and as the
 * fallback inside every beat-aware effect so they never have to null-check a
 * missing clock.
 *
 * Position is set by the effect each frame ({@link #setRenderTime}) rather than
 * read from the wall clock, because previews render a whole animation offline in
 * a few milliseconds — a wall-clock source would emit the same instant for every
 * frame and the preview would come out frozen.
 */
public class FreeRunningBeatSource implements BeatSource {
    private final double bpm;
    private final int beatsPerBar;
    private double renderTimeSeconds;

    public FreeRunningBeatSource(double bpm) {
        this(bpm, 4);
    }

    public FreeRunningBeatSource(double bpm, int beatsPerBar) {
        this.bpm = bpm > 0 ? bpm : 120.0;
        this.beatsPerBar = Math.max(1, beatsPerBar);
    }

    /** Advances the source to the effect's current frame time. */
    public void setRenderTime(double seconds) {
        this.renderTimeSeconds = seconds;
    }

    @Override
    public boolean isSynced() {
        return false;
    }

    @Override
    public double getBpm() {
        return bpm;
    }

    @Override
    public double getBeatPosition() {
        return renderTimeSeconds * bpm / 60.0;
    }

    @Override
    public int getBeatsPerBar() {
        return beatsPerBar;
    }

    @Override
    public double getPositionSeconds() {
        return renderTimeSeconds;
    }
}
