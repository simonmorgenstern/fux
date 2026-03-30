public class QueueEntry {
    private final String effectName;
    private final Integer duration;  // seconds per cycle, null = use JSON default
    private final Integer repeat;    // number of repetitions, null = 1

    public QueueEntry(String effectName, Integer duration, Integer repeat) {
        this.effectName = effectName;
        this.duration = duration;
        this.repeat = repeat;
    }

    public String getEffectName() {
        return effectName;
    }

    public Integer getDuration() {
        return duration;
    }

    public int getRepeat() {
        return repeat != null ? repeat : 1;
    }

    public int getTotalDuration(int defaultDuration) {
        int dur = duration != null ? duration : defaultDuration;
        return dur * getRepeat();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(effectName);
        if (duration != null) sb.append(":duration=").append(duration);
        if (repeat != null && repeat > 1) sb.append(":repeat=").append(repeat);
        return sb.toString();
    }
}
