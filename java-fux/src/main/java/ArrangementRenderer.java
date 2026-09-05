import java.awt.Color;
import java.io.IOException;
import java.util.*;

/** Independent session: never accesses hardware or mutates the live Spotify clock. */
public class ArrangementRenderer implements AutoCloseable {
    private final PixelCoordinates coordinates;
    private MusicArrangement show;
    private MusicArrangement.Clip clip;
    private Effect effect;
    private double renderedAt = Double.NaN;
    private long frame;
    private Map<Integer, Color> pixels = Collections.emptyMap();
    private final Clock clock = new Clock();
    private class Clock implements BeatSource {
        double time;
        public boolean isSynced() { return true; }
        public double getBpm() { return show.bpm; }
        public double getBeatPosition() { return show.beat(time); }
        public int getBeatsPerBar() { return show.beatsPerBar; }
        public double getPositionSeconds() { return time; }
    }
    public ArrangementRenderer(PixelCoordinates coordinates) { this.coordinates = coordinates; }
    public synchronized Map<Integer, Color> render(MusicArrangement arrangement, double time) throws IOException {
        MusicArrangement.require(MusicArrangement.finite(time) && time >= 0, "Invalid preview position");
        if (coordinates == null) throw new IOException("LED coordinates unavailable");
        MusicArrangement.Clip next = arrangement.clipAt(time);
        boolean reset = show != arrangement || next != clip || Double.isNaN(renderedAt)
            || time < renderedAt - 0.001 || time - renderedAt > 0.75;
        if (reset) {
            close(); show = arrangement; clip = next; frame = 0; clock.time = time;
            if (clip != null) effect = MusicEffectFactory.create(clip.effect, coordinates, clock, show.beatsPerBar);
            renderedAt = time;
            pixels = effect == null ? Collections.emptyMap() : effect.renderFrame(frame++, Math.max(0, time - show.seconds(clip.startBeat)));
        } else if (effect != null) {
            double step = 1.0 / Math.max(1, Math.min(60, effect.getFPS()));
            while (renderedAt + step <= time + 0.000001) {
                renderedAt += step; clock.time = renderedAt;
                pixels = effect.renderFrame(frame++, Math.max(0, renderedAt - show.seconds(clip.startBeat)));
            }
        }
        return pixels;
    }
    public synchronized void close() {
        if (effect != null) effect.dispose();
        effect = null; clip = null; show = null; renderedAt = Double.NaN;
        pixels = Collections.emptyMap();
    }
}
