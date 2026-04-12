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
    private double spawnChance;
    private int minParticles;
    private int maxParticles;

    // Coordinate bounds (computed once)
    private double minY, maxY, minX, maxX;

    // Vivid color palettes — each explosion picks one palette
    private static final Color[][] PALETTES = {
        // warm: red, orange, gold, yellow
        { new Color(255, 30, 30), new Color(255, 120, 0), new Color(255, 200, 0), new Color(255, 255, 100) },
        // cool: cyan, blue, purple, white
        { new Color(0, 220, 255), new Color(60, 60, 255), new Color(180, 50, 255), new Color(220, 220, 255) },
        // green-teal: green, teal, lime, white
        { new Color(0, 255, 80), new Color(0, 255, 200), new Color(180, 255, 0), new Color(200, 255, 200) },
        // pink-magenta: pink, magenta, hot pink, white
        { new Color(255, 80, 150), new Color(255, 0, 200), new Color(255, 50, 100), new Color(255, 200, 255) },
        // gold-white: gold, amber, white, yellow
        { new Color(255, 200, 0), new Color(255, 160, 30), new Color(255, 255, 255), new Color(255, 240, 100) },
        // rainbow burst
        { new Color(255, 0, 50), new Color(255, 180, 0), new Color(0, 255, 100), new Color(80, 80, 255), new Color(255, 0, 255) },
    };

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.random = new Random();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.maxRockets = params.has("max_rockets") ? params.get("max_rockets").getAsInt() : 3;
        this.spawnChance = params.has("spawn_chance") ? params.get("spawn_chance").getAsDouble() : 0.08;
        this.minParticles = params.has("min_particles") ? params.get("min_particles").getAsInt() : 80;
        this.maxParticles = params.has("max_particles") ? params.get("max_particles").getAsInt() : 140;

        this.rockets = new ArrayList<>();
        this.particles = new ArrayList<>();

        // Compute coordinate bounds
        minY = Double.MAX_VALUE; maxY = -Double.MAX_VALUE;
        minX = Double.MAX_VALUE; maxX = -Double.MAX_VALUE;
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate p = coords.get(i);
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
        }

        spawnRocket();

        System.out.println("FireworkEffect initialized:");
        System.out.println("  Max rockets: " + maxRockets);
        System.out.println("  Particles per burst: " + minParticles + "-" + maxParticles);
        System.out.println("  Coord bounds: X[" + (int)minX + "," + (int)maxX + "] Y[" + (int)minY + "," + (int)maxY + "]");
    }

    private void spawnRocket() {
        if (rockets.size() >= maxRockets) return;

        double rangeX = maxX - minX;
        double rangeY = maxY - minY;

        // Start at bottom third
        double startX = minX + rangeX * (0.2 + random.nextDouble() * 0.6);
        double startY = maxY - rangeY * 0.05;

        // Explode in the upper 60% — biased toward upper third
        double targetX = startX + (random.nextDouble() - 0.5) * rangeX * 0.3;
        targetX = Math.max(minX + rangeX * 0.1, Math.min(maxX - rangeX * 0.1, targetX));
        double targetY = minY + rangeY * (0.1 + random.nextDouble() * 0.3);

        // Pick a palette for this rocket
        Color[] palette = PALETTES[random.nextInt(PALETTES.length)];

        // Fast rocket: covers the distance in ~8-12 frames
        double dist = startY - targetY;
        double speed = dist / (8.0 + random.nextDouble() * 4.0);

        rockets.add(new Rocket(startX, startY, targetX, targetY, speed, palette));
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        // Update rockets
        List<Rocket> toRemove = new ArrayList<>();
        for (Rocket rocket : rockets) {
            // Interpolate X toward target as it rises
            double progress = 1.0 - (rocket.y - rocket.targetY) / (rocket.startY - rocket.targetY);
            rocket.x = rocket.startX + (rocket.targetX - rocket.startX) * Math.min(1.0, progress);
            rocket.y -= rocket.speed;

            if (rocket.y <= rocket.targetY) {
                explode(rocket);
                toRemove.add(rocket);
            } else {
                renderRocket(rocket, pixels);
            }
        }
        rockets.removeAll(toRemove);

        // Update particles
        List<Particle> deadParticles = new ArrayList<>();
        for (Particle p : particles) {
            p.x += p.vx;
            p.y += p.vy;
            p.vy += p.gravity;
            p.vx *= 0.98; // air drag
            p.vy *= 0.98;
            p.life -= p.decay;

            if (p.life <= 0) {
                deadParticles.add(p);
            } else {
                renderParticle(p, pixels);
            }
        }
        particles.removeAll(deadParticles);

        // Spawn new rockets
        if (rockets.isEmpty() && random.nextDouble() < spawnChance) {
            spawnRocket();
            // Occasionally launch two at once
            if (random.nextDouble() < 0.3) {
                spawnRocket();
            }
        }

        return pixels;
    }

    private void explode(Rocket rocket) {
        int count = minParticles + random.nextInt(maxParticles - minParticles + 1);
        Color[] palette = rocket.palette;
        double rangeY = maxY - minY;
        // Particle speed scales with the fox size so the burst covers the top half
        double baseSpeed = rangeY * 0.04;

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            // Use gaussian-ish distribution: most particles at medium speed, some fast
            double speedMult = 0.3 + random.nextDouble() * 0.7 + (random.nextDouble() < 0.2 ? random.nextDouble() * 0.5 : 0);
            double speed = baseSpeed * speedMult;

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed;

            // Pick a color from the palette with slight variation
            Color base = palette[random.nextInt(palette.length)];
            int r = clamp(base.getRed() + random.nextInt(40) - 20);
            int g = clamp(base.getGreen() + random.nextInt(40) - 20);
            int b = clamp(base.getBlue() + random.nextInt(40) - 20);

            // Vary decay so particles die at different times (sparkle trail effect)
            double decay = 0.015 + random.nextDouble() * 0.02;
            double gravity = 0.15 + random.nextDouble() * 0.1;

            particles.add(new Particle(rocket.x, rocket.y, vx, vy, new Color(r, g, b), decay, gravity));
        }
    }

    private void renderRocket(Rocket rocket, Map<Integer, Color> pixels) {
        // Bright white-yellow head
        int headLED = findClosestLED(rocket.x, rocket.y, 40);
        if (headLED >= 0) {
            pixels.put(headLED, new Color(255, 255, 220));
        }

        // Short trail
        double trailSpacing = rocket.speed * 0.4;
        for (int i = 1; i <= 2; i++) {
            int trailLED = findClosestLED(rocket.x, rocket.y + i * trailSpacing, 40);
            if (trailLED >= 0 && !pixels.containsKey(trailLED)) {
                int bright = 180 - i * 60;
                pixels.put(trailLED, new Color(bright, bright, bright / 2));
            }
        }
    }

    private void renderParticle(Particle p, Map<Integer, Color> pixels) {
        double glowRadius = 25.0;

        // Light up all LEDs within glow radius, with falloff
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate led = coords.get(i);
            double dx = led.getX() - p.x;
            double dy = led.getY() - p.y;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist < glowRadius) {
                double falloff = 1.0 - (dist / glowRadius);
                falloff *= falloff; // quadratic falloff
                double intensity = p.life * falloff;

                int r = (int)(p.color.getRed() * intensity);
                int g = (int)(p.color.getGreen() * intensity);
                int b = (int)(p.color.getBlue() * intensity);

                if (r > 2 || g > 2 || b > 2) {
                    Color current = pixels.get(i);
                    if (current == null) {
                        pixels.put(i, new Color(r, g, b));
                    } else {
                        // Additive blend
                        pixels.put(i, new Color(
                            Math.min(255, current.getRed() + r),
                            Math.min(255, current.getGreen() + g),
                            Math.min(255, current.getBlue() + b)
                        ));
                    }
                }
            }
        }
    }

    private int findClosestLED(double x, double y, double maxDist) {
        int closest = -1;
        double minDist = maxDist * maxDist;

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

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
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
        double startX, startY;
        double targetX, targetY;
        double speed;
        Color[] palette;

        Rocket(double x, double y, double targetX, double targetY, double speed, Color[] palette) {
            this.x = x;
            this.y = y;
            this.startX = x;
            this.startY = y;
            this.targetX = targetX;
            this.targetY = targetY;
            this.speed = speed;
            this.palette = palette;
        }
    }

    private static class Particle {
        double x, y;
        double vx, vy;
        double life;
        double decay;
        double gravity;
        Color color;

        Particle(double x, double y, double vx, double vy, Color color, double decay, double gravity) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = 1.0;
            this.color = color;
            this.decay = decay;
            this.gravity = gravity;
        }
    }
}
