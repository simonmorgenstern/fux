import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
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
        "diamond_pulse",
        "outside_spin",
        "heartbeat",
        "lava_lamp",
        "contour_trace",
        "strobe",
        "body_flash",
        "ripple_burst",
        "color_chase",
        "drop_pulse",
        "flood_fill",
        "pulse_network",
        "forest_fire",
        "lightning",
        "firefly_sync"
    };
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, PUT, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        // Handle OPTIONS preflight
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            if ("GET".equals(method)) {
                if (path.equals("/api/effects") || path.equals("/api/effects/")) {
                    handleListEffects(exchange);
                } else if (path.startsWith("/api/effects/")) {
                    String effectName = path.substring("/api/effects/".length());
                    handleGetEffect(exchange, effectName);
                } else {
                    sendError(exchange, 404, "Not found");
                }
            } else if ("PUT".equals(method) && path.startsWith("/api/effects/")) {
                String effectName = path.substring("/api/effects/".length());
                handleUpdateEffect(exchange, effectName);
            } else {
                sendError(exchange, 405, "Method not allowed");
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
     * Handle PUT /api/effects/{name} - Update effect parameters
     */
    private void handleUpdateEffect(HttpExchange exchange, String effectName) throws IOException {
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

        // Read request body
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject updates = gson.fromJson(body, JsonObject.class);

        if (!updates.has("parameters")) {
            sendError(exchange, 400, "Request must contain 'parameters' object");
            return;
        }

        // Find the writable effect JSON file
        String filePath = findEffectFilePath(effectName);
        if (filePath == null) {
            sendError(exchange, 500, "Cannot find writable effect file for: " + effectName);
            return;
        }

        // Load current effect definition
        JsonObject effectDef = gson.fromJson(new FileReader(filePath), JsonObject.class);

        // Merge updated parameters
        JsonObject newParams = updates.getAsJsonObject("parameters");
        effectDef.add("parameters", newParams);

        // Write back to file
        Gson prettyGson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(prettyGson.toJson(effectDef));
            writer.write("\n");
        }

        logger.info("Updated parameters for effect: {}", effectName);

        // Return updated metadata
        JsonObject effectInfo = loadEffectMetadata(effectName);
        sendJsonResponse(exchange, 200, effectInfo);
    }

    /**
     * Find the writable file path for an effect definition
     */
    private String findEffectFilePath(String effectName) {
        String[] pathsToTry = {
            "/home/pi/fux-effects/" + effectName + ".json",
            effectName + ".json",
            "effects/" + effectName + ".json",
            "../effects/" + effectName + ".json"
        };
        for (String path : pathsToTry) {
            java.io.File f = new java.io.File(path);
            if (f.exists() && f.canWrite()) {
                return path;
            }
        }
        return null;
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
