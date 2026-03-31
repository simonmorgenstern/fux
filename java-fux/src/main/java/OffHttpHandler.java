import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * HTTP handler for OFF mode endpoint.
 * Turns off all LEDs immediately.
 */
public class OffHttpHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(OffHttpHandler.class);
    private final EffectEngine effectEngine;
    private final Gson gson;

    public OffHttpHandler(EffectEngine effectEngine) {
        this.effectEngine = effectEngine;
        this.gson = new Gson();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        String method = exchange.getRequestMethod();

        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equals(method)) {
            sendError(exchange, 405, "Method not allowed. Use POST.");
            return;
        }

        effectEngine.turnOff();

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("mode", "OFF");
        response.addProperty("message", "All LEDs turned off");

        sendJson(exchange, 200, response);
        logger.info("OFF mode activated via HTTP");
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
