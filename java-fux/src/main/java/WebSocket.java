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
                musicModeRunner = new MusicMode(fuxStrip);
                effectEngine = new EffectEngine(fuxStrip);
                System.out.println("Hardware LED strip initialized (fuxStrip on GPIO 18)");
            } catch (Exception e) {
                System.err.println("Failed to initialize hardware: " + e.getMessage());
                isHardwareAvailable = false;
                fuxStrip = null;
            }
        } else {
            // Mac/Linux dev mode - no hardware
            System.out.println("Running in preview-only mode (hardware not available on " + osName + ")");
            effectEngine = new EffectEngine(null);
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
            webSocket.send(gson.toJson(effectEngine.buildStateMessage()));
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
                 message.startsWith("CASINO:") || message.startsWith("MUSIC_") ||
                 message.equals("CLEAR_QUEUE") || message.equals("GET_STATE") ||
                 message.equals("STOP_EFFECT") || message.equals("MODE:OFF"))) {
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
            
            if (message.equals("MODE:IDLE")) {
                effectEngine.setMode(ControlMode.IDLE);
                System.out.println("Mode set to IDLE");
                return;
            }

            if (message.equals("MODE:OFF")) {
                effectEngine.turnOff();
                System.out.println("Mode set to OFF");
                return;
            }

            if (message.equals("MODE:CASINO")) {
                effectEngine.setMode(ControlMode.CASINO);
                System.out.println("Mode set to CASINO");
                return;
            }

            if (message.equals("MODE:MUSIC")) {
                effectEngine.setMode(ControlMode.MUSIC);
                System.out.println("Mode set to MUSIC");
                return;
            }

            // Beat calibration. BPM gives the period but not the phase, so the
            // grid is nudged by ear and the correction is remembered per track.
            // Format: MUSIC_OFFSET:+150 | MUSIC_OFFSET:-150 | MUSIC_OFFSET:0
            if (message.startsWith("MUSIC_OFFSET:")) {
                String payload = message.substring("MUSIC_OFFSET:".length()).trim();
                try {
                    if (payload.isEmpty() || "0".equals(payload)) {
                        effectEngine.resetMusicOffset();
                    } else if (payload.startsWith("+") || payload.startsWith("-")) {
                        // Signed values are relative nudges
                        long delta = Long.parseLong(payload.startsWith("+") ? payload.substring(1) : payload);
                        effectEngine.nudgeMusicOffset(delta);
                    } else {
                        // Unsigned values set the offset outright
                        effectEngine.nudgeMusicOffset(Long.parseLong(payload)
                            - effectEngine.getMusicSyncService().getBeatClock().getOffsetMs());
                    }
                } catch (NumberFormatException e) {
                    sendError(webSocket, "MUSIC_OFFSET expects milliseconds, e.g. MUSIC_OFFSET:+150");
                }
                return;
            }

            // Manual tempo override for a track with no BPM data.
            // Format: MUSIC_BPM:128
            if (message.startsWith("MUSIC_BPM:")) {
                String payload = message.substring("MUSIC_BPM:".length()).trim();
                try {
                    effectEngine.setMusicBpm(Double.parseDouble(payload));
                } catch (NumberFormatException e) {
                    sendError(webSocket, "MUSIC_BPM expects a number, e.g. MUSIC_BPM:128");
                }
                return;
            }

            // Casino control commands
            if (message.equals("CASINO:ROLL")) {
                effectEngine.casinoRoll();
                return;
            }
            if (message.equals("CASINO:RED")) {
                effectEngine.casinoSetResult(EffectEngine.CasinoState.RED);
                return;
            }
            if (message.equals("CASINO:GREEN")) {
                effectEngine.casinoSetResult(EffectEngine.CasinoState.GREEN);
                return;
            }
            if (message.equals("CASINO:BLACK")) {
                effectEngine.casinoSetResult(EffectEngine.CasinoState.BLACK);
                return;
            }

            // Idle mode: play effect indefinitely
            // Format: IDLE:effect_name
            if (message.startsWith("IDLE:")) {
                String effectName = message.substring(5).trim();
                effectEngine.playIdle(effectName);
                System.out.println("Playing idle effect: " + effectName);
                return;
            }
            
            // Queue operations
            // Format: ADD_QUEUE:effect_name[:duration=X][:repeat=Y]
            if (message.startsWith("ADD_QUEUE:")) {
                String payload = message.substring(10).trim();
                String[] parts = payload.split(":");
                String effectName = parts[0];
                Integer duration = null;
                Integer repeat = null;
                for (int idx = 1; idx < parts.length; idx++) {
                    if (parts[idx].startsWith("duration=")) {
                        try { duration = Integer.parseInt(parts[idx].substring(9)); } catch (NumberFormatException ignored) {}
                    } else if (parts[idx].startsWith("repeat=")) {
                        try { repeat = Integer.parseInt(parts[idx].substring(7)); } catch (NumberFormatException ignored) {}
                    }
                }
                effectEngine.addToQueue(new QueueEntry(effectName, duration, repeat));
                return;
            }
            
            if (message.equals("CLEAR_QUEUE")) {
                effectEngine.clearQueue();
                System.out.println("Queue cleared");
                return;
            }
            
            // State query
            if (message.equals("GET_STATE")) {
                webSocket.send(gson.toJson(effectEngine.buildStateMessage()));
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
                        // Close native strip first (clears LEDs via ws2811_fini)
                        if (effectEngine != null) {
                            effectEngine.closeStrip();
                        }

                        stopMusicMode();

                        if (effectEngine != null) {
                            effectEngine.stop();
                        }

                        if (previewHttpServer != null) {
                            previewHttpServer.stop();
                        }

                        Thread.sleep(500);

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
        if (musicModeThread == null || !musicModeThread.isAlive()) return;
        musicModeRunner.stopped = true;
        musicModeThread.interrupt();
        try {
            musicModeThread.join(2000);
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    /**
     * Clean shutdown: close native strip, stop threads, stop servers.
     * Called from JVM shutdown hook on Ctrl+C.
     *
     * IMPORTANT: closeStrip() must happen FIRST because diozero registers its
     * own shutdown hook that calls ws2811_fini() concurrently. If we spend time
     * joining threads before closing, diozero frees the native memory while our
     * render thread is still calling ws2811_render() → SIGSEGV.
     * closeStrip() acquires renderLock (waiting for any in-progress render),
     * sets closed=true, then calls close() which deregisters from diozero.
     */
    public void shutdown() {
        try {
            // FIRST: close native strip before diozero's concurrent hook can free it
            if (effectEngine != null) {
                effectEngine.closeStrip();
            }
            // Now safely stop threads (render thread will see closed=true and skip renders)
            stopMusicMode();
            if (effectEngine != null) {
                effectEngine.stop();
            }
            if (previewHttpServer != null) {
                previewHttpServer.stop();
            }
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
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
