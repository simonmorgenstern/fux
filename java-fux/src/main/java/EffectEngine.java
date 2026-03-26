import com.github.mbelling.ws281x.Ws281xLedStrip;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.io.FileReader;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Random;

public class EffectEngine implements Runnable {
    private Effect currentEffect;
    private boolean running;
    private Thread renderThread;
    private Ws281xLedStrip mainStrip;
    private Ws281xLedStrip sideStrip;
    private PixelCoordinates coordinates;
    
    // Queue management
    private final LinkedBlockingQueue<String> effectQueue = new LinkedBlockingQueue<>();
    private final int queueCapacity = 50;
    private ControlMode mode = ControlMode.RANDOM;
    private long effectStartTime = 0;
    private final int minDisplaySeconds = 2;
    private final Random random = new Random();
    
    // Available effects
    private final List<String> availableEffects = new ArrayList<String>() {{
        add("radial_wave");
        add("rainbow_pulse");
        add("sparkle");
        add("fire");
        add("breathing");
        add("snake");
        add("meteor_shower");
        add("firework");
        add("rain");
        add("aurora");
        add("bilateral_fill");
        add("motion_blur");
        add("gradient");
        add("box_mirror");
        add("eye_blink");
        add("diamond_pulse");
        add("box_wave");
        add("outside_spin");
    }};
    
    // Callback for state updates
    private StateUpdateCallback stateUpdateCallback = null;
    
    public interface StateUpdateCallback {
        void broadcastState(StateMessage state);
        void broadcastError(String message);
    }
    
    public EffectEngine(Ws281xLedStrip mainStrip, Ws281xLedStrip sideStrip) {
        this.mainStrip = mainStrip;
        this.sideStrip = sideStrip;
        this.running = false;
        
        // Load pixel coordinates
        String coordsPath = "/home/pi/fux/assets/pixelCoordinates.json";
        try {
            this.coordinates = new PixelCoordinates(coordsPath);
        } catch (Exception e) {
            System.err.println("Failed to load coordinates from " + coordsPath + ", trying current directory...");
            try {
                this.coordinates = new PixelCoordinates("pixelCoordinates.json");
            } catch (Exception e2) {
                System.err.println("Failed to load coordinates: " + e2.getMessage());
                this.coordinates = null;
            }
        }
    }
    
    public void setStateUpdateCallback(StateUpdateCallback callback) {
        this.stateUpdateCallback = callback;
    }
    
    public void setMode(ControlMode mode) {
        this.mode = mode;
        System.out.println("Mode changed to " + mode);
        broadcastState();
    }
    
    public void addToQueue(String effectName) {
        if (!availableEffects.contains(effectName)) {
            broadcastError("Unknown effect: " + effectName);
            return;
        }
        if (effectQueue.size() >= queueCapacity) {
            broadcastError("Queue is full (capacity: " + queueCapacity + ")");
            return;
        }
        effectQueue.offer(effectName);
        System.out.println("Added effect '" + effectName + "' to queue (size: " + effectQueue.size() + ")");
        broadcastState();
    }
    
    public void clearQueue() {
        effectQueue.clear();
        System.out.println("Queue cleared");
        broadcastState();
    }
    
    public String getCurrentEffect() {
        String name = currentEffect != null ? currentEffect.getName() : null;
        return name;
    }
    
    public ControlMode getMode() {
        return mode;
    }
    
    public List<String> getQueueSnapshot() {
        return new ArrayList<>(effectQueue);
    }
    
    public int getQueueCapacity() {
        return queueCapacity;
    }
    
    public Integer getRemainingSeconds() {
        if (currentEffect == null || effectStartTime == 0) {
            return null;
        }
        long elapsedSeconds = (System.currentTimeMillis() - effectStartTime) / 1000;
        int remaining = minDisplaySeconds - (int) elapsedSeconds;
        return remaining > 0 ? remaining : 0;
    }
    
    private void loadEffect(String effectName) {
        if (coordinates == null) {
            System.err.println("Cannot load effect: coordinates not loaded");
            return;
        }
        
        try {
            // Load effect definition
            String effectPath = "/home/pi/fux-effects/" + effectName + ".json";
            Gson gson = new Gson();
            JsonObject effectDef = gson.fromJson(new FileReader(effectPath), JsonObject.class);
            
            String algorithm = effectDef.get("algorithm").getAsString();
            JsonObject params = effectDef.getAsJsonObject("parameters");
            
            // Create effect instance
            switch(algorithm) {
                case "radial_wave":
                    currentEffect = new RadialWaveEffect();
                    break;
                case "rainbow_pulse":
                    currentEffect = new RainbowPulseEffect();
                    break;
                case "sparkle":
                    currentEffect = new SparkleEffect();
                    break;
                case "fire":
                    currentEffect = new FireEffect();
                    break;
                case "breathing":
                    currentEffect = new BreathingEffect();
                    break;
                case "snake":
                    currentEffect = new SnakeEffect();
                    break;
                case "meteor_shower":
                    currentEffect = new MeteorShowerEffect();
                    break;
                case "firework":
                    currentEffect = new FireworkEffect();
                    break;
                case "rain":
                    currentEffect = new RainEffect();
                    break;
                case "aurora":
                    currentEffect = new AuroraEffect();
                    break;
                case "bilateral_fill":
                    currentEffect = new BilateralFillEffect();
                    break;
                case "motion_blur":
                    currentEffect = new MotionBlurEffect();
                    break;
                case "gradient":
                    currentEffect = new GradientEffect();
                    break;
                case "box_mirror":
                    currentEffect = new BoxMirrorEffect();
                    break;
                case "eye_blink":
                    currentEffect = new EyeBlinkEffect();
                    break;
                case "diamond_pulse":
                    currentEffect = new DiamondPulseEffect();
                    break;
                case "box_wave":
                    currentEffect = new BoxWaveEffect();
                    break;
                case "outside_spin":
                    currentEffect = new OutsideSpinEffect();
                    break;
                default:
                    System.err.println("Unknown algorithm: " + algorithm);
                    return;
            }
            
            // Initialize effect
            currentEffect.initialize(params, coordinates);
            effectStartTime = System.currentTimeMillis();
            
            System.out.println("Effect loaded: " + currentEffect.getName());
        } catch (Exception e) {
            System.err.println("Error loading effect: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void start() {
        if (running) {
            return;
        }
        
        running = true;
        renderThread = new Thread(this);
        renderThread.start();
    }
    
    public void stop() {
        running = false;
        if (renderThread != null) {
            try {
                renderThread.join(1000);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        
        // Clear LEDs
        clearAllLEDs();
    }
    
    private void clearAllLEDs() {
        if (mainStrip != null) {
            for (int i = 0; i < 268; i++) {
                mainStrip.setPixel(i, 0, 0, 0);
            }
            mainStrip.render();
        }
        if (sideStrip != null) {
            for (int i = 0; i < 120; i++) {
                sideStrip.setPixel(i, 0, 0, 0);
            }
            sideStrip.render();
        }
    }
    
    @Override
    public void run() {
        System.out.println("EffectEngine thread started");
        
        while (running) {
            try {
                // If we have a current effect, ensure min display time before switching
                if (currentEffect != null) {
                    long elapsedSeconds = (System.currentTimeMillis() - effectStartTime) / 1000;
                    if (elapsedSeconds < minDisplaySeconds) {
                        // Render current effect
                        renderFrame();
                        Thread.sleep(100);
                        continue;
                    }
                }
                
                // Determine next effect
                String nextEffect = null;
                if (mode == ControlMode.QUEUE) {
                    // Block until an effect is available (with timeout to check running)
                    nextEffect = effectQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                } else { // RANDOM mode
                    // Pick a random effect
                    nextEffect = availableEffects.get(random.nextInt(availableEffects.size()));
                }
                
                if (nextEffect != null) {
                    // Load the effect
                    loadEffect(nextEffect);
                    broadcastState();
                }
                
                // Render current effect
                renderFrame();
                
                // Small sleep to avoid busy loop
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in EffectEngine loop: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("EffectEngine thread terminated");
        clearAllLEDs();
    }
    
    private void renderFrame() {
        if (currentEffect == null) {
            return;
        }
        
        long frameNumber = 0;
        int fps = currentEffect.getFPS();
        long targetFrameTimeMs = 1000 / fps;
        long startTime = System.currentTimeMillis();
        
        // Render frame
        double timeSeconds = frameNumber / (double) fps;
        Map<Integer, Color> pixels = currentEffect.renderFrame(frameNumber, timeSeconds);
        
        // Render to hardware (only if LED strips are available)
        if (mainStrip != null && sideStrip != null) {
            // Clear all LEDs first
            for (int i = 0; i < 268; i++) {
                mainStrip.setPixel(i, 0, 0, 0);
            }
            for (int i = 0; i < 120; i++) {
                sideStrip.setPixel(i, 0, 0, 0);
            }
            
            // Apply pixels
            for (Map.Entry<Integer, Color> entry : pixels.entrySet()) {
                int index = entry.getKey();
                Color color = entry.getValue();
                
                if (index < 268) {
                    mainStrip.setPixel(index, color.getRed(), color.getGreen(), color.getBlue());
                } else if (index < 388) {
                    sideStrip.setPixel(index - 268, color.getRed(), color.getGreen(), color.getBlue());
                }
            }
            
            // Render to hardware
            mainStrip.render();
            sideStrip.render();
        }
        
        // Sleep to maintain FPS
        long elapsed = System.currentTimeMillis() - startTime;
        long sleepTime = targetFrameTimeMs - elapsed;
        
        if (sleepTime > 0) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }
    
    private void broadcastState() {
        if (stateUpdateCallback != null) {
            StateMessage state = new StateMessage(
                mode.toString(),
                getCurrentEffect(),
                getQueueSnapshot(),
                queueCapacity,
                System.currentTimeMillis(),
                getRemainingSeconds()
            );
            stateUpdateCallback.broadcastState(state);
        }
    }
    
    private void broadcastError(String message) {
        if (stateUpdateCallback != null) {
            stateUpdateCallback.broadcastError(message);
        }
    }
}
