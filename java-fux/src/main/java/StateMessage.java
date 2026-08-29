import java.util.List;

public class StateMessage {
    private final String type = "STATE";
    private String mode;
    private String currentEffect;
    private List<QueueEntry> queue;
    private int queueCapacity;
    private long timestamp;
    private Integer remainingSeconds;
    /** Present only in MUSIC mode: now playing, tempo and beat phase. */
    private MusicSyncService.MusicState music;

    public StateMessage(String mode, String currentEffect, List<QueueEntry> queue, int queueCapacity, long timestamp, Integer remainingSeconds) {
        this(mode, currentEffect, queue, queueCapacity, timestamp, remainingSeconds, null);
    }

    public StateMessage(String mode, String currentEffect, List<QueueEntry> queue, int queueCapacity,
                        long timestamp, Integer remainingSeconds, MusicSyncService.MusicState music) {
        this.mode = mode;
        this.currentEffect = currentEffect;
        this.queue = queue;
        this.queueCapacity = queueCapacity;
        this.timestamp = timestamp;
        this.remainingSeconds = remainingSeconds;
        this.music = music;
    }

    public String getType() { return type; }
    public String getMode() { return mode; }
    public String getCurrentEffect() { return currentEffect; }
    public List<QueueEntry> getQueue() { return queue; }
    public int getQueueCapacity() { return queueCapacity; }
    public long getTimestamp() { return timestamp; }
    public Integer getRemainingSeconds() { return remainingSeconds; }
    public MusicSyncService.MusicState getMusic() { return music; }
}
