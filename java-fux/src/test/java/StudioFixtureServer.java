import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.lang.reflect.*;

/** Manual UI fixture. No credentials, Spotify requests or physical LEDs. */
public class StudioFixtureServer {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("fux-studio-fixture").toString());
        EffectEngine engine = new EffectEngine(null);
        MusicSyncService music = new MusicSyncService() {
            private final long start = System.nanoTime();
            public synchronized void start() { }
            public boolean hasFreshPlayback() { return true; }
            public NowPlaying getNowPlaying() {
                double seconds = (System.nanoTime() - start) / 1e9 % 120;
                return new NowPlaying(MusicArrangementTest.ID, "Studio test song", "Local test fixture · no Spotify playback", 120000, (long)(seconds * 1000), true, System.nanoTime());
            }
            public MusicState snapshot() {
                NowPlaying now = getNowPlaying();
                MusicState s = new MusicState(); s.configured = true; s.authorized = true; s.playing = true;
                s.polling = true; s.synced = true; s.trackId = now.trackId; s.title = now.title; s.artist = now.artist;
                s.bpm = 120.0; s.positionSeconds = now.progressMs / 1000.0; return s;
            }
        };
        Field field = EffectEngine.class.getDeclaredField("musicSyncService"); field.setAccessible(true); field.set(engine, music);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 18765), 0);
        server.createContext("/api/studio", new StudioHttpHandler(engine)); server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.stop(0); music.shutdown(); }));
        System.out.println("Studio UI fixture at http://127.0.0.1:18765");
    }
}
