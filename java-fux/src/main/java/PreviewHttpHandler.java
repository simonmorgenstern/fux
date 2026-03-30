import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for the /api/preview endpoint.
 * Generates animated GIF previews of effects.
 */
public class PreviewHttpHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(PreviewHttpHandler.class);
    private static final Gson gson = new Gson();
    private final EffectEngine effectEngine;
    
    public PreviewHttpHandler(EffectEngine effectEngine) {
        this.effectEngine = effectEngine;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            
            if ("POST".equals(method)) {
                handlePostRequest(exchange);
            } else if ("OPTIONS".equals(method)) {
                // Handle CORS preflight
                setCorsHeaders(exchange);
                exchange.sendResponseHeaders(200, -1);
            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Error in PreviewHttpHandler: {}", e.getMessage(), e);
            try {
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            } catch (IOException e2) {
                logger.error("Failed to send error response: {}", e2.getMessage());
            }
        } finally {
            exchange.close();
        }
    }
    
    /**
     * Handle POST request to generate preview GIF
     */
    private void handlePostRequest(HttpExchange exchange) throws IOException {
        // Read request body
        InputStream is = exchange.getRequestBody();
        String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        is.close();
        
        logger.info("Preview request: {}", requestBody);
        
        // Parse JSON
        JsonObject request;
        try {
            request = gson.fromJson(requestBody, JsonObject.class);
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
            return;
        }
        
        // Validate required fields
        if (!request.has("effect")) {
            sendError(exchange, 400, "Missing required field: effect");
            return;
        }
        
        String effectName = request.get("effect").getAsString();
        double duration = request.has("duration") ? request.get("duration").getAsDouble() : 2.0;
        int fps = request.has("fps") ? request.get("fps").getAsInt() : 15;
        int pixelSize = request.has("pixelSize") ? request.get("pixelSize").getAsInt() : 8;
        
        // Validate parameters
        if (fps < 1 || fps > 60) {
            sendError(exchange, 400, "FPS must be between 1 and 60");
            return;
        }
        if (pixelSize < 1 || pixelSize > 32) {
            sendError(exchange, 400, "pixelSize must be between 1 and 32");
            return;
        }
        if (duration < 0.1 || duration > 30) {
            sendError(exchange, 400, "duration must be between 0.1 and 30 seconds");
            return;
        }
        
        logger.info("Generating preview: effect={}, duration={}, fps={}, pixelSize={}", 
            effectName, duration, fps, pixelSize);
        
        try {
            // Generate GIF
            byte[] gifData = generatePreviewGif(effectName, duration, fps, pixelSize);
            
            // Send response
            setCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "image/gif");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.getResponseHeaders().set("Expires", "0");
            
            exchange.sendResponseHeaders(200, gifData.length);
            OutputStream os = exchange.getResponseBody();
            os.write(gifData);
            os.close();
            
            logger.info("Preview GIF sent: {} bytes", gifData.length);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid effect: {}", effectName);
            sendError(exchange, 404, "Effect not found: " + effectName);
        } catch (Exception e) {
            logger.error("Error generating preview: {}", e.getMessage(), e);
            sendError(exchange, 500, "Error generating preview: " + e.getMessage());
        }
    }
    
    /**
     * Generate animated GIF for an effect
     */
    private byte[] generatePreviewGif(String effectName, double duration, int fps, int pixelSize)
            throws Exception {

        // Create renderer
        PreviewRenderer renderer = new PreviewRenderer(pixelSize);

        // Load effect definition from JSON (same lookup as EffectEngine)
        JsonObject effectDef = loadEffectDefinition(effectName);
        if (effectDef == null) {
            throw new IllegalArgumentException("Unknown effect: " + effectName);
        }

        String algorithm = effectDef.get("algorithm").getAsString();
        JsonObject params = effectDef.getAsJsonObject("parameters");

        Effect effect = instantiateEffect(algorithm);
        if (effect == null) {
            throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }

        effect.initialize(params, renderer.getCoordinates());

        // Calculate frame count
        int frameCount = Math.max(1, (int) (duration * fps));

        logger.info("Rendering {} frames for effect: {}", frameCount, effectName);

        // Render frames
        List<BufferedImage> frames = renderer.renderFrames(effect, frameCount, fps);

        // Encode to GIF
        int frameDelayMs = 1000 / fps;
        GifEncoder encoder = new GifEncoder(frameDelayMs, true);

        ByteArrayOutputStream gifOutput = new ByteArrayOutputStream();
        encoder.encode(frames, gifOutput);

        // Cleanup
        effect.dispose();

        return gifOutput.toByteArray();
    }

    /**
     * Load effect definition JSON, trying the effect name directly and also
     * as a normalized algorithm ID (lowercase, spaces to underscores).
     */
    private JsonObject loadEffectDefinition(String effectName) {
        // Try the name as-is first, then normalized
        String[] namesToTry = {
            effectName,
            effectName.toLowerCase().replace(" ", "_")
        };

        Gson localGson = new Gson();

        for (String name : namesToTry) {
            String[] pathsToTry = {
                "/home/pi/fux-effects/" + name + ".json",
                name + ".json",
                "effects/" + name + ".json",
                "../effects/" + name + ".json"
            };

            for (String path : pathsToTry) {
                try {
                    return localGson.fromJson(new java.io.FileReader(path), JsonObject.class);
                } catch (java.io.FileNotFoundException e) {
                    // Try next path
                }
            }

            // Try classpath
            String[] classpathPaths = {"animations/", "effects/"};
            for (String classpathDir : classpathPaths) {
                try {
                    InputStream is = getClass().getClassLoader()
                        .getResourceAsStream(classpathDir + name + ".json");
                    if (is != null) {
                        JsonObject def = localGson.fromJson(
                            new java.io.InputStreamReader(is), JsonObject.class);
                        is.close();
                        return def;
                    }
                } catch (Exception e) {
                    // Try next
                }
            }
        }

        return null;
    }

    /**
     * Instantiate effect by algorithm name
     */
    private Effect instantiateEffect(String algorithm) {
        switch (algorithm.toLowerCase()) {
            case "radial_wave": return new RadialWaveEffect();
            case "rainbow_pulse": return new RainbowPulseEffect();
            case "sparkle": return new SparkleEffect();
            case "fire": return new FireEffect();
            case "breathing": return new BreathingEffect();
            case "snake": return new SnakeEffect();
            case "meteor_shower": return new MeteorShowerEffect();
            case "firework": return new FireworkEffect();
            case "rain": return new RainEffect();
            case "aurora": return new AuroraEffect();
            case "bilateral_fill": return new BilateralFillEffect();
            case "motion_blur": return new MotionBlurEffect();
            case "gradient": return new GradientEffect();
            case "box_mirror": return new BoxMirrorEffect();
            case "eye_blink": return new EyeBlinkEffect();
            case "diamond_pulse": return new DiamondPulseEffect();
            case "box_wave": return new BoxWaveEffect();
            case "outside_spin": return new OutsideSpinEffect();
            default: return null;
        }
    }
    
    /**
     * Send error response
     */
    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        setCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("timestamp", System.currentTimeMillis());
        
        byte[] response = gson.toJson(error).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, response.length);
        exchange.getResponseBody().write(response);
    }
    
    /**
     * Set CORS headers
     */
    private void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }
}
