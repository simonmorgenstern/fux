# Java Implementation Sketch

## Dateistruktur

```
java-fux/src/main/java/
├── Fux.java (existing - entry point)
├── WebSocket.java (existing - extend)
├── effects/
│   ├── Effect.java (interface)
│   ├── EffectEngine.java (main engine)
│   ├── EffectLoader.java (JSON parser)
│   ├── RadialWaveEffect.java (implementation)
│   ├── ColorPulseEffect.java (implementation)
│   └── PixelCoordinates.java (coordinate system)
```

## Pseudo-Code

### Effect.java (Interface)
```java
public interface Effect {
    // Called once when effect loads
    void initialize(JsonObject params, PixelCoordinates coords);
    
    // Called every frame (33ms @ 30fps)
    // Returns map of LED index -> RGB color
    Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds);
    
    // Cleanup
    void dispose();
    
    // Metadata
    String getName();
    int getFPS();
}
```

### EffectEngine.java
```java
public class EffectEngine implements Runnable {
    private Effect currentEffect;
    private boolean running;
    private Thread renderThread;
    private Ws281xLedStrip mainStrip;
    private Ws281xLedStrip sideStrip;
    
    public void loadEffect(String effectName) {
        // Load JSON from /home/pi/fux-effects/{effectName}.json
        JsonObject json = EffectLoader.load(effectName);
        
        // Create effect instance based on algorithm type
        String algorithm = json.get("algorithm").getAsString();
        currentEffect = EffectFactory.create(algorithm);
        
        // Initialize with parameters
        currentEffect.initialize(json.getAsJsonObject("parameters"), coords);
        
        // Start render loop
        start();
    }
    
    @Override
    public void run() {
        long frameNumber = 0;
        long targetFrameTime = 1000 / currentEffect.getFPS();
        
        while (running) {
            long startTime = System.currentTimeMillis();
            
            // Render frame
            double timeSeconds = frameNumber / (double) currentEffect.getFPS();
            Map<Integer, Color> pixels = currentEffect.renderFrame(frameNumber, timeSeconds);
            
            // Apply to LED strips
            applyPixels(pixels);
            mainStrip.render();
            sideStrip.render();
            
            // Sleep to maintain FPS
            long elapsed = System.currentTimeMillis() - startTime;
            long sleepTime = targetFrameTime - elapsed;
            if (sleepTime > 0) {
                Thread.sleep(sleepTime);
            }
            
            frameNumber++;
        }
    }
    
    private void applyPixels(Map<Integer, Color> pixels) {
        // Clear all first
        for (int i = 0; i < 268; i++) {
            mainStrip.setPixel(i, Color.BLACK);
        }
        for (int i = 0; i < 120; i++) {
            sideStrip.setPixel(i, Color.BLACK);
        }
        
        // Set colors
        for (Map.Entry<Integer, Color> entry : pixels.entrySet()) {
            int index = entry.getKey();
            if (index < 268) {
                mainStrip.setPixel(index, entry.getValue());
            } else {
                sideStrip.setPixel(index - 268, entry.getValue());
            }
        }
    }
}
```

### RadialWaveEffect.java
```java
public class RadialWaveEffect implements Effect {
    private PixelCoordinates coords;
    private double waveSpacing;
    private double waveWidth;
    private double waveSpeed;
    private int[] colorsHSV;
    private double centerX, centerY;
    private int fps;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.waveSpacing = params.get("wave_spacing").getAsDouble();
        this.waveWidth = params.get("wave_width").getAsDouble();
        this.waveSpeed = params.get("wave_speed").getAsDouble();
        
        JsonArray colorsArray = params.getAsJsonArray("colors_hsv");
        this.colorsHSV = new int[colorsArray.size()];
        for (int i = 0; i < colorsArray.size(); i++) {
            this.colorsHSV[i] = colorsArray.get(i).getAsInt();
        }
        
        // Auto-calculate center if not provided
        this.centerX = coords.getCenterX();
        this.centerY = coords.getCenterY();
        
        this.fps = 33;
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Calculate animation time offset
        double animationOffset = frameNumber * waveSpeed;
        
        // For each LED
        for (int ledIndex = 0; ledIndex < coords.getCount(); ledIndex++) {
            PixelCoordinate led = coords.get(ledIndex);
            double distance = led.getDistanceFromCenter();
            
            // Check which waves affect this LED
            for (int waveNum = -20; waveNum < 20; waveNum++) {
                double waveOffset = waveNum * waveSpacing;
                double waveRadius = animationOffset - waveOffset;
                
                // Skip if wave not near this LED
                if (waveRadius < -waveWidth/2 || waveRadius > distance + waveWidth/2) {
                    continue;
                }
                
                double distFromWave = Math.abs(distance - waveRadius);
                
                // LED is within wave band
                if (distFromWave < waveWidth / 2) {
                    // Calculate intensity
                    double intensity = 1.0 - (distFromWave / (waveWidth / 2));
                    
                    // Get color for this wave
                    int colorIndex = Math.abs(waveNum) % colorsHSV.length;
                    int hue = colorsHSV[colorIndex];
                    
                    // Convert HSV to RGB
                    Color color = Color.getHSBColor(hue / 360f, 1f, (float)intensity);
                    
                    // Only add if bright enough
                    if (color.getRed() > 5 || color.getGreen() > 5 || color.getBlue() > 5) {
                        pixels.put(ledIndex, color);
                    }
                }
            }
        }
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Radial Wave";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
```

### WebSocket.java - Erweiterung

```java
// In onMessage() method:

if (message.startsWith("EFFECT:")) {
    String effectName = message.substring(7);
    effectEngine.loadEffect(effectName);
    return;
}

if (message.equals("STOP_EFFECT")) {
    effectEngine.stop();
    return;
}

if (message.equals("LIST_EFFECTS")) {
    String[] effects = EffectLoader.listEffects();
    // Send back as JSON array
    return;
}
```

## Memory & Performance

**Current approach (frame buffer):**
- 200 frames × ~150 LEDs × 3 bytes = ~90 KB RAM
- All frames pre-calculated

**Effect Engine approach:**
- Only current frame in memory: 268 × 3 bytes = ~800 bytes
- Calculated on-demand
- **~100x less RAM usage**

**CPU Usage:**
- Per frame: 268 LEDs × distance calculation + HSV conversion
- ~10,000 operations per frame
- @ 33 FPS = ~330k ops/sec
- Pi 3/4 can handle this easily (<5% CPU)

## Next Steps

1. **Create base infrastructure** (Effect interface, EffectEngine, EffectLoader)
2. **Implement RadialWaveEffect** (port current Python logic to Java)
3. **Test locally** on Pi
4. **Add WebSocket commands**
5. **Create more effects**

Ready to start coding?
