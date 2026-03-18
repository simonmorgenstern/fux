package controllers;

import models.ControlMode;
import models.StateMessage;
import renderer.EffectRenderer;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EffectEngine implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(EffectEngine.class);
    
    private final EffectRenderer renderer;
    private final LinkedBlockingQueue<String> effectQueue = new LinkedBlockingQueue<>();
    private final int queueCapacity = 50;
    private ControlMode mode = ControlMode.RANDOM;
    private String currentEffect = null;
    private long effectStartTime = 0;
    private final int minDisplaySeconds = 2;
    
    private volatile boolean running = false;
    private Thread engineThread;
    private final Random random = new Random();
    
    // List of available effect names (hardcoded for now; could be loaded from filesystem)
    private final List<String> availableEffects = List.of(
        "radial_wave", "rainbow_pulse", "sparkle", "fire", "breathing", "snake",
        "meteor_shower", "firework", "rain", "aurora", "bilateral_fill",
        "motion_blur", "gradient", "box_mirror", "eye_blink", "diamond_pulse",
        "box_wave", "outside_spin"
    );
    
    // Callback for broadcasting state updates (will be set by WebSocket)
    private StateUpdateCallback stateUpdateCallback = null;
    
    public interface StateUpdateCallback {
        void broadcastState(StateMessage state);
        void broadcastError(String message);
    }
    
    public EffectEngine(EffectRenderer renderer) {
        this.renderer = renderer;
    }
    
    public void setStateUpdateCallback(StateUpdateCallback callback) {
        this.stateUpdateCallback = callback;
    }
    
    public void start() {
        if (running) {
            return;
        }
        running = true;
        engineThread = new Thread(this);
        engineThread.start();
        logger.info("EffectEngine started");
    }
    
    public void stop() {
        running = false;
        if (engineThread != null) {
            engineThread.interrupt();
            try {
                engineThread.join(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        renderer.stop();
        logger.info("EffectEngine stopped");
    }
    
    public void setMode(ControlMode mode) {
        this.mode = mode;
        logger.info("Mode changed to {}", mode);
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
        logger.info("Added effect '{}' to queue (size: {})", effectName, effectQueue.size());
        broadcastState();
    }
    
    public void clearQueue() {
        effectQueue.clear();
        logger.info("Queue cleared");
        broadcastState();
    }
    
    public String getCurrentEffect() {
        return currentEffect;
    }
    
    public ControlMode getMode() {
        return mode;
    }
    
    public List<String> getQueueSnapshot() {
        return List.copyOf(effectQueue);
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
    
    @Override
    public void run() {
        logger.info("EffectEngine thread started");
        while (running) {
            try {
                // Determine next effect
                String nextEffect = null;
                if (mode == ControlMode.QUEUE) {
                    // Block until an effect is available (with timeout to check running)
                    nextEffect = effectQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                } else { // RANDOM mode
                    // If we have a current effect, ensure min display time
                    if (currentEffect != null) {
                        long elapsedSeconds = (System.currentTimeMillis() - effectStartTime) / 1000;
                        if (elapsedSeconds < minDisplaySeconds) {
                            Thread.sleep(100);
                            continue;
                        }
                    }
                    // Pick a random effect (could be same as current, that's okay)
                    nextEffect = availableEffects.get(random.nextInt(availableEffects.size()));
                }
                
                if (nextEffect != null) {
                    // Load the effect
                    renderer.loadEffect(nextEffect);
                    currentEffect = nextEffect;
                    effectStartTime = System.currentTimeMillis();
                    logger.info("Started effect '{}'", nextEffect);
                    broadcastState();
                }
                
                // Small sleep to avoid busy loop
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in EffectEngine loop", e);
            }
        }
        logger.info("EffectEngine thread terminated");
    }
    
    private void broadcastState() {
        if (stateUpdateCallback != null) {
            StateMessage state = new StateMessage(
                mode.toString(),
                currentEffect,
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