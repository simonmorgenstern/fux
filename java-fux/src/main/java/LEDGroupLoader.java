import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Centralized utility for loading LED groups from led-groups.json
 * Tries multiple path resolution strategies to find the file
 */
public class LEDGroupLoader {
    private static final Logger logger = LoggerFactory.getLogger(LEDGroupLoader.class);
    
    private static JsonArray cachedGroups = null;
    private static String loadedFromPath = null;
    
    /**
     * Load LED group by name (e.g., "eyes", "diamond", "outside", "boxes1")
     */
    public static Set<Integer> loadGroup(String groupName) {
        Set<Integer> leds = new HashSet<>();
        
        try {
            // Load groups file if not cached
            if (cachedGroups == null) {
                loadGroupsFile();
            }
            
            if (cachedGroups == null) {
                logger.error("LED groups file not loaded - cannot load group '{}'", groupName);
                return leds;
            }
            
            // Find the group
            for (int i = 0; i < cachedGroups.size(); i++) {
                JsonObject group = cachedGroups.get(i).getAsJsonObject();
                if (group.get("name").getAsString().equals(groupName)) {
                    JsonArray ranges = group.getAsJsonArray("ranges");
                    
                    // Parse ranges
                    for (int j = 0; j < ranges.size(); j++) {
                        JsonObject range = ranges.get(j).getAsJsonObject();
                        int start = range.get("start").getAsInt();
                        int end = range.get("end").getAsInt();
                        
                        for (int led = start; led <= end; led++) {
                            leds.add(led);
                        }
                    }
                    
                    logger.debug("Loaded LED group '{}': {} LEDs", groupName, leds.size());
                    return leds;
                }
            }
            
            logger.warn("LED group '{}' not found in led-groups.json", groupName);
            
        } catch (Exception e) {
            logger.error("Error loading LED group '{}': {}", groupName, e.getMessage());
        }
        
        return leds;
    }
    
    /**
     * Load the led-groups.json file from various possible locations
     */
    private static void loadGroupsFile() {
        Gson gson = new Gson();
        
        // Try multiple paths in order of preference
        String[] pathsToTry = {
            // Absolute path (development/deployment on server)
            "/home/simon/.openclaw/workspace/fux/led-namer/led-groups.json",
            "/home/pi/fux/led-namer/led-groups.json",
            // Relative paths
            "led-namer/led-groups.json",
            "../led-namer/led-groups.json",
            "../../led-namer/led-groups.json",
            // Current directory
            "led-groups.json"
        };
        
        // Try file system paths
        for (String path : pathsToTry) {
            try {
                FileReader reader = new FileReader(path);
                cachedGroups = gson.fromJson(reader, JsonArray.class);
                reader.close();
                
                if (cachedGroups != null && cachedGroups.size() > 0) {
                    loadedFromPath = path;
                    logger.info("✓ Loaded LED groups from: {}", path);
                    return;
                }
            } catch (Exception e) {
                // Try next path
            }
        }
        
        // Try classpath as fallback
        String[] classpathPaths = {
            "led-groups.json",
            "led-namer/led-groups.json",
            "assets/led-groups.json"
        };
        
        for (String classpathPath : classpathPaths) {
            try {
                InputStream is = LEDGroupLoader.class.getClassLoader().getResourceAsStream(classpathPath);
                if (is != null) {
                    cachedGroups = gson.fromJson(new InputStreamReader(is), JsonArray.class);
                    is.close();
                    
                    if (cachedGroups != null && cachedGroups.size() > 0) {
                        loadedFromPath = "classpath:" + classpathPath;
                        logger.info("✓ Loaded LED groups from classpath: {}", classpathPath);
                        return;
                    }
                }
            } catch (Exception e) {
                // Try next classpath
            }
        }
        
        logger.error("⚠ Failed to load LED groups file from any location!");
    }
    
    /**
     * Get all available group names
     */
    public static String[] getAvailableGroups() {
        if (cachedGroups == null) {
            loadGroupsFile();
        }
        
        if (cachedGroups == null) {
            return new String[0];
        }
        
        String[] names = new String[cachedGroups.size()];
        for (int i = 0; i < cachedGroups.size(); i++) {
            JsonObject group = cachedGroups.get(i).getAsJsonObject();
            names[i] = group.get("name").getAsString();
        }
        
        return names;
    }
    
    /**
     * Get info about loaded groups (for debugging)
     */
    public static String getLoadedInfo() {
        if (loadedFromPath == null) {
            return "LED groups not loaded";
        }
        
        int groupCount = cachedGroups != null ? cachedGroups.size() : 0;
        return String.format("LED groups loaded from %s (%d groups)", loadedFromPath, groupCount);
    }
}
