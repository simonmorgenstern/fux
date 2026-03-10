import com.github.mbelling.ws281x.Ws281xLedStrip;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.io.FileReader;
import java.util.Map;

public class EffectEngine implements Runnable {
    private Effect currentEffect;
    private boolean running;
    private Thread renderThread;
    private Ws281xLedStrip mainStrip;
    private Ws281xLedStrip sideStrip;
    private PixelCoordinates coordinates;
    
    public EffectEngine(Ws281xLedStrip mainStrip, Ws281xLedStrip sideStrip) {
        this.mainStrip = mainStrip;
        this.sideStrip = sideStrip;
        this.running = false;
        
        // Load pixel coordinates
        String coordsPath = "/home/pi/fux/assets/pixelCoordinates.json";
        // Try alternate path if first doesn't exist
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
    
    public void loadEffect(String effectName) {
        if (coordinates == null) {
            System.err.println("Cannot load effect: coordinates not loaded");
            return;
        }
        
        // Stop current effect if running
        stop();
        
        try {
            // Load effect definition
            String effectPath = "/home/pi/fux-effects/" + effectName + ".json";
            Gson gson = new Gson();
            JsonObject effectDef = gson.fromJson(new FileReader(effectPath), JsonObject.class);
            
            String algorithm = effectDef.get("algorithm").getAsString();
            JsonObject params = effectDef.getAsJsonObject("parameters");
            
            // Create effect instance
            if ("radial_wave".equals(algorithm)) {
                currentEffect = new RadialWaveEffect();
            } else {
                System.err.println("Unknown algorithm: " + algorithm);
                return;
            }
            
            // Initialize effect
            currentEffect.initialize(params, coordinates);
            
            // Start rendering
            start();
            
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
        for (int i = 0; i < 268; i++) {
            mainStrip.setPixel(i, 0, 0, 0);
        }
        for (int i = 0; i < 120; i++) {
            sideStrip.setPixel(i, 0, 0, 0);
        }
        mainStrip.render();
        sideStrip.render();
    }
    
    @Override
    public void run() {
        if (currentEffect == null) {
            System.err.println("No effect loaded");
            return;
        }
        
        long frameNumber = 0;
        int fps = currentEffect.getFPS();
        long targetFrameTimeMs = 1000 / fps;
        
        System.out.println("Effect rendering started @ " + fps + " FPS");
        
        while (running) {
            long startTime = System.currentTimeMillis();
            
            // Render frame
            double timeSeconds = frameNumber / (double) fps;
            Map<Integer, Color> pixels = currentEffect.renderFrame(frameNumber, timeSeconds);
            
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
            
            // Sleep to maintain FPS
            long elapsed = System.currentTimeMillis() - startTime;
            long sleepTime = targetFrameTimeMs - elapsed;
            
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            frameNumber++;
            
            // Log every 100 frames
            if (frameNumber % 100 == 0) {
                System.out.println("Frame " + frameNumber + ", render time: " + elapsed + "ms, LEDs: " + pixels.size());
            }
        }
        
        System.out.println("Effect rendering stopped");
        clearAllLEDs();
    }
}
