import com.github.mbelling.ws281x.LedStripType;
import com.github.mbelling.ws281x.Ws281xLedStrip;
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
    private static Ws281xLedStrip fuxStrip = new Ws281xLedStrip(268, 18, 800000, 10, 100, 0, false, LedStripType.WS2811_STRIP_GRB, true);
    private static Ws281xLedStrip sideStrip = new Ws281xLedStrip(120, 13, 800000, 10, 200, 1, false, LedStripType.WS2811_STRIP_GRB, true);
    private static MusicMode musicModeRunner = new MusicMode(fuxStrip);
    private static Thread musicModeThread;
    private static EffectEngine effectEngine = new EffectEngine(fuxStrip, sideStrip);

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
        
        System.out.println("WebSocket server initialized on port " + port);
    }

    @Override
    public void onOpen(org.java_websocket.WebSocket webSocket, ClientHandshake clientHandshake) {
        String clientAddr = webSocket.getRemoteSocketAddress().getAddress().getHostAddress();
        System.out.println("Client connected: " + clientAddr);
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
    public void onClose(org.java_websocket.WebSocket webSocket, int i, String s, boolean b) {
        System.out.println("Client disconnected: " + webSocket);
    }

    @Override
    public void onMessage(org.java_websocket.WebSocket webSocket, String message) {
        System.out.println("Received: " + message);
        
        try {
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
                if (musicModeThread != null && musicModeThread.isAlive()) return; // guard clause

                System.out.println("start music mode");
                musicModeRunner.stopped = false;
                musicModeThread = new Thread(musicModeRunner);
                musicModeThread.start();
                return;
            } 
            else if (message.matches("musicModeOff")) {
                stopMusicMode();
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
                for (ParsedFrame frame: this.parsedFrames) {
                    int[][] changes = frame.getParsedChanges();
                    for(int index = 0; index < changes.length; index ++) {
                       fuxStrip.setPixel(changes[index][0], changes[index][1], changes[index][2], changes[index][3]);
                    }
                    try {
                        Thread.sleep(frame.getDuration());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    fuxStrip.render();
                }
                this.parsedFrames.clear();
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
