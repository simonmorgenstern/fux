import com.github.mbelling.ws281x.Color;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FrameParser {
    private final Type INTEGER_ARRAY_TYPE;

    Gson jsonParser;
    ArrayList<Integer[]> currentArray;

    Random colorDice;
    Color currentColor;
    Color randomColor;

    private final String RGB_COLOR_PATTERN = "(\\d+),(\\d+),(\\d+)";
    private Pattern rgbColorPattern;
    private Matcher rgbColorMatcher;

    private final String RANGE_PATTERN = "\\{(\\d+)-(\\d+)}";
    private Pattern rangePattern;
    private Matcher rangeMatcher;

    private final String ARRAY_PATTERN = "(\\[.+\\])";
    private Pattern arrayPattern;
    private Matcher arrayMatcher;

    private final String SINGLE_LED_PATTERN = "^\\d+";
    private Pattern singleLEDPattern;
    private Matcher singleLEDMatcher;


    public FrameParser() {
        INTEGER_ARRAY_TYPE = new TypeToken<ArrayList<Integer>>(){}.getType();

        this.jsonParser = new Gson();
        this.colorDice = new Random();
        this.currentArray = new ArrayList<>();
        this.currentColor = new Color(0, 0, 0);
        this.randomColor = new Color(0, 0, 0);

        this.rgbColorPattern = Pattern.compile(RGB_COLOR_PATTERN);
        this.rangePattern = Pattern.compile(RANGE_PATTERN);
        this.arrayPattern = Pattern.compile(ARRAY_PATTERN);
        this.singleLEDPattern = Pattern.compile(SINGLE_LED_PATTERN);
    }

    public ParsedFrame getParsedFrame(UnparsedFrame f) {
        this.setRandomColor();
        for(ArrayList<String> change: f.getChanges()) {
            // get Color
            currentColor = this.getColor(change.get(0));
            // get indices
            getIndices(change.get(1)).forEach(integer ->
                    this.currentArray.add(new Integer[]{integer, currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue()})
            );
        }
        int[][] parsedChanges = new int[currentArray.size()][4];
        for (int index = 0; index < parsedChanges.length; index++){
            parsedChanges[index] = new int[]{currentArray.get(index)[0],  currentArray.get(index)[1], currentArray.get(index)[2], currentArray.get(index)[3]};
        }
        // clear used variables
        this.currentArray.clear();
        return new ParsedFrame(f.getIndex(), f.getDuration(), parsedChanges);
    }

    public ParsedFrame getParsedFrame(String frame) {
        UnparsedFrame f = this.jsonParser.fromJson(frame, UnparsedFrame.class);
        return getParsedFrame(f);
    }


    public void setRandomColor() {
        this.randomColor = new Color(
                colorDice.nextInt(256),
                colorDice.nextInt(256),
                colorDice.nextInt(256)
        );
    }

    public Color getRandomColor() {
        return new Color(
                colorDice.nextInt(256),
                colorDice.nextInt(256),
                colorDice.nextInt(256)
        );
    }

    public Color getColor(String colorString) {
        rgbColorMatcher = rgbColorPattern.matcher(colorString);
        if (rgbColorMatcher.find()) {
            return new Color(
                    Integer.parseInt(rgbColorMatcher.group(1)),
                    Integer.parseInt(rgbColorMatcher.group(2)),
                    Integer.parseInt(rgbColorMatcher.group(3))
            );
        } 
        if (colorString.equals("c")) {
            return new Color(0, 0, 0);
        } 
        if (colorString.equals("r")) {
            return randomColor;
        } 
        if (colorString.equals("ra")) {
            return getRandomColor();
        }
        return new Color(0, 0,0);
    }

    public ArrayList<Integer> getIndices(String indexString) {
        ArrayList<Integer> indices = new ArrayList<>();

        rangeMatcher = rangePattern.matcher(indexString);
        if (rangeMatcher.find()) {
            int start = Integer.parseInt(rangeMatcher.group(1));
            int end = Integer.parseInt(rangeMatcher.group(2));
            for (int i = start; i <= end; i++) {
                indices.add(i);
            }
            return indices;
        }

        arrayMatcher = arrayPattern.matcher(indexString);
        if (arrayMatcher.find()) {
            return jsonParser.fromJson(arrayMatcher.group(1), INTEGER_ARRAY_TYPE);
        }

        singleLEDMatcher = singleLEDPattern.matcher(indexString);
        if (singleLEDMatcher.find()) {
            indices.add(Integer.parseInt(singleLEDMatcher.group(0)));
        }
        return indices;
    }

}
