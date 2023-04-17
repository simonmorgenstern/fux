public class ParsedFrame {
    private int index;
    private int duration;
    private int[][] parsedChanges;
    private int[][] parsedSideStripChanges;

    public ParsedFrame(int index, int duration, int[][] parsedChanges) {
        this.index = index;
        this.duration = duration;
        this.parsedChanges = parsedChanges;
    }

    public int getDuration() {
        return this.duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getIndex() {
        return this.index;
    }

    public int[][] getParsedChanges() {
        return this.parsedChanges;
    }

    public void printParsedChanges() {
        for(int y = 0; y < parsedChanges.length; y++) {
            System.out.println("index: " + this.parsedChanges[y][0] + " (" 
                              + this.parsedChanges[y][1] + "," 
                              + this.parsedChanges[y][2] + "," 
                              + this.parsedChanges[y][3] + ")");
        }
    }
}
