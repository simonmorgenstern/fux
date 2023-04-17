import java.util.ArrayList;

public class UnparsedFrame {
    private int index;
    private int duration;
    private ArrayList<ArrayList<String>> changes;

    public UnparsedFrame(int index, int duration, ArrayList<ArrayList<String>> changes) {
        this.index = index;
        this.duration = duration;
        this.changes = changes;
    }

    @Override
    public String toString() {
        return "Frame{" +
               "index=" + this.index +
               ", duration=" + this.duration +
               ", changes=" + this.changes +
               '}';
    }

    public int getIndex() {
        return this.index;
    }

    public int getDuration() {
        return this.duration;
    }

    public ArrayList<ArrayList<String>> getChanges() {
        return this.changes;
    }
}
