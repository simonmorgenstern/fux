package server;

import com.github.mbelling.ws281x.LedStripType;
import com.github.mbelling.ws281x.Ws281xLedStrip;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import controllers.EffectEngine;
import models.ControlMode;
import models.StateMessage;
import renderer.EffectRenderer;
import renderer.ParsedFrame;
import renderer.FrameParser;
import renderer.MusicMode;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class WebSocket extends WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(WebSocket.class);
    
    private ArrayList<ParsedFrame> parsedFrames;
    private Gson gson;
    private FrameParser frameParser;
    
    // LED strips
    private static Ws281xLedStrip fuxStrip = new Ws281xLedStrip(268, 18, 800000, 10, 100, 0, false, LedStripType.WS2811_STRIP_GRB, true);
    private static Ws281xLedStrip sideStrip = new Ws281xLedStrip(120, 13, 800000, 10, 200, 1, false, LedStripType.WS2811_STRIP_GRB, true);
    
    // Effect system
    private static EffectRenderer renderer = new EffectRenderer(fuxStrip, sideStrip);
    private static EffectEngine effectEngine = new EffectEngine(renderer);
    
    // Music mode (legacy)
    private static MusicMode musicModeRunner = new MusicMode(fuxStrip);
    private static Thread musicModeThread;

    public WebSocket(int port) throws UnknownHostException {
        super(new InetSocketAddress(port));
        this.gson = new Gson();
        this.frameParser = new FrameParser();
        this.parsedFrames = new ArrayList<>();
        
        // Set up state change callbacks for effect engine
        effectEngine.setStateUpdateCallback(new EffectEngine.StateUpdateCallback() {
            @Override
            public void broadcastState(StateMessage state) {
                String json = gson.toJson(state);
                broadcast(json);
                logger.info("Broadcasted state: mode={}, current={}, queue={}", 
                    state.getMode(), state.getCurrentEffect(), state.getQueue().size());
            }
            
            @Override
            public void broadcastError(String message) {
                sendErrorToAll(message);
            }
        });
        
        // Start effect engine
        effectEngine.start();
        
        logger.info("WebSocket server initialized on port {}", port);
    }

    @Override
    public void onOpen(org.java_websocket.WebSocket webSocket, ClientHandshake clientHandshake) {
        logger.info("Client connected: {}", webSocket.getRemoteSocketAddress().getAddress().getHostAddress());
        
        // Send welcome message
        webSocket.send("Welcome to fux LED control server!");
        
        // Send current state to new client
        StateMessage state = new StateMessage(
            effectEngine.getMode().toString(),
            effectEngine.getCurrentEffect(),
            effectEngine.getQueueSnapshot(),
            effectEngine.getQueueCapacity(),
            System.currentTimeMillis(),
            effectEngine.getRemainingSeconds()
        );
        webSocket.send(gson.toJson(state));
    }

    @Override
    public void onClose(org.java_websocket.WebSocket webSocket, int code, String reason, boolean remote) {
        logger.info("Client disconnected: {}", webSocket.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(org.java_websocket.WebSocket webSocket, String message) {
        logger.debug("Received: {}", message);
        
        try {
            // Mode control commands
            if (message.equals("MODE:RANDOM")) {
                effectEngine.setMode(ControlMode.RANDOM);
                logger.info("Mode set to RANDOM");
                return;
            }
            
            if (message.equals("MODE:QUEUE")) {
                effectEngine.setMode(ControlMode.QUEUE);
                logger.info("Mode set to QUEUE");
                return;
            }
            
            // Queue operations
            if (message.startsWith("ADD_QUEUE:")) {
                String effectName = message.substring(10).trim();
                effectEngine.addToQueue(effectName);
                return;
            }
            
            if (message.equals("CLEAR_QUEUE")) {
                effectEngine.clearQueue();
                logger.info("Queue cleared");
                return;
            }
            
            // State query
            if (message.equals("GET_STATE")) {
                StateMessage state = new StateMessage(
                    effectEngine.getMode().toString(),
                    effectEngine.getCurrentEffect(),
                    effectEngine.getQueueSnapshot(),
                    effectEngine.getQueueCapacity(),
                    System.currentTimeMillis(),
                    effectEngine.getRemainingSeconds()
                );
                webSocket.send(gson.toJson(state));
                return;
            }
            
            // Legacy effect control (immediate playback)
            if (message.startsWith("EFFECT:")) {
                String effectName = message.substring(7).trim();
                logger.info("Loading effect: {}", effectName);
                renderer.loadEffect(effectName);
                return;
            }
            
            if (message.equals("STOP_EFFECT")) {
                logger.info("Stopping effect");
                renderer.stop();
                return;
            }
            
            // Music mode
            if (message.matches("musicModeOn:\\d+")) {
                if (musicModeThread != null && musicModeThread.isAlive()) {
                    logger.warn("Music mode already running");
                    sendError(webSocket, "Music mode already running");
                    return;
                }
                
                logger.info("Starting music mode");
                musicModeRunner.stopped = false;
                musicModeThread = new Thread(musicModeRunner);
                musicModeThread.start();
                return;
            }
            
            if (message.equals("musicModeOff")) {
                stopMusicMode();
                return;
            }
            
            // Shutdown command
            if (message.equals("STOP") || message.equals("SHUTDOWN")) {
                logger.warn("Shutdown command received");
                webSocket.send("Shutting down server...");
                
                // Clean shutdown sequence
                new Thread(() -> {
                    try {
                        stopMusicMode();
                        effectEngine.stop();
                        
                        // Clear all LEDs
                        for (int i = 0; i < 268; i++) {
                            fuxStrip.setPixel(i, 0, 0, 0);
                            if (i < 120) {
                                sideStrip.setPixel(i, 0, 0, 0);
                            }
                        }
                        fuxStrip.render();
                        sideStrip.render();
                        
                        Thread.sleep(500);
                        
                        logger.info("Server shutting down");
                        this.stop(1000);
                        System.exit(0);
                    } catch (Exception e) {
                        logger.error("Error during shutdown", e);
                        System.exit(1);
                    }
                }).start();
                return;
            }
            
            // Legacy frame-based animation
            if (message.matches("start:\\d+")) {
                for (ParsedFrame frame: this.parsedFrames) {
                    int[][] changes = frame.getParsedChanges();
                    for(int index = 0; index < changes.length; index ++) {
                       fuxStrip.setPixel(changes[index][0], changes[index][1], changes[index][2], changes[index][3]);
                    }
                    try {
                        Thread.sleep(frame.getDuration());
                    } catch (InterruptedException e) {
                        logger.error("Interrupted during frame playback", e);
                    }
                    fuxStrip.render();
                }
                this.parsedFrames.clear();
                return;
            }
            
            // Parse frame data (legacy)
            ParsedFrame parsedFrame = frameParser.getParsedFrame(message);
            this.parsedFrames.add(parsedFrame);
            
        } catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage(), e);
            sendError(webSocket, "Error processing command: " + e.getMessage());
        }
    }
    
    /**
     * Send error message to specific client
     */
    private void sendError(org.java_websocket.WebSocket webSocket, String errorMessage) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "ERROR");
        json.addProperty("message", errorMessage);
        json.addProperty("timestamp", System.currentTimeMillis());
        
        String message = gson.toJson(json);
        webSocket.send(message);
        logger.warn("Sent error to client: {}", errorMessage);
    }
    
    /**
     * Send error message to all connected clients
     */
    private void sendErrorToAll(String errorMessage) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "ERROR");
        json.addProperty("message", errorMessage);
        json.addProperty("timestamp", System.currentTimeMillis());
        
        String message = gson.toJson(json);
        this.broadcast(message);
        logger.warn("Broadcasted error: {}", errorMessage);
    }

    private void stopMusicMode() {
        if (musicModeThread == null || !musicModeThread.isAlive()) {
            return;
        }
        
        logger.info("Stopping music mode");
        musicModeRunner.stopped = true;
        musicModeThread.interrupt();
        
        // Clear LEDs
        for (int i = 0; i < 268; i++) {
            fuxStrip.setPixel(i, 0, 0, 0);
            if (i < 120) {
                sideStrip.setPixel(i, 0, 0, 0);
            }
        }
        fuxStrip.render();
        sideStrip.render();
    }

    @Override
    public void onError(org.java_websocket.WebSocket webSocket, Exception error) {
        logger.error("WebSocket error", error);
        
        if (webSocket != null) {
            try {
                sendError(webSocket, "Server error: " + error.getMessage());
            } catch (Exception e) {
                // Ignore if we can't send error
            }
        }
    }

    @Override
    public void onStart() {
        logger.info("WebSocket server started!");
        setConnectionLostTimeout(10);
        logger.info("Effect Engine running in {} mode", effectEngine.getMode());
    }
}
