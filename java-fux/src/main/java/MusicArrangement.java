import java.util.*;

/** Versioned, single-lane musical arrangement. Intervals are [start, end). */
public class MusicArrangement {
    public int version;
    public String trackId, title, artist;
    public double durationSeconds, bpm, offsetMs;
    public int beatsPerBar;
    public List<Clip> clips;
    public static class Clip {
        public String id, effect;
        public double startBeat, lengthBeats;
    }
    public void validate() {
        require(version == 1, "Unsupported arrangement version");
        require(trackId != null && trackId.matches("[A-Za-z0-9]{22}"), "Invalid Spotify track ID");
        require(title != null && artist != null && title.length() <= 1000 && artist.length() <= 1000, "Invalid song metadata");
        require(finite(durationSeconds) && durationSeconds > 0 && durationSeconds <= 86400, "Invalid song duration");
        require(finite(bpm) && bpm >= 20 && bpm <= 400, "BPM must be between 20 and 400");
        require(beatsPerBar >= 1 && beatsPerBar <= 16, "Invalid beats per bar");
        require(finite(offsetMs) && Math.abs(offsetMs) <= durationSeconds * 1000, "Invalid beat offset");
        require(clips != null && clips.size() <= 2000, "Too many clips");
        Set<String> ids = new HashSet<>();
        for (Clip c : clips) {
            require(c != null && c.id != null && c.id.length() <= 100 && ids.add(c.id), "Invalid or duplicate clip ID");
            require(MusicEffectFactory.IDS.contains(c.effect), "Unsupported music effect");
            require(finite(c.startBeat) && finite(c.lengthBeats) && c.lengthBeats > 0, "Invalid clip timing");
            require(seconds(c.startBeat) >= -0.000001 && seconds(c.startBeat + c.lengthBeats) <= durationSeconds + 0.000001, "Clip extends outside song");
        }
        clips.sort(Comparator.comparingDouble(c -> c.startBeat));
        double end = -Double.MAX_VALUE;
        for (Clip c : clips) {
            require(c.startBeat >= end - 0.0000001, "Effects cannot overlap");
            end = c.startBeat + c.lengthBeats;
        }
    }
    public double seconds(double beat) { return offsetMs / 1000 + beat * 60 / bpm; }
    public double beat(double seconds) { return (seconds - offsetMs / 1000) * bpm / 60; }
    public Clip clipAt(double seconds) {
        if (seconds < 0 || seconds >= durationSeconds) return null;
        double beat = beat(seconds);
        for (Clip c : clips) if (beat >= c.startBeat && beat < c.startBeat + c.lengthBeats) return c;
        return null;
    }
    static boolean finite(double n) { return !Double.isNaN(n) && !Double.isInfinite(n); }
    static void require(boolean ok, String message) { if (!ok) throw new IllegalArgumentException(message); }
}
