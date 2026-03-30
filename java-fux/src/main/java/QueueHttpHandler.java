import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * HTTP handler for queue control operations
 * POST /api/queue - Add effect to queue
 * DELETE /api/queue - Clear queue
 * GET /api/queue - Get current queue state
 */
public class QueueHttpHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(QueueHttpHandler.class);
    private final Gson gson = new Gson();
    private final EffectEngine effectEngine;
    
    public QueueHttpHandler(EffectEngine effectEngine) {
        this.effectEngine = effectEngine;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        
        // Handle OPTIONS preflight
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        String method = exchange.getRequestMethod();
        
        try {
            switch (method) {
                case "POST":
                    handleAddToQueue(exchange);
                    break;
                case "DELETE":
                    handleClearQueue(exchange);
                    break;
                case "GET":
                    handleGetQueue(exchange);
                    break;
                default:
                    sendError(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Error handling request: {}", e.getMessage(), e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Handle POST /api/queue - Add effect to queue
     * Request body: { "effect": "effect_name", "duration": 30, "repeat": 1 }
     */
    private void handleAddToQueue(HttpExchange exchange) throws IOException {
        try {
            // Read request body
            String requestBody = new String(
                exchange.getRequestBody().readAllBytes(), 
                StandardCharsets.UTF_8
            );
            
            if (requestBody.isEmpty()) {
                sendError(exchange, 400, "Request body is required");
                return;
            }
            
            // Parse JSON request
            JsonObject request = gson.fromJson(requestBody, JsonObject.class);
            
            if (!request.has("effect")) {
                sendError(exchange, 400, "Missing required field: effect");
                return;
            }
            
            String effectName = request.get("effect").getAsString();
            Integer duration = request.has("duration") ? request.get("duration").getAsInt() : null;
            Integer repeat = request.has("repeat") ? request.get("repeat").getAsInt() : null;
            
            // Create queue entry and add to queue
            QueueEntry entry = new QueueEntry(effectName, duration, repeat);
            effectEngine.addToQueue(entry);
            
            // Build success response
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Effect added to queue");
            response.addProperty("effect", effectName);
            if (duration != null) response.addProperty("duration", duration);
            if (repeat != null) response.addProperty("repeat", repeat);
            response.addProperty("queueSize", effectEngine.getQueueSnapshot().size());
            
            sendJsonResponse(exchange, 200, response);
            logger.info("Added effect to queue: {}", effectName);
            
        } catch (JsonSyntaxException e) {
            sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
        }
    }
    
    /**
     * Handle DELETE /api/queue - Clear queue
     */
    private void handleClearQueue(HttpExchange exchange) throws IOException {
        effectEngine.clearQueue();
        
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("message", "Queue cleared");
        response.addProperty("queueSize", 0);
        
        sendJsonResponse(exchange, 200, response);
        logger.info("Queue cleared");
    }
    
    /**
     * Handle GET /api/queue - Get current queue state
     */
    private void handleGetQueue(HttpExchange exchange) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("mode", effectEngine.getMode().toString());
        response.addProperty("currentEffect", effectEngine.getCurrentEffect());
        response.addProperty("queueSize", effectEngine.getQueueSnapshot().size());
        response.addProperty("queueCapacity", effectEngine.getQueueCapacity());
        
        Integer remainingSeconds = effectEngine.getRemainingSeconds();
        if (remainingSeconds != null) {
            response.addProperty("remainingSeconds", remainingSeconds);
        }
        
        // Add queue items
        com.google.gson.JsonArray queueArray = new com.google.gson.JsonArray();
        for (QueueEntry entry : effectEngine.getQueueSnapshot()) {
            JsonObject queueItem = new JsonObject();
            queueItem.addProperty("effect", entry.getEffectName());
            if (entry.getDuration() != null) {
                queueItem.addProperty("duration", entry.getDuration());
            }
            int repeatCount = entry.getRepeat();
            if (repeatCount > 1) {
                queueItem.addProperty("repeat", repeatCount);
            }
            queueArray.add(queueItem);
        }
        response.add("queue", queueArray);
        
        sendJsonResponse(exchange, 200, response);
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
