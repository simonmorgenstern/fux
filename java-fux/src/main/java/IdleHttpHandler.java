import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * HTTP handler for idle mode endpoint.
 * Allows setting an effect to play indefinitely until replaced.
 */
public class IdleHttpHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(IdleHttpHandler.class);
    private final EffectEngine effectEngine;
    private final Gson gson;
    
    public IdleHttpHandler(EffectEngine effectEngine) {
        this.effectEngine = effectEngine;
        this.gson = new Gson();
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Add CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        
        String method = exchange.getRequestMethod();
        
        // Handle preflight OPTIONS request
        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        // Only accept POST requests
        if (!"POST".equals(method)) {
            sendError(exchange, 405, "Method not allowed. Use POST.");
            return;
        }
        
        try {
            // Read request body
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();
            
            // Parse JSON request
            JsonObject request = gson.fromJson(requestBody, JsonObject.class);
            
            if (!request.has("effect")) {
                sendError(exchange, 400, "Missing required field: effect");
                return;
            }
            
            String effectName = request.get("effect").getAsString();
            
            // Play effect in idle mode
            effectEngine.playIdle(effectName);
            
            // Build response
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("mode", "IDLE");
            response.addProperty("effect", effectName);
            response.addProperty("message", "Effect playing in idle mode (indefinite duration)");
            
            sendJson(exchange, 200, response);
            
            logger.info("Idle mode activated with effect: {}", effectName);
            
        } catch (Exception e) {
            logger.error("Error handling idle request: {}", e.getMessage(), e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    private void sendJson(HttpExchange exchange, int statusCode, JsonObject json) throws IOException {
        String response = gson.toJson(json);
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }
    
    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("status", statusCode);
        
        sendJson(exchange, statusCode, error);
    }
}
