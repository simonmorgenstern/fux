import java.util.ArrayList;

public class Animation {
    private ArrayList<ParsedFrame> frames;
    private int duration;

    public Animation(ArrayList<ParsedFrame> frames, int duration) {
        this.frames = frames;
        this.duration = duration;
    }

    public void setDurationOfFrames(int totalDuration) {
        int durationOfFrame = (int) totalDuration / frames.size();
        int rest = totalDuration - frames.size() * durationOfFrame;
        for (ParsedFrame frame: frames) {
            frame.setDuration(durationOfFrame);
        }
        frames.get(frames.size() - 1).setDuration(durationOfFrame + rest);
    }

    public ArrayList<ParsedFrame> getFrames() {
        return this.frames;
    }

    public int getDuration() {
        return this.duration;
    }

}
