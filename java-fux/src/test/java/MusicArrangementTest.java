import junit.framework.TestCase;
import com.google.gson.Gson;
import java.nio.file.*;
import java.awt.Color;
import java.util.*;
import java.lang.reflect.*;

public class MusicArrangementTest extends TestCase {
    private final Gson gson = new Gson();
    static final String ID = "0123456789012345678901";
    MusicArrangement show() {
        MusicArrangement a = new MusicArrangement();
        a.version = 1; a.trackId = ID; a.title = "Test"; a.artist = "Fixture";
        a.durationSeconds = 120; a.bpm = 120; a.offsetMs = 1000; a.beatsPerBar = 4;
        a.clips = new ArrayList<>();
        a.clips.add(clip("a", "beat_pulse", 0, 4));
        a.clips.add(clip("b", "beat_sweep", 4, 4));
        a.validate(); return a;
    }
    MusicArrangement.Clip clip(String id, String effect, double start, double length) {
        MusicArrangement.Clip c = new MusicArrangement.Clip(); c.id = id; c.effect = effect; c.startBeat = start; c.lengthBeats = length; return c;
    }
    public void testBoundariesAndSeek() {
        MusicArrangement a = show();
        assertNull(a.clipAt(0.999)); assertEquals("a", a.clipAt(1).id);
        assertEquals("a", a.clipAt(2.999).id); assertEquals("b", a.clipAt(3).id);
        assertNull(a.clipAt(5)); assertEquals("a", a.clipAt(1.5).id);
        assertNull(a.clipAt(120));
    }
    public void testRejectsOverlapAndNonfiniteValues() {
        MusicArrangement a = show(); a.clips.get(1).startBeat = 3;
        reject(a); a = show(); a.bpm = Double.NaN; reject(a);
        a = show(); a.clips.get(0).lengthBeats = Double.POSITIVE_INFINITY; reject(a);
        a = show(); a.trackId = "../../spotify_tokens"; reject(a);
        a = show(); a.clips.get(0).effect = "unknown"; reject(a);
        a = show(); a.clips.get(0).startBeat = -4; reject(a);
    }
    private void reject(MusicArrangement a) {
        try { a.validate(); fail("Invalid arrangement accepted"); } catch (IllegalArgumentException expected) { }
    }
    public void testPersistenceAndFailedSaveKeepsPreviousVersion() throws Exception {
        Path dir = Files.createTempDirectory("fux-arrangements-test");
        MusicArrangementStore store = new MusicArrangementStore(dir);
        MusicArrangement a = show(); store.save(a);
        a.title = "Unsaved mutation";
        assertEquals("Test", store.get(ID).title);
        assertEquals("Test", new MusicArrangementStore(dir).get(ID).title);
        a.clips.get(0).lengthBeats = 1000;
        try { store.save(a); fail(); } catch (IllegalArgumentException expected) { }
        assertEquals(4.0, new MusicArrangementStore(dir).get(ID).clips.get(0).lengthBeats, 0.0001);
        assertNull(store.get("9999999999999999999999"));
    }
    public void testSharedRendererMatchesForStatelessEffectAndFreezes() throws Exception {
        PixelCoordinates coords = PixelCoordinates.loadWithFallback();
        MusicArrangement a = show();
        try (ArrangementRenderer preview = new ArrangementRenderer(coords); ArrangementRenderer live = new ArrangementRenderer(coords)) {
            Map<Integer, Color> first = preview.render(a, 1.25);
            assertFalse(first.isEmpty());
            assertEquals(first, live.render(a, 1.25));
            assertEquals(first, preview.render(a, 1.25));
            assertTrue(preview.render(a, 6).isEmpty());
            assertEquals(first, preview.render(a, 1.25));
            for (String effect : MusicEffectFactory.IDS) {
                MusicArrangement b = show(); b.clips.get(0).effect = effect;
                for (double t = 1; t < 2; t += 0.04) {
                    for (Integer led : preview.render(b, t).keySet()) assertTrue(led >= 0 && led < 268);
                }
            }
        }
    }
    public void testMusicModeUsesSavedArrangementWithoutTempoLookupOrMac() throws Exception {
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createTempDirectory("fux-engine-test").toString());
        EffectEngine engine = new EffectEngine(null);
        MusicSyncService service = engine.getMusicSyncService();
        try {
            // Drive real engine selection with a fixture clock, without network polling or GPIO.
            Field mode = EffectEngine.class.getDeclaredField("mode"); mode.setAccessible(true); mode.set(engine, ControlMode.MUSIC);
            Field running = MusicSyncService.class.getDeclaredField("running"); running.setAccessible(true); running.set(service, true);
            Field current = MusicSyncService.class.getDeclaredField("current"); current.setAccessible(true);
            Method render = EffectEngine.class.getDeclaredMethod("renderArrangement"); render.setAccessible(true);
            engine.getArrangementStore().save(show());
            NowPlaying track = new NowPlaying(ID, "Test", "Fixture", 120000, 1500, true, System.nanoTime());
            current.set(service, track); service.getBeatClock().anchor(ID, 1500, true, track.observedAtNanos); service.onPlaybackConfirmed();
            assertEquals(true, render.invoke(engine)); assertEquals("beat_pulse", engine.getCurrentEffect());
            service.getBeatClock().anchor(ID, 3500, true, System.nanoTime());
            assertEquals(true, render.invoke(engine)); assertEquals("beat_sweep", engine.getCurrentEffect());
            service.getBeatClock().anchor(ID, 6000, true, System.nanoTime());
            assertEquals(true, render.invoke(engine)); assertEquals("Blackout", engine.getCurrentEffect());
            current.set(service, new NowPlaying(ID, "Test", "Fixture", 120000, 6000, false, System.nanoTime()));
            assertEquals(false, render.invoke(engine));
            current.set(service, new NowPlaying("9999999999999999999999", "Other", "Fixture", 120000, 0, true, System.nanoTime()));
            assertEquals(false, render.invoke(engine));
        } finally { service.shutdown(); System.setProperty("user.home", home); }
    }
}
