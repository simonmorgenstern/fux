import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP handler for effect listing and detail endpoints
 * GET /api/effects - List all available effects
 * GET /api/effects/{name} - Get details for a specific effect
 */
public class EffectsHttpHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(EffectsHttpHandler.class);
    private final Gson gson = new Gson();
    
    // List of all available effects (should match EffectEngine)
    private static final String[] AVAILABLE_EFFECTS = {
        "radial_wave",
        "rainbow_pulse",
        "sparkle",
        "breathing",
        "snake",
        "meteor_shower",
        "firework",
        "rain",
        "aurora",
        "bilateral_fill",
        "motion_blur",
        "gradient",
        "box_mirror",
        "eye_blink",
        "diamond_pulse",
        "box_wave",
        "outside_spin",
        "heartbeat",
        "lava_lamp",
        "contour_trace"
    };
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        
        // Handle OPTIONS preflight
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        // Only allow GET
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        
        String path = exchange.getRequestURI().getPath();
        
        try {
            if (path.equals("/api/effects") || path.equals("/api/effects/")) {
                // List all effects
                handleListEffects(exchange);
            } else if (path.startsWith("/api/effects/")) {
                // Get specific effect details
                String effectName = path.substring("/api/effects/".length());
                handleGetEffect(exchange, effectName);
            } else {
                sendError(exchange, 404, "Not found");
            }
        } catch (Exception e) {
            logger.error("Error handling request: {}", e.getMessage(), e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Handle GET /api/effects - List all available effects
     */
    private void handleListEffects(HttpExchange exchange) throws IOException {
        JsonArray effects = new JsonArray();
        
        for (String effectName : AVAILABLE_EFFECTS) {
            JsonObject effectInfo = loadEffectMetadata(effectName);
            if (effectInfo != null) {
                effects.add(effectInfo);
            }
        }
        
        JsonObject response = new JsonObject();
        response.addProperty("count", effects.size());
        response.add("effects", effects);
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * Handle GET /api/effects/{name} - Get specific effect details
     */
    private void handleGetEffect(HttpExchange exchange, String effectName) throws IOException {
        // Validate effect name exists
        boolean found = false;
        for (String name : AVAILABLE_EFFECTS) {
            if (name.equals(effectName)) {
                found = true;
                break;
            }
        }
        
        if (!found) {
            sendError(exchange, 404, "Effect not found: " + effectName);
            return;
        }
        
        JsonObject effectInfo = loadEffectMetadata(effectName);
        if (effectInfo == null) {
            sendError(exchange, 500, "Failed to load effect metadata");
            return;
        }
        
        sendJsonResponse(exchange, 200, effectInfo);
    }
    
    /**
     * Load effect metadata from JSON file
     * Returns a summary object with useful frontend information
     */
    private JsonObject loadEffectMetadata(String effectName) {
        try {
            // Try multiple paths to find the effect definition
            String[] pathsToTry = {
                "/home/pi/fux-effects/" + effectName + ".json",
                effectName + ".json",
                "effects/" + effectName + ".json",
                "../effects/" + effectName + ".json"
            };
            
            JsonObject effectDef = null;
            
            // Try file system paths
            for (String path : pathsToTry) {
                try {
                    effectDef = gson.fromJson(new FileReader(path), JsonObject.class);
                    break;
                } catch (java.io.FileNotFoundException e) {
                    // Try next path
                }
            }
            
            // Try classpath as fallback
            if (effectDef == null) {
                String[] classpathPaths = {"animations/", "effects/"};
                for (String classpathDir : classpathPaths) {
                    try {
                        java.io.InputStream is = getClass().getClassLoader()
                            .getResourceAsStream(classpathDir + effectName + ".json");
                        if (is != null) {
                            effectDef = gson.fromJson(
                                new java.io.InputStreamReader(is), 
                                JsonObject.class
                            );
                            break;
                        }
                    } catch (Exception e) {
                        // Try next classpath path
                    }
                }
            }
            
            if (effectDef == null) {
                logger.warn("Could not find effect definition for: {}", effectName);
                return null;
            }
            
            // Build API response with useful metadata
            JsonObject response = new JsonObject();
            
            // Basic info
            response.addProperty("id", effectName);
            response.addProperty("name", 
                effectDef.has("name") ? effectDef.get("name").getAsString() : effectName);
            response.addProperty("description", 
                effectDef.has("description") ? effectDef.get("description").getAsString() : "");
            response.addProperty("algorithm", 
                effectDef.has("algorithm") ? effectDef.get("algorithm").getAsString() : effectName);
            
            // Type/category
            response.addProperty("type", 
                effectDef.has("type") ? effectDef.get("type").getAsString() : "procedural");
            
            // Performance info
            response.addProperty("fps", 
                effectDef.has("fps") ? effectDef.get("fps").getAsInt() : 30);
            
            // Version
            if (effectDef.has("version")) {
                response.addProperty("version", effectDef.get("version").getAsString());
            }
            
            // Parameters with metadata
            if (effectDef.has("parameters")) {
                JsonObject params = effectDef.getAsJsonObject("parameters");
                JsonArray paramsList = new JsonArray();
                
                // Convert parameters to a more frontend-friendly format
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : params.entrySet()) {
                    String key = entry.getKey();
                    com.google.gson.JsonElement value = entry.getValue();
                    
                    JsonObject param = new JsonObject();
                    param.addProperty("name", key);
                    
                    // Determine parameter type and add metadata
                    if (value.isJsonPrimitive()) {
                        if (value.getAsJsonPrimitive().isNumber()) {
                            param.addProperty("type", "number");
                            param.addProperty("default", value.getAsNumber());
                        } else if (value.getAsJsonPrimitive().isBoolean()) {
                            param.addProperty("type", "boolean");
                            param.addProperty("default", value.getAsBoolean());
                        } else {
                            param.addProperty("type", "string");
                            param.addProperty("default", value.getAsString());
                        }
                    } else if (value.isJsonArray()) {
                        param.addProperty("type", "array");
                        param.add("default", value.getAsJsonArray());
                    } else {
                        param.addProperty("type", "object");
                        param.add("default", value.getAsJsonObject());
                    }
                    
                    paramsList.add(param);
                }
                
                response.add("parameters", paramsList);
            }
            
            // Add tags/categories based on effect characteristics
            JsonArray tags = new JsonArray();
            String name = effectDef.has("name") ? 
                effectDef.get("name").getAsString().toLowerCase() : effectName.toLowerCase();
            String desc = effectDef.has("description") ? 
                effectDef.get("description").getAsString().toLowerCase() : "";
            
            // Auto-tag based on name/description
            if (name.contains("rain") || desc.contains("rain")) tags.add("weather");
            if (name.contains("fire") || desc.contains("fire")) tags.add("flame");
            if (name.contains("meteor") || name.contains("firework")) tags.add("particle");
            if (name.contains("pulse") || name.contains("breath")) tags.add("pulse");
            if (name.contains("wave") || name.contains("ripple")) tags.add("wave");
            if (name.contains("rainbow") || name.contains("gradient")) tags.add("colorful");
            if (name.contains("sparkle")) tags.add("sparkle");
            if (name.contains("snake") || name.contains("spin")) tags.add("motion");
            if (name.contains("aurora")) tags.add("aurora");
            
            if (tags.size() > 0) {
                response.add("tags", tags);
            }
            
            return response;
            
        } catch (Exception e) {
            logger.error("Error loading effect metadata for {}: {}", effectName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Send JSON response
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject json) 
            throws IOException {
        String response = gson.toJson(json);
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    /**
     * Send error response
     */
    private void sendError(HttpExchange exchange, int statusCode, String message) 
            throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("status", statusCode);
        
        sendJsonResponse(exchange, statusCode, error);
    }
}
