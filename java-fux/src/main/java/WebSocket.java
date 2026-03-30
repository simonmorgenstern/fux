import com.diozero.ws281xj.rpiws281x.WS281x;
import com.diozero.ws281xj.PixelColour;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;


public class WebSocket extends WebSocketServer {
    private ArrayList<ParsedFrame> parsedFrames;
    private Gson gson;
    private FrameParser frameParser;
    private static WS281x fuxStrip;
    private static WS281x sideStrip;
    private static MusicMode musicModeRunner;
    private static Thread musicModeThread;
    private static EffectEngine effectEngine;
    private static PreviewHttpServer previewHttpServer;
    private static boolean isHardwareAvailable = false;
    
    static {
        // Only initialize hardware LED strips on Raspberry Pi
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch");
        isHardwareAvailable = osName.contains("linux") && (osArch.startsWith("arm") || osArch.equals("aarch64"));
        System.out.println("Platform detection: os.name=" + osName + ", os.arch=" + osArch + ", hardwareAvailable=" + isHardwareAvailable);
        
        if (isHardwareAvailable) {
            try {
                // WS281x constructor: (gpioPin, brightness, ledCount)
                fuxStrip = new WS281x(18, 100, 268);
                sideStrip = new WS281x(13, 200, 120);
                musicModeRunner = new MusicMode(fuxStrip);
                effectEngine = new EffectEngine(fuxStrip, sideStrip);
                System.out.println("Hardware LED strips initialized (Raspberry Pi detected)");
            } catch (Exception e) {
                System.err.println("Failed to initialize hardware: " + e.getMessage());
                isHardwareAvailable = false;
                fuxStrip = null;
                sideStrip = null;
            }
        } else {
            // Mac/Linux dev mode - no hardware
            System.out.println("Running in preview-only mode (hardware not available on " + osName + ")");
            effectEngine = new EffectEngine(null, null);
        }
    }

    public WebSocket(int port) throws UnknownHostException {
        super(new InetSocketAddress(port));
        this.gson = new Gson();
        this.frameParser = new FrameParser();
        this.parsedFrames = new ArrayList<>();
        
        // Only start effect engine if hardware is available
        if (isHardwareAvailable) {
            // Set up state change callbacks for effect engine
            effectEngine.setStateUpdateCallback(new EffectEngine.StateUpdateCallback() {
                @Override
                public void broadcastState(StateMessage state) {
                    String json = gson.toJson(state);
                    broadcast(json);
                    System.out.println("Broadcasted state: mode=" + state.getMode() + 
                        ", current=" + state.getCurrentEffect() + 
                        ", queue_size=" + state.getQueue().size());
                }
                
                @Override
                public void broadcastError(String message) {
                    sendErrorToAll(message);
                }
            });
            
            // Start effect engine
            effectEngine.start();
            System.out.println("EffectEngine started (hardware mode)");
        } else {
            System.out.println("EffectEngine disabled (preview-only mode)");
        }
        
        // Start preview HTTP server
        try {
            previewHttpServer = new PreviewHttpServer(effectEngine);
            previewHttpServer.start();
        } catch (Exception e) {
            System.err.println("Failed to start preview HTTP server: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("WebSocket server initialized on port " + port);
    }

    @Override
    public void onOpen(org.java_websocket.WebSocket webSocket, ClientHandshake clientHandshake) {
        String clientAddr = webSocket.getRemoteSocketAddress().getAddress().getHostAddress();
        System.out.println("Client connected: " + clientAddr);
        webSocket.send("Welcome to fux LED control server!");
        
        // Send current state to new client (only if hardware mode)
        if (isHardwareAvailable && effectEngine != null) {
            StateMessage state = new StateMessage(
                effectEngine.getMode().toString(),
                effectEngine.getCurrentEffect(),
                effectEngine.getQueueSnapshot(),
                effectEngine.getQueueCapacity(),
                System.currentTimeMillis(),
                effectEngine.getRemainingSeconds()
            );
            webSocket.send(gson.toJson(state));
        } else {
            webSocket.send("{\"mode\":\"preview_only\",\"message\":\"Running in preview-only mode. Use /api/preview endpoint.\"}");
        }
    }

    @Override
    public void onClose(org.java_websocket.WebSocket webSocket, int i, String s, boolean b) {
        System.out.println("Client disconnected: " + webSocket);
    }

    @Override
    public void onMessage(org.java_websocket.WebSocket webSocket, String message) {
        System.out.println("Received: " + message);
        
        try {
            // Check if effect engine commands are available
            if (!isHardwareAvailable && 
                (message.startsWith("MODE:") || message.startsWith("ADD_QUEUE:") || 
                 message.equals("CLEAR_QUEUE") || message.equals("GET_STATE") || 
                 message.equals("STOP_EFFECT"))) {
                webSocket.send("{\"error\":\"Command not available in preview-only mode. Use /api/preview endpoint instead.\"}");
                return;
            }
            
            // Mode control commands
            if (message.equals("MODE:RANDOM")) {
                effectEngine.setMode(ControlMode.RANDOM);
                System.out.println("Mode set to RANDOM");
                return;
            }
            
            if (message.equals("MODE:QUEUE")) {
                effectEngine.setMode(ControlMode.QUEUE);
                System.out.println("Mode set to QUEUE");
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
                System.out.println("Queue cleared");
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
            
            // Stop/shutdown commands
            if (message.equals("STOP_EFFECT")) {
                System.out.println("Stopping effect");
                effectEngine.stop();
                return;
            }

            if (message.matches("musicModeOn:\\d+")) {
                if (!isHardwareAvailable) {
                    webSocket.send("{\"error\":\"Music mode not available in preview-only mode.\"}");
                    return;
                }
                if (musicModeThread != null && musicModeThread.isAlive()) return; // guard clause

                System.out.println("start music mode");
                musicModeRunner.stopped = false;
                musicModeThread = new Thread(musicModeRunner);
                musicModeThread.start();
                return;
            } 
            else if (message.matches("musicModeOff")) {
                if (isHardwareAvailable) {
                    stopMusicMode();
                }
                return;
            } 
            else if (message.equals("STOP") || message.equals("SHUTDOWN")) {
                System.out.println("Shutdown command received - stopping server");
                webSocket.send("Shutting down server...");
                
                // Clean shutdown sequence
                new Thread(() -> {
                    try {
                        // Stop music mode if running
                        stopMusicMode();
                        
                        // Stop effect engine
                        if (effectEngine != null) {
                            effectEngine.stop();
                        }
                        
                        // Stop preview HTTP server
                        if (previewHttpServer != null) {
                            previewHttpServer.stop();
                        }
                        
                        // Clear all LEDs (if hardware available)
                        if (fuxStrip != null && sideStrip != null) {
                            for (int i = 0; i < 268; i++) {
                                fuxStrip.setPixelColour(i, PixelColour.createColourRGB(0, 0, 0));
                                if (i < 120) {
                                    sideStrip.setPixelColour(i, PixelColour.createColourRGB(0, 0, 0));
                                }
                            }
                            fuxStrip.render();
                            sideStrip.render();
                        }
                        
                        Thread.sleep(500); // Give time for response to be sent
                        
                        System.out.println("Server shutting down...");
                        WebSocket.this.stop(1000);
                        System.exit(0);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }).start();
                return;
            }

            // Legacy frame-based animations
            if (message.matches("start:\\d+")) {
                if (fuxStrip != null) {
                    for (ParsedFrame frame: this.parsedFrames) {
                        int[][] changes = frame.getParsedChanges();
                        for(int index = 0; index < changes.length; index ++) {
                           fuxStrip.setPixelColourRGB(changes[index][0], changes[index][1], changes[index][2], changes[index][3]);
                        }
                        try {
                            Thread.sleep(frame.getDuration());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        fuxStrip.render();
                    }
                    this.parsedFrames.clear();
                } else {
                    System.out.println("Hardware not available - skipping frame animation");
                }
                return;
            } 
            
            // Parse frame data
            ParsedFrame parsedFrame = frameParser.getParsedFrame(message);
            this.parsedFrames.add(parsedFrame);
            
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            sendError(webSocket, "Error processing command: " + e.getMessage());
        }
    }
    
    private void sendError(org.java_websocket.WebSocket webSocket, String errorMessage) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "ERROR");
        json.addProperty("message", errorMessage);
        json.addProperty("timestamp", System.currentTimeMillis());
        
        String message = gson.toJson(json);
        webSocket.send(message);
        System.err.println("Sent error to client: " + errorMessage);
    }
    
    private void sendErrorToAll(String errorMessage) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "ERROR");
        json.addProperty("message", errorMessage);
        json.addProperty("timestamp", System.currentTimeMillis());
        
        String message = gson.toJson(json);
        this.broadcast(message);
        System.err.println("Broadcasted error: " + errorMessage);
    }

    private void stopMusicMode() {
        if (musicModeThread == null || !musicModeThread.isAlive()) return; // guard clause
        musicModeRunner.stopped = true;
        musicModeThread.interrupt();
        
        // Clear LEDs if hardware available
        if (fuxStrip != null && sideStrip != null) {
            for (int i = 0; i < 268; i++) {
                fuxStrip.setPixelColourRGB(i, 0, 0, 0);
                if (i < 120) {
                    sideStrip.setPixelColourRGB(i, 0, 0, 0);
                }
            }
            fuxStrip.render();
            sideStrip.render();
        }
    }

    @Override
    public void onError(org.java_websocket.WebSocket webSocket, Exception error) {
        error.printStackTrace();
        if (webSocket != null) {
            // some errors like port binding failed may not be assignable to a specific websocket
        }
    }

    @Override
    public void onStart() {
        System.out.println("Server started!");
        setConnectionLostTimeout(0);
    }
}
