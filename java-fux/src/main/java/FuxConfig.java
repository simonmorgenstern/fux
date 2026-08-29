import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * Small helper around the per-user {@code ~/.fux} directory, where music mode
 * keeps its credentials, tokens and caches. None of these files belong in the
 * repository — the directory is created on demand with owner-only permissions.
 */
public class FuxConfig {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** {@code ~/.fux}, created if missing. */
    public static File dir() {
        String home = System.getProperty("user.home");
        File dir = new File(home == null ? "." : home, ".fux");
        if (!dir.exists() && dir.mkdirs()) {
            restrictToOwner(dir);
        }
        return dir;
    }

    public static File file(String name) {
        return new File(dir(), name);
    }

    /** Reads a JSON object, returning null if the file is missing or malformed. */
    public static JsonObject readJson(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            Reader reader = new InputStreamReader(in, UTF8);
            return GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            System.err.println("Could not read " + file + ": " + e.getMessage());
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    /** Writes a JSON object with owner-only permissions. */
    public static boolean writeJson(File file, Object value) {
        FileOutputStream out = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            out = new FileOutputStream(file);
            Writer writer = new OutputStreamWriter(out, UTF8);
            GSON.toJson(value, writer);
            writer.flush();
            restrictToOwner(file);
            return true;
        } catch (Exception e) {
            System.err.println("Could not write " + file + ": " + e.getMessage());
            return false;
        } finally {
            closeQuietly(out);
        }
    }

    /**
     * Reads a setting from {@code ~/.fux/spotify.json}, falling back to an
     * environment variable so the Pi can be configured either way.
     */
    public static String setting(JsonObject config, String key, String envVar, String fallback) {
        if (config != null && config.has(key) && !config.get(key).isJsonNull()) {
            String value = config.get(key).getAsString().trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        String env = System.getenv(envVar);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return fallback;
    }

    private static void restrictToOwner(File file) {
        try {
            file.setReadable(false, false);
            file.setWritable(false, false);
            file.setExecutable(false, false);
            file.setReadable(true, true);
            file.setWritable(true, true);
            if (file.isDirectory()) {
                file.setExecutable(true, true);
            }
        } catch (Exception e) {
            // Best effort — filesystem may not support POSIX permissions
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
                // Nothing useful to do
            }
        }
    }
}
