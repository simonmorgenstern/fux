import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class FireworkEffect implements Effect {
    private PixelCoordinates coords;
    private List<Rocket> rockets;
    private List<Particle> particles;
    private Random random;
    private int maxRockets;
    private int fps;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.maxRockets = params.has("max_rockets") ? params.get("max_rockets").getAsInt() : 2;
        
        this.rockets = new ArrayList<>();
        this.particles = new ArrayList<>();
        
        // Spawn initial rocket
        spawnRocket();
        
        System.out.println("FireworkEffect initialized:");
        System.out.println("  Max rockets: " + maxRockets);
    }
    
    private void spawnRocket() {
        if (rockets.size() >= maxRockets) return;
        
        // Random X position, start at bottom (high Y)
        double startX = 150 + random.nextDouble() * 200; // Center area
        double startY = 550; // Bottom
        
        // Target: same X, top area
        double targetX = startX + (random.nextDouble() - 0.5) * 50; // Slight drift
        double targetY = 50 + random.nextDouble() * 100; // Top area
        
        // Random color for the explosion
        Color color = new Color(
            random.nextInt(256),
            random.nextInt(256),
            random.nextInt(256)
        );
        
        rockets.add(new Rocket(startX, startY, targetX, targetY, 8.0, color)); // Faster rocket!
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Update and render rockets
        List<Rocket> rocketsToRemove = new ArrayList<>();
        for (Rocket rocket : rockets) {
            rocket.y -= rocket.speed; // Move up (decrease Y)
            
            // Check if reached target (explode)
            if (rocket.y <= rocket.targetY) {
                explode(rocket);
                rocketsToRemove.add(rocket);
            } else {
                // Render rocket trail
                renderRocket(rocket, pixels);
            }
        }
        rockets.removeAll(rocketsToRemove);
        
        // Update and render particles
        List<Particle> particlesToRemove = new ArrayList<>();
        for (Particle p : particles) {
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.5; // Stronger gravity
            p.life -= 0.04; // Faster fade out
            
            if (p.life <= 0) {
                particlesToRemove.add(p);
            } else {
                renderParticle(p, pixels);
            }
        }
        particles.removeAll(particlesToRemove);
        
        // Spawn new rocket more frequently
        if (rockets.isEmpty() && particles.size() < 30 && random.nextDouble() < 0.15) {
            spawnRocket();
        }
        
        return pixels;
    }
    
    private void explode(Rocket rocket) {
        // Create 30-50 particles
        int particleCount = 30 + random.nextInt(20);
        
        for (int i = 0; i < particleCount; i++) {
            // Random direction (angle)
            double angle = random.nextDouble() * 2 * Math.PI;
            double speed = 2.0 + random.nextDouble() * 5.0; // Faster particles!
            
            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 3.0; // Stronger initial burst
            
            // Vary colors around the rocket's base color
            int r = Math.min(255, Math.max(0, rocket.color.getRed() + random.nextInt(100) - 50));
            int g = Math.min(255, Math.max(0, rocket.color.getGreen() + random.nextInt(100) - 50));
            int b = Math.min(255, Math.max(0, rocket.color.getBlue() + random.nextInt(100) - 50));
            
            particles.add(new Particle(rocket.x, rocket.y, vx, vy, new Color(r, g, b)));
        }
        
        System.out.println("💥 Firework exploded at (" + (int)rocket.x + "," + (int)rocket.y + ") with " + particleCount + " particles");
    }
    
    private void renderRocket(Rocket rocket, Map<Integer, Color> pixels) {
        // Find closest LED to rocket position
        int closestLED = findClosestLED(rocket.x, rocket.y);
        if (closestLED >= 0) {
            // Bright white/yellow rocket
            pixels.put(closestLED, new Color(255, 255, 200));
            
            // Trail behind (3-4 LEDs below)
            for (int i = 1; i <= 3; i++) {
                int trailLED = findClosestLED(rocket.x, rocket.y + i * 15);
                if (trailLED >= 0) {
                    int brightness = 200 - i * 50;
                    pixels.put(trailLED, new Color(brightness, brightness, brightness/2));
                }
            }
        }
    }
    
    private void renderParticle(Particle p, Map<Integer, Color> pixels) {
        int closestLED = findClosestLED(p.x, p.y);
        if (closestLED >= 0) {
            // Apply life (fade)
            int r = (int)(p.color.getRed() * p.life);
            int g = (int)(p.color.getGreen() * p.life);
            int b = (int)(p.color.getBlue() * p.life);
            
            if (r > 10 || g > 10 || b > 10) {
                Color currentColor = pixels.get(closestLED);
                if (currentColor == null) {
                    pixels.put(closestLED, new Color(r, g, b));
                } else {
                    // Blend particles
                    int newR = Math.min(255, currentColor.getRed() + r);
                    int newG = Math.min(255, currentColor.getGreen() + g);
                    int newB = Math.min(255, currentColor.getBlue() + b);
                    pixels.put(closestLED, new Color(newR, newG, newB));
                }
            }
        }
    }
    
    private int findClosestLED(double x, double y) {
        int closest = -1;
        double minDist = Double.MAX_VALUE;
        
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate led = coords.get(i);
            double dx = led.getX() - x;
            double dy = led.getY() - y;
            double dist = dx * dx + dy * dy;
            
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }
        
        return closest;
    }
    
    @Override
    public void dispose() {
        rockets.clear();
        particles.clear();
    }
    
    @Override
    public String getName() {
        return "Firework";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
    
    private static class Rocket {
        double x, y;
        double targetY;
        double targetX;
        double speed;
        Color color;
        
        Rocket(double x, double y, double targetX, double targetY, double speed, Color color) {
            this.x = x;
            this.y = y;
            this.targetX = targetX;
            this.targetY = targetY;
            this.speed = speed;
            this.color = color;
        }
    }
    
    private static class Particle {
        double x, y;
        double vx, vy; // Velocity
        double life; // 1.0 to 0.0
        Color color;
        
        Particle(double x, double y, double vx, double vy, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = 1.0;
            this.color = color;
        }
    }
}
