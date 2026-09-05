import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The same music effect definitions are used for arranged playback and preview. */
public class MusicEffectFactory {
    public static final List<String> IDS = Collections.unmodifiableList(Arrays.asList(
        "beat_pulse", "beat_sweep", "beat_sparkle", "firework", "heartbeat", "strobe", "center_pulse", "center_heartbeat"));
    public static Effect create(String id, PixelCoordinates coordinates, BeatSource clock, int beatsPerBar) throws IOException {
        Effect effect;
        switch (id) {
            case "beat_pulse": effect = new BeatPulseEffect(); break;
            case "beat_sweep": effect = new BeatSweepEffect(); break;
            case "beat_sparkle": effect = new BeatSparkleEffect(); break;
            case "firework": effect = new FireworkEffect(); break;
            case "heartbeat": effect = new HeartbeatEffect(); break;
            case "strobe": effect = new StrobeEffect(); break;
            case "center_pulse": effect = new CenterPulseEffect(); break;
            case "center_heartbeat": effect = new CenterHeartbeatEffect(); break;
            default: throw new IllegalArgumentException("Unsupported music effect");
        }
        JsonObject definition = null;
        for (String root : new String[]{"/home/pi/fux-effects/", "effects/", "../effects/"}) {
            File file = new File(root, id + ".json");
            if (file.isFile()) {
                try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                    definition = new Gson().fromJson(reader, JsonObject.class);
                }
                break;
            }
        }
        if (definition == null) {
            InputStream stream = MusicEffectFactory.class.getResourceAsStream("/effects/" + id + ".json");
            if (stream == null) throw new IOException("Missing effect definition: " + id);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                definition = new Gson().fromJson(reader, JsonObject.class);
            }
        }
        JsonObject parameters = definition.getAsJsonObject("parameters");
        parameters.addProperty("beats_per_bar", beatsPerBar);
        effect.initialize(parameters, coordinates);
        ((BeatAware) effect).setBeatSource(clock);
        return effect;
    }
}
