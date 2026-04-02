
import com.diozero.ws281xj.rpiws281x.WS281x;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.io.FileReader;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EffectRenderer handles the actual LED rendering for effects.
 * Bridges the Effect system with hardware LED strips.
 */
public class EffectRenderer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(EffectRenderer.class);
    
    private static final int MAIN_LED_COUNT = 268;
    private static final String[] EFFECTS_PATHS = {
        "/home/pi/fux-effects/",  // Production path
        "/home/simon/.openclaw/workspace/fux/effects/",  // Development path
        "effects/",  // Relative path
        "../effects/"  // Parent relative path
    };
    private static final String COORDS_PATH_PRIMARY = "/home/pi/fux/assets/pixelCoordinates.json";
    private static final String COORDS_PATH_FALLBACK = "pixelCoordinates.json";
    
    private final WS281x mainStrip;
    private final PixelCoordinates coordinates;
    
    private volatile Effect currentEffect = null;
    private volatile boolean running = false;
    private Thread renderThread;
    
    public EffectRenderer(WS281x mainStrip) {
        this.mainStrip = mainStrip;
        
        // Load pixel coordinates with fallback
        PixelCoordinates coords = null;
        try {
            coords = new PixelCoordinates(COORDS_PATH_PRIMARY);
            logger.info("Loaded pixel coordinates from: {}", COORDS_PATH_PRIMARY);
        } catch (Exception e) {
            logger.warn("Failed to load coordinates from {}, trying fallback", COORDS_PATH_PRIMARY);
            try {
                coords = new PixelCoordinates(COORDS_PATH_FALLBACK);
                logger.info("Loaded pixel coordinates from: {}", COORDS_PATH_FALLBACK);
            } catch (Exception e2) {
                logger.error("Failed to load coordinates: {}", e2.getMessage());
            }
        }
        this.coordinates = coords;
    }
    
    /**
     * Load and start rendering a new effect
     */
    public synchronized void loadEffect(String effectName) {
        if (coordinates == null) {
            logger.error("Cannot load effect: coordinates not loaded");
            return;
        }
        
        // Stop current effect
        stopCurrentEffect();
        
        // Create new effect
        Effect newEffect = createEffect(effectName);
        if (newEffect != null) {
            currentEffect = newEffect;
            
            // Start rendering thread if not running
            if (!running) {
                running = true;
                renderThread = new Thread(this, "EffectRenderer");
                renderThread.start();
            }
            
            logger.info("Effect loaded: {}", effectName);
        }
    }
    
    /**
     * Stop the current effect and renderer
     */
    public synchronized void stop() {
        running = false;
        
        if (renderThread != null) {
            renderThread.interrupt();
            try {
                renderThread.join(2000);
                if (renderThread.isAlive()) {
                    logger.warn("Render thread did not stop gracefully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            renderThread = null;
        }
        
        stopCurrentEffect();
        clearAllLEDs();
        logger.info("EffectRenderer stopped");
    }
    
    /**
     * Stop and cleanup current effect
     */
    private void stopCurrentEffect() {
        if (currentEffect != null) {
            try {
                currentEffect.dispose();
            } catch (Exception e) {
                logger.error("Error disposing effect: {}", e.getMessage());
            }
            currentEffect = null;
        }
    }
    
    /**
     * Create effect instance from name
     */
    private Effect createEffect(String effectName) {
        Gson gson = new Gson();
        JsonObject effectDef = null;
        String loadedFromPath = null;
        
        // Try multiple paths
        for (String basePath : EFFECTS_PATHS) {
            try {
                String effectPath = basePath + effectName + ".json";
                effectDef = gson.fromJson(new FileReader(effectPath), JsonObject.class);
                loadedFromPath = effectPath;
                break;
            } catch (Exception e) {
                // Try next path
            }
        }
        
        if (effectDef == null) {
            logger.error("Could not find effect definition for: {}", effectName);
            return null;
        }
        
        try {
            String algorithm = effectDef.get("algorithm").getAsString();
            JsonObject params = effectDef.getAsJsonObject("parameters");
            
            Effect effect = instantiateEffect(algorithm);
            if (effect != null) {
                effect.initialize(params, coordinates);
                logger.info("Effect loaded from: {}", loadedFromPath);
                return effect;
            } else {
                logger.error("Unknown algorithm: {}", algorithm);
            }
        } catch (Exception e) {
            logger.error("Error initializing effect '{}': {}", effectName, e.getMessage());
        }
        return null;
    }
    
    /**
     * Instantiate effect by algorithm name
     */
    private Effect instantiateEffect(String algorithm) {
        switch (algorithm) {
            case "radial_wave": return new RadialWaveEffect();
            case "rainbow_pulse": return new RainbowPulseEffect();
            case "sparkle": return new SparkleEffect();
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
            // New effects
            case "comet": return new CometEffect();
            case "scanner": return new ScannerEffect();
            case "twinkle": return new TwinkleEffect();
            case "theater_chase": return new TheaterChaseEffect();
            case "box_cascade": return new BoxCascadeEffect();
            case "heartbeat": return new HeartbeatEffect();
            case "lava_lamp": return new LavaLampEffect();
            case "contour_trace": return new ContourTraceEffect();
            default: return null;
        }
    }
    
    /**
     * Clear all LEDs
     */
    private void clearAllLEDs() {
        try {
            for (int i = 0; i < MAIN_LED_COUNT; i++) {
                mainStrip.setPixelColourRGB(i, 0, 0, 0);
            }
            mainStrip.render();
        } catch (Exception e) {
            logger.error("Error clearing LEDs: {}", e.getMessage());
        }
    }
    
    /**
     * Main rendering loop
     */
    @Override
    public void run() {
        logger.info("EffectRenderer thread started");
        
        long frameNumber = 0;
        
        while (running) {
            try {
                Effect effect = currentEffect;
                
                if (effect != null) {
                    renderFrame(effect, frameNumber);
                    frameNumber++;
                } else {
                    // No effect, wait
                    Thread.sleep(100);
                    frameNumber = 0;
                }
                
            } catch (InterruptedException e) {
                logger.info("EffectRenderer interrupted");
                break;
            } catch (Exception e) {
                logger.error("Error in rendering loop: {}", e.getMessage(), e);
                // Continue despite errors
            }
        }
        
        logger.info("EffectRenderer thread stopped");
        clearAllLEDs();
    }
    
    /**
     * Render a single frame
     */
    private void renderFrame(Effect effect, long frameNumber) throws InterruptedException {
        int fps = effect.getFPS();
        double timeSeconds = frameNumber / (double) fps;
        long targetFrameTimeMs = 1000 / fps;
        
        long startTime = System.currentTimeMillis();
        
        // Render frame
        Map<Integer, Color> pixels = effect.renderFrame(frameNumber, timeSeconds);
        
        // Clear all LEDs first
        for (int i = 0; i < MAIN_LED_COUNT; i++) {
            mainStrip.setPixelColourRGB(i, 0, 0, 0);
        }
        
        // Apply pixels (only indices 0-267 for fuxStrip)
        for (Map.Entry<Integer, Color> entry : pixels.entrySet()) {
            int index = entry.getKey();
            Color color = entry.getValue();
            
            if (index < MAIN_LED_COUNT) {
                mainStrip.setPixelColourRGB(index, color.getRed(), color.getGreen(), color.getBlue());
            }
            // Ignore indices >= 268 (old sideStrip range)
        }
        
        // Render to hardware
        mainStrip.render();
        
        // Sleep to maintain FPS
        long elapsed = System.currentTimeMillis() - startTime;
        long sleepTime = targetFrameTimeMs - elapsed;
        
        if (sleepTime > 0) {
            Thread.sleep(sleepTime);
        }
        
        // Log every 300 frames (reduce logging frequency)
        if (frameNumber % 300 == 0) {
            logger.debug("Frame {}, render: {}ms, LEDs: {}", frameNumber, elapsed, pixels.size());
        }
    }
}
