import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP server for preview endpoint.
 * Runs separately from WebSocket server to handle REST API requests.
 */
public class PreviewHttpServer {
    private static final Logger logger = LoggerFactory.getLogger(PreviewHttpServer.class);
    private static final int DEFAULT_PORT = 8080;
    private static final int FALLBACK_PORT = 8888;
    
    private HttpServer httpServer;
    private int port;
    private final EffectEngine effectEngine;
    
    public PreviewHttpServer(EffectEngine effectEngine) throws IOException {
        this.effectEngine = effectEngine;
        
        // Try to start on default port, fallback to alternate port if needed
        int actualPort = DEFAULT_PORT;
        try {
            this.httpServer = HttpServer.create(new InetSocketAddress(actualPort), 0);
            this.port = actualPort;
            logger.info("HTTP server will run on port: {}", actualPort);
        } catch (IOException e) {
            logger.warn("Port {} not available, trying port {}", actualPort, FALLBACK_PORT);
            try {
                this.httpServer = HttpServer.create(new InetSocketAddress(FALLBACK_PORT), 0);
                this.port = FALLBACK_PORT;
                logger.info("HTTP server will run on port: {}", FALLBACK_PORT);
            } catch (IOException e2) {
                logger.error("Failed to bind to any port");
                throw e2;
            }
        }
        
        // Register handlers
        setupHandlers();
    }
    
    /**
     * Setup HTTP request handlers
     */
    private void setupHandlers() {
        // Preview GIF endpoint
        httpServer.createContext("/api/preview", new PreviewHttpHandler(effectEngine));
        
        // Effects listing and detail endpoints
        httpServer.createContext("/api/effects", new EffectsHttpHandler());
        
        // Queue control endpoint
        httpServer.createContext("/api/queue", new QueueHttpHandler(effectEngine));
        
        // Health check endpoint
        httpServer.createContext("/health", exchange -> {
            try {
                String response = "{\"status\":\"ok\",\"timestamp\":" + System.currentTimeMillis() + "}";
                byte[] responseBytes = response.getBytes("UTF-8");
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            } catch (Exception e) {
                logger.error("Error handling health check: {}", e.getMessage());
            }
        });
        
        // Root endpoint with API documentation
        httpServer.createContext("/", exchange -> {
            try {
                String response = "Fux Preview API\n" +
                    "================\n\n" +
                    "GET /api/effects - List all available LED effects\n" +
                    "Response: JSON array with effect metadata (name, description, parameters, tags)\n\n" +
                    "GET /api/effects/{name} - Get details for a specific effect\n" +
                    "Response: JSON object with full effect metadata\n\n" +
                    "POST /api/preview - Generate animated GIF preview\n" +
                    "Request body (JSON):\n" +
                    "  {\n" +
                    "    \"effect\": \"effect_name\",\n" +
                    "    \"duration\": 2.0,          (seconds, default: 2, range: 0.1-30)\n" +
                    "    \"fps\": 15,                (frames per second, default: 15, range: 1-60)\n" +
                    "    \"pixelSize\": 8            (pixel size, default: 8, range: 1-32)\n" +
                    "  }\n\n" +
                    "Response: Animated GIF image/gif\n\n" +
                    "POST /api/queue - Add effect to queue\n" +
                    "Request body (JSON):\n" +
                    "  {\n" +
                    "    \"effect\": \"effect_name\",   (required, e.g., \"rain\", \"meteor_shower\")\n" +
                    "    \"duration\": 30,            (optional, seconds to play)\n" +
                    "    \"repeat\": 1                (optional, number of times to repeat)\n" +
                    "  }\n\n" +
                    "Response: JSON with success status and queue size\n\n" +
                    "GET /api/queue - Get current queue state\n" +
                    "Response: JSON with mode, current effect, queue contents, and remaining time\n\n" +
                    "DELETE /api/queue - Clear the queue\n" +
                    "Response: JSON with success confirmation\n\n" +
                    "GET /health - Health check\n";
                
                byte[] responseBytes = response.getBytes("UTF-8");
                
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            } catch (Exception e) {
                logger.error("Error handling root request: {}", e.getMessage());
            }
        });
    }
    
    /**
     * Start the HTTP server
     */
    public void start() {
        httpServer.setExecutor(null); // Use default executor
        httpServer.start();
        logger.info("Preview HTTP server started on port {}", port);
    }
    
    /**
     * Stop the HTTP server
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            logger.info("Preview HTTP server stopped");
        }
    }
    
    public int getPort() {
        return port;
    }
    
    public HttpServer getServer() {
        return httpServer;
    }
}
