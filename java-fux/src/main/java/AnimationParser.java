import com.google.gson.Gson;

import java.lang.reflect.Array;
import java.util.ArrayList;

public class AnimationParser {

    Gson jsonParser;
    FrameParser frameParser;

    public AnimationParser() {
        this.jsonParser = new Gson();
        this.frameParser = new FrameParser();
    }

    public Animation getAnimationFromJSON(UnparsedAnimation unparsed) {
        ArrayList<ParsedFrame> parsedFrames = new ArrayList<>();
        for(String uff: unparsed.getUnparsedFrames()) {
            parsedFrames.add(frameParser.getParsedFrame(uff));
        }

        return new Animation(parsedFrames, unparsed.getDuration());
    }


}
