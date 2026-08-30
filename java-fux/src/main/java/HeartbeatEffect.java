import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Heartbeat — a lub-dub ring pair radiating from the center.
 *
 * Free running it beats at its own {@code bpm}. In MUSIC mode {@link EffectEngine}
 * hands it the live {@link BeatClock} and one full lub-dub lands per
 * {@code beats_per_cycle} beats of the track instead. No fallback source is
 * installed on purpose: with no clock the effect keeps its original wall-clock
 * timing, so queue, idle and preview renders look exactly as they always did.
 */
public class HeartbeatEffect implements Effect, BeatAware {
    private PixelCoordinates coords;
    private double bpm;
    private double pulseWidth;
    private double brightness;
    private int fps;
    private double beatsPerCycle;
    private BeatSource beat;   // null unless music mode installed a clock

    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.bpm = params.has("bpm") ? params.get("bpm").getAsDouble() : 72.0;
        this.pulseWidth = params.has("pulse_width") ? params.get("pulse_width").getAsDouble() : 0.3;
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.beatsPerCycle = params.has("beats_per_cycle")
            ? Math.max(0.25, params.get("beats_per_cycle").getAsDouble()) : 1.0;

        System.out.println("HeartbeatEffect initialized:");
        System.out.println("  BPM: " + bpm);
        System.out.println("  Pulse width: " + pulseWidth);
        System.out.println("  Brightness: " + brightness);
        System.out.println("  FPS: " + fps);
    }

    @Override
    public void setBeatSource(BeatSource source) {
        if (source != null) {
            this.beat = source;
        }
    }

    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();

        // Position within the current cycle (0.0 to 1.0). On a live clock the
        // cycle is measured in beats so the lub always lands on the beat.
        double cyclePos;
        if (beat != null) {
            double position = beat.getBeatPosition() / beatsPerCycle;
            cyclePos = position - Math.floor(position);
        } else {
            double cycleDuration = 60.0 / bpm;
            cyclePos = (timeSeconds % cycleDuration) / cycleDuration;
        }

        // Two pulses per cycle: lub at 0%, dub at 30%
        // Each pulse ring travels from 0.0 to 1.0 over ~40% of half-cycle (20% of full cycle)
        double lubRingPos = getRingPosition(cyclePos, 0.0, 0.20);
        double dubRingPos = getRingPosition(cyclePos, 0.30, 0.20);

        double maxDist = coords.getMaxDistance();

        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate led = coords.get(i);
            double normDist = led.getDistanceFromCenter() / maxDist;

            // Calculate intensity from both pulses, take the max
            double lubIntensity = getPulseIntensity(normDist, lubRingPos);
            double dubIntensity = getPulseIntensity(normDist, dubRingPos);
            double intensity = Math.max(lubIntensity, dubIntensity) * brightness;

            if (intensity > 0.01) {
                // Color gradient: deep red (200,0,0) at center to warm orange (255,80,0) at edges
                int r = (int)((200 + 55 * normDist) * intensity);
                int g = (int)((80 * normDist) * intensity);
                int b = 0;

                r = Math.min(255, Math.max(0, r));
                g = Math.min(255, Math.max(0, g));

                pixels.put(i, new Color(r, g, b));
            }
        }

        return pixels;
    }

    /**
     * Returns the ring position (0.0 to 1.0) for a pulse, or -1 if the pulse is inactive.
     */
    private double getRingPosition(double cyclePos, double pulseStart, double pulseDuration) {
        if (cyclePos < pulseStart || cyclePos > pulseStart + pulseDuration) {
            return -1.0;
        }
        double progress = (cyclePos - pulseStart) / pulseDuration;
        return progress;
    }

    /**
     * Calculate LED intensity based on proximity to the expanding ring front.
     * LEDs near the ring light up, with a fading trail behind.
     */
    private double getPulseIntensity(double normDist, double ringPos) {
        if (ringPos < 0) {
            return 0.0;
        }

        double distFromRing = normDist - ringPos;

        // LED is ahead of the ring front - no light
        if (distFromRing > pulseWidth * 0.3) {
            return 0.0;
        }

        // LED is near or behind the ring front
        if (distFromRing >= 0) {
            // Slightly ahead but within small forward glow
            return 1.0 - (distFromRing / (pulseWidth * 0.3));
        }

        // LED is behind the ring - fading trail
        double trailDist = Math.abs(distFromRing);
        if (trailDist > pulseWidth) {
            return 0.0;
        }

        return 1.0 - (trailDist / pulseWidth);
    }

    @Override
    public void dispose() {
        // Nothing to clean up
    }

    @Override
    public String getName() {
        return "Heartbeat";
    }

    @Override
    public int getFPS() {
        return fps;
    }
}
