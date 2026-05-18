import com.diozero.ws281xj.rpiws281x.WS281x;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.io.FileReader;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Random;

public class EffectEngine implements Runnable {
    private Effect currentEffect;
    private volatile boolean running;
    private volatile boolean closed;
    private Thread renderThread;
    private WS281x mainStrip;
    private final Object renderLock = new Object();
    private PixelCoordinates coordinates;
    
    // Queue management
    private final LinkedBlockingQueue<QueueEntry> effectQueue = new LinkedBlockingQueue<>();
    private final int queueCapacity = 50;
    private ControlMode mode = ControlMode.RANDOM;
    private long effectStartTime = 0;
    private long frameNumber = 0;
    private int currentEffectDuration = 30; // total seconds for current effect
    private int currentEffectDefaultDuration = 30; // duration from JSON
    private final Random random = new Random();
    
    // Available effects
    private final List<String> availableEffects = new ArrayList<String>() {{
        add("radial_wave");
        add("rainbow_pulse");
        add("sparkle");
        add("breathing");
        add("snake");
        add("meteor_shower");
        add("firework");
        add("rain");
        add("aurora");
        add("bilateral_fill");
        add("motion_blur");
        add("diamond_pulse");
        add("outside_spin");
        add("heartbeat");
        add("lava_lamp");
        add("contour_trace");
        add("strobe");
        add("ripple_burst");
        add("drop_pulse");
        add("pulse_network");
        add("forest_fire");
        add("lightning");
        add("firefly_sync");
        add("box_bloom");
        add("box_outline_trace");
        add("box_breathe_grid");
        add("box_ripple");
        add("mirror_wave");
        add("symmetric_sparkle");
        add("mirror_cascade");
        add("mirror_breathe");
        add("mirror_snake");
        add("flood_fill");
        add("center_pulse");
        add("center_heartbeat");
        add("star");
    }};
    
    // Casino mode state
    public enum CasinoState { IDLE, ROLLING, RED, GREEN, BLACK }
    private volatile CasinoState casinoState = CasinoState.IDLE;
    private long casinoRollStartMs = 0;
    private List<Integer> casinoOutsideLEDs;
    private Map<Integer, Double> casinoOutsideAngles;

    // Callback for state updates
    private StateUpdateCallback stateUpdateCallback = null;
    
    public interface StateUpdateCallback {
        void broadcastState(StateMessage state);
        void broadcastError(String message);
    }
    
    public EffectEngine(WS281x mainStrip) {
        this.mainStrip = mainStrip;
        this.running = false;
        
        // Load pixel coordinates with fallback paths
        this.coordinates = PixelCoordinates.loadWithFallback();
    }
    
    public void setStateUpdateCallback(StateUpdateCallback callback) {
        this.stateUpdateCallback = callback;
    }
    
    public void setMode(ControlMode mode) {
        ControlMode previous = this.mode;
        this.mode = mode;
        if (mode == ControlMode.CASINO) {
            initCasinoLEDs();
            casinoState = CasinoState.IDLE;
            currentEffect = null;
            clearAllLEDs();
        } else if (previous == ControlMode.CASINO) {
            casinoState = CasinoState.IDLE;
            clearAllLEDs();
        }
        System.out.println("Mode changed to " + mode);
        broadcastState();
    }

    // MARK: - Casino mode

    /**
     * Start the casino "roll": rainbow circulates around the outside ring.
     * Switches into CASINO mode if not already.
     */
    public void casinoRoll() {
        if (mode != ControlMode.CASINO) {
            this.mode = ControlMode.CASINO;
        }
        initCasinoLEDs();
        currentEffect = null;
        casinoState = CasinoState.ROLLING;
        casinoRollStartMs = System.currentTimeMillis();
        System.out.println("Casino: ROLL");
        broadcastState();
    }

    /**
     * Set the casino roulette result. Red → all LEDs red, Green → all green,
     * Black → all LEDs off. Stops the rolling animation.
     */
    public void casinoSetResult(CasinoState result) {
        if (mode != ControlMode.CASINO) {
            this.mode = ControlMode.CASINO;
        }
        if (result == CasinoState.ROLLING) {
            casinoRoll();
            return;
        }
        casinoState = result;
        System.out.println("Casino: result = " + result);
        broadcastState();
    }

    private void initCasinoLEDs() {
        if (casinoOutsideLEDs != null && !casinoOutsideLEDs.isEmpty()) {
            return;
        }
        Set<Integer> outsideSet = LEDGroupLoader.loadGroup("outside");
        casinoOutsideLEDs = new ArrayList<>(outsideSet);
        casinoOutsideAngles = new HashMap<>();

        if (coordinates == null || casinoOutsideLEDs.isEmpty()) {
            return;
        }

        double cx = 0, cy = 0;
        int count = 0;
        for (int led : casinoOutsideLEDs) {
            if (led < coordinates.getCount()) {
                PixelCoordinate c = coordinates.get(led);
                cx += c.getX();
                cy += c.getY();
                count++;
            }
        }
        if (count > 0) {
            cx /= count;
            cy /= count;
        }

        for (int led : casinoOutsideLEDs) {
            if (led < coordinates.getCount()) {
                PixelCoordinate c = coordinates.get(led);
                double dx = c.getX() - cx;
                double dy = c.getY() - cy;
                double angle = Math.atan2(dy, dx);
                if (angle < 0) angle += 2 * Math.PI;
                casinoOutsideAngles.put(led, angle);
            }
        }
        System.out.println("Casino: outside ring has " + casinoOutsideLEDs.size() + " LEDs");
    }

    private void renderCasinoFrame() {
        synchronized (renderLock) {
            if (closed || mainStrip == null) return;
            try {
                // Clear all first
                for (int i = 0; i < 268; i++) {
                    mainStrip.setPixelColourRGB(i, 0, 0, 0);
                }

                switch (casinoState) {
                    case ROLLING: {
                        if (casinoOutsideLEDs == null || casinoOutsideAngles == null) break;
                        double rotationTime = 1.5; // seconds per full rotation
                        double elapsed = (System.currentTimeMillis() - casinoRollStartMs) / 1000.0;
                        double rotation = (elapsed / rotationTime) * 2 * Math.PI;
                        for (int led : casinoOutsideLEDs) {
                            Double angle = casinoOutsideAngles.get(led);
                            if (angle == null) continue;
                            double rel = (angle - rotation) % (2 * Math.PI);
                            if (rel < 0) rel += 2 * Math.PI;
                            float hue = (float) (rel / (2 * Math.PI));
                            Color c = Color.getHSBColor(hue, 1.0f, 1.0f);
                            mainStrip.setPixelColourRGB(led, c.getRed(), c.getGreen(), c.getBlue());
                        }
                        break;
                    }
                    case RED:
                        for (int i = 0; i < 268; i++) {
                            mainStrip.setPixelColourRGB(i, 255, 0, 0);
                        }
                        break;
                    case GREEN:
                        for (int i = 0; i < 268; i++) {
                            mainStrip.setPixelColourRGB(i, 0, 255, 0);
                        }
                        break;
                    case BLACK:
                    case IDLE:
                    default:
                        // Already cleared above
                        break;
                }
                mainStrip.render();
            } catch (Exception e) {
                // ignore native strip errors
            }
        }
    }
    
    /**
     * Turn off all LEDs (OFF mode)
     */
    public void turnOff() {
        this.mode = ControlMode.OFF;
        this.currentEffect = null;
        effectQueue.clear();
        clearAllLEDs();
        System.out.println("OFF mode activated - all LEDs cleared");
        broadcastState();
    }

    /**
     * Play an effect in IDLE mode (runs indefinitely until replaced)
     */
    public void playIdle(String effectName) {
        if (!availableEffects.contains(effectName)) {
            broadcastError("Unknown effect: " + effectName);
            return;
        }
        
        // Switch to IDLE mode
        this.mode = ControlMode.IDLE;
        
        // Clear queue (idle mode ignores queue)
        effectQueue.clear();
        
        // Load effect with indefinite duration (-1)
        loadEffectIndefinite(effectName);
        
        System.out.println("Idle mode activated with effect: " + effectName);
        broadcastState();
    }
    
    /**
     * Load effect for indefinite duration (idle mode)
     */
    private void loadEffectIndefinite(String effectName) {
        // Use special queue entry with duration -1 (indefinite)
        QueueEntry indefiniteEntry = new QueueEntry(effectName, -1, null);
        loadEffect(effectName, indefiniteEntry);
    }
    
    public void addToQueue(QueueEntry entry) {
        if (!availableEffects.contains(entry.getEffectName())) {
            broadcastError("Unknown effect: " + entry.getEffectName());
            return;
        }
        if (effectQueue.size() >= queueCapacity) {
            broadcastError("Queue is full (capacity: " + queueCapacity + ")");
            return;
        }
        effectQueue.offer(entry);
        System.out.println("Added effect '" + entry + "' to queue (size: " + effectQueue.size() + ")");
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
    
    public List<QueueEntry> getQueueSnapshot() {
        return new ArrayList<>(effectQueue);
    }
    
    public int getQueueCapacity() {
        return queueCapacity;
    }
    
    public Integer getRemainingSeconds() {
        if (currentEffect == null || effectStartTime == 0) {
            return null;
        }
        
        // In IDLE/OFF mode, return null (indefinite)
        if (mode == ControlMode.IDLE || mode == ControlMode.OFF || currentEffectDuration == -1) {
            return null;
        }
        
        long elapsedSeconds = (System.currentTimeMillis() - effectStartTime) / 1000;
        int remaining = currentEffectDuration - (int) elapsedSeconds;
        return remaining > 0 ? remaining : 0;
    }
    
    private void loadEffect(String effectName, QueueEntry queueEntry) {
        if (coordinates == null) {
            System.err.println("Cannot load effect: coordinates not loaded");
            return;
        }
        
        try {
            // Load effect definition with fallbacks
            Gson gson = new Gson();
            JsonObject effectDef = null;
            
            // Try multiple paths: deployed -> current dir -> effects subdir -> classpath
            String[] pathsToTry = {
                "/home/pi/fux-effects/" + effectName + ".json",
                effectName + ".json",
                "effects/" + effectName + ".json",
                "../effects/" + effectName + ".json"
            };
            
            // Try file system paths first
            for (String path : pathsToTry) {
                try {
                    effectDef = gson.fromJson(new FileReader(path), JsonObject.class);
                    System.out.println("Loaded effect from: " + path);
                    break;
                } catch (java.io.FileNotFoundException e) {
                    // Try next path
                }
            }
            
            // Try classpath resources as fallback
            if (effectDef == null) {
                String[] classpathPaths = {"animations/", "effects/"};
                for (String classpathDir : classpathPaths) {
                    try {
                        java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(classpathDir + effectName + ".json");
                        if (is != null) {
                            effectDef = gson.fromJson(new java.io.InputStreamReader(is), JsonObject.class);
                            System.out.println("Loaded effect from classpath: " + classpathDir + effectName + ".json");
                            break;
                        }
                    } catch (Exception e) {
                        // Try next classpath path
                    }
                }
            }
            
            if (effectDef == null) {
                throw new Exception("Could not find effect definition for: " + effectName + " (tried /home/pi/fux-effects/, current dir, effects/ subdir, and classpath)");
            }
            
            String algorithm = effectDef.get("algorithm").getAsString();
            JsonObject params = effectDef.getAsJsonObject("parameters");

            // Read default duration from JSON (fallback 30s)
            currentEffectDefaultDuration = effectDef.has("duration") ? effectDef.get("duration").getAsInt() : 30;
            
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
                case "diamond_pulse":
                    currentEffect = new DiamondPulseEffect();
                    break;
                case "outside_spin":
                    currentEffect = new OutsideSpinEffect();
                    break;
                case "heartbeat":
                    currentEffect = new HeartbeatEffect();
                    break;
                case "lava_lamp":
                    currentEffect = new LavaLampEffect();
                    break;
                case "contour_trace":
                    currentEffect = new ContourTraceEffect();
                    break;
                case "strobe":
                    currentEffect = new StrobeEffect();
                    break;
                case "ripple_burst":
                    currentEffect = new RippleBurstEffect();
                    break;
                case "drop_pulse":
                    currentEffect = new DropPulseEffect();
                    break;
                case "pulse_network":
                    currentEffect = new PulseNetworkEffect();
                    break;
                case "forest_fire":
                    currentEffect = new ForestFireEffect();
                    break;
                case "firefly_sync":
                    currentEffect = new FireflySyncEffect();
                    break;
                case "lightning":
                    currentEffect = new LightningEffect();
                    break;
                case "box_bloom":
                    currentEffect = new BoxBloomEffect();
                    break;
                case "box_outline_trace":
                    currentEffect = new BoxOutlineTraceEffect();
                    break;
                case "box_breathe_grid":
                    currentEffect = new BoxBreatheGridEffect();
                    break;
                case "box_ripple":
                    currentEffect = new BoxRippleEffect();
                    break;
                case "mirror_wave":
                    currentEffect = new MirrorWaveEffect();
                    break;
                case "symmetric_sparkle":
                    currentEffect = new SymmetricSparkleEffect();
                    break;
                case "mirror_cascade":
                    currentEffect = new MirrorCascadeEffect();
                    break;
                case "mirror_breathe":
                    currentEffect = new MirrorBreatheEffect();
                    break;
                case "mirror_snake":
                    currentEffect = new MirrorSnakeEffect();
                    break;
                case "flood_fill":
                    currentEffect = new FloodFillEffect();
                    break;
                case "center_pulse":
                    currentEffect = new CenterPulseEffect();
                    break;
                case "center_heartbeat":
                    currentEffect = new CenterHeartbeatEffect();
                    break;
                case "star":
                    currentEffect = new StarEffect();
                    break;
                default:
                    System.err.println("Unknown algorithm: " + algorithm);
                    return;
            }
            
            // Initialize effect
            currentEffect.initialize(params, coordinates);
            effectStartTime = System.currentTimeMillis();
            frameNumber = 0;

            // Compute total display time
            if (queueEntry != null) {
                currentEffectDuration = queueEntry.getTotalDuration(currentEffectDefaultDuration);
            } else {
                currentEffectDuration = currentEffectDefaultDuration;
            }

            System.out.println("Effect loaded: " + currentEffect.getName() + " (duration: " + currentEffectDuration + "s)");
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
            renderThread.interrupt();
            try {
                renderThread.join(3000);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        System.out.println("EffectEngine thread terminated");

        clearAllLEDs();
    }

    /**
     * Close the native strip. Waits for any in-progress render to finish,
     * then prevents further renders and calls ws2811_fini (which internally
     * clears all LEDs before freeing native resources).
     * Also deregisters from diozero's device factory, preventing its
     * shutdown hook from racing with ours.
     */
    public void closeStrip() {
        synchronized (renderLock) {
            if (closed) return;
            closed = true;
            if (mainStrip != null) {
                try {
                    mainStrip.close();
                } catch (Exception e) {
                    // Ignore - best effort cleanup
                }
                mainStrip = null;
            }
        }
    }

    private void clearAllLEDs() {
        synchronized (renderLock) {
            if (closed || mainStrip == null) return;
            try {
                for (int i = 0; i < 268; i++) {
                    mainStrip.setPixelColourRGB(i, 0, 0, 0);
                }
                mainStrip.render();
            } catch (Exception e) {
                // Native strip may already be torn down, ignore
            }
        }
    }
    
    @Override
    public void run() {
        System.out.println("EffectEngine thread started");
        
        while (running) {
            try {
                // Casino mode is handled separately - it bypasses the effect system.
                if (mode == ControlMode.CASINO) {
                    renderCasinoFrame();
                    Thread.sleep(casinoState == CasinoState.ROLLING ? 33 : 100);
                    continue;
                }

                // If we have a current effect, ensure min display time before switching
                if (currentEffect != null) {
                    long elapsedSeconds = (System.currentTimeMillis() - effectStartTime) / 1000;
                    
                    // In IDLE mode, play effect indefinitely (duration == -1)
                    if (mode == ControlMode.IDLE) {
                        renderFrame();
                        continue;
                    }

                    // In OFF mode, skip rendering
                    if (mode == ControlMode.OFF) {
                        Thread.sleep(100);
                        continue;
                    }
                    
                    // In other modes, check duration
                    if (elapsedSeconds < currentEffectDuration) {
                        // Render current effect at proper FPS (sleep is handled inside renderFrame)
                        renderFrame();
                        continue;
                    }
                }

                // Determine next effect
                if (mode == ControlMode.QUEUE) {
                    // Block until an effect is available (with timeout to check running)
                    QueueEntry entry = effectQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                    if (entry != null) {
                        loadEffect(entry.getEffectName(), entry);
                        broadcastState();
                    }
                } else if (mode == ControlMode.RANDOM) {
                    // Pick a random effect
                    String nextEffect = availableEffects.get(random.nextInt(availableEffects.size()));
                    loadEffect(nextEffect, null);
                    broadcastState();
                } else if (mode == ControlMode.IDLE) {
                    // In idle mode, wait for new commands (no auto-switching)
                    Thread.sleep(100);
                } else if (mode == ControlMode.OFF) {
                    // In OFF mode, keep LEDs dark and wait for new commands
                    Thread.sleep(100);
                }
                
                // Render current effect (sleep is handled inside renderFrame)
                if (currentEffect != null) {
                    renderFrame();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in EffectEngine loop: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("EffectEngine render thread exited");
    }
    
    private void renderFrame() {
        if (currentEffect == null) {
            return;
        }

        int fps = currentEffect.getFPS();
        // Guard against fps being 0
        if (fps <= 0) {
            fps = 30; // Default fallback FPS
        }
        long targetFrameTimeMs = 1000 / fps;
        long startTime = System.currentTimeMillis();

        double timeSeconds = (System.currentTimeMillis() - effectStartTime) / 1000.0;
        Map<Integer, Color> pixels = currentEffect.renderFrame(frameNumber, timeSeconds);
        frameNumber++;
        
        // Render to hardware (only if LED strip is available)
        synchronized (renderLock) {
            if (closed || mainStrip == null) return;

            // Clear all LEDs first
            for (int i = 0; i < 268; i++) {
                mainStrip.setPixelColourRGB(i, 0, 0, 0);
            }

            // Apply pixels (only indices 0-267 for fuxStrip)
            for (Map.Entry<Integer, Color> entry : pixels.entrySet()) {
                int index = entry.getKey();
                Color color = entry.getValue();

                if (index < 268) {
                    mainStrip.setPixelColourRGB(index, color.getRed(), color.getGreen(), color.getBlue());
                }
            }

            // Render to hardware
            mainStrip.render();
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
