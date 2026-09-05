import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Disk writes complete before a new arrangement becomes visible to playback. */
public class MusicArrangementStore {
    private final Path directory;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, MusicArrangement> cache = new HashMap<>();
    public MusicArrangementStore() { this(FuxConfig.file("arrangements").toPath()); }
    public MusicArrangementStore(Path directory) { this.directory = directory; }
    private Path path(String id) {
        MusicArrangement.require(id != null && id.matches("[A-Za-z0-9]{22}"), "Invalid Spotify track ID");
        return directory.resolve(id + ".json");
    }
    public synchronized MusicArrangement get(String id) throws IOException {
        Path file = path(id);
        if (cache.containsKey(id)) return cache.get(id);
        MusicArrangement show = null;
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                show = gson.fromJson(reader, MusicArrangement.class);
                MusicArrangement.require(show != null && id.equals(show.trackId), "Invalid saved arrangement");
                show.validate();
            } catch (RuntimeException e) { throw new IOException("Cannot read saved arrangement: " + e.getMessage(), e); }
        }
        cache.put(id, show);
        return show;
    }
    public synchronized void save(MusicArrangement show) throws IOException {
        show.validate();
        // Own the saved object so a caller cannot mutate the playback snapshot.
        MusicArrangement saved = gson.fromJson(gson.toJson(show), MusicArrangement.class);
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".arrangement-", ".tmp");
        try {
            Files.write(temporary, gson.toJson(saved).getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, path(saved.trackId), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            cache.put(saved.trackId, saved);
        } finally { Files.deleteIfExists(temporary); }
    }
}
