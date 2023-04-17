import com.github.mbelling.ws281x.Ws281xLedStrip;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Random;

public class MusicMode implements Runnable{

    Ws281xLedStrip fuxStrip;

    ArrayList<Animation> futureAnimations;
    Animation currentAnimation;
    private AnimationParser animationParser;
    Gson jsonReader;

    public volatile boolean stopped = false;
    private int currentBPM = 150;
    private Random wuerfel;

    public MusicMode(Ws281xLedStrip fuxStrip) {
        this.fuxStrip = fuxStrip;
        this.animationParser = new AnimationParser();
        this.jsonReader = new Gson();
        this.wuerfel = new Random();
    }

    public MusicMode() {
        this.animationParser = new AnimationParser();
        this.jsonReader = new Gson();
    }

    public void changeBPM (int newBPM) {
        if (this.stopped) {
            this.currentBPM = newBPM;
        }
    }

    private void loadAnimations() throws IOException {
        InputStream inputStream = this.getClass().getResourceAsStream("/animations/eins.json");
        Reader reader = new InputStreamReader(inputStream, "UTF-8");
        UnparsedAnimation unparsedAnimation = jsonReader.fromJson(reader, UnparsedAnimation.class);
        this.currentAnimation = animationParser.getAnimationFromJSON(unparsedAnimation);
//        this.futureAnimations.add(animationParser.getAnimationFromJSON(unparsedAnimation));

//        inputStream = this.getClass().getResourceAsStream("/animations/2.json");
//        reader = new InputStreamReader(inputStream, "UTF-8");
//        unparsedAnimation = jsonReader.fromJson(reader, UnparsedAnimation.class);
//        this.futureAnimations.add(animationParser.getAnimationFromJSON(unparsedAnimation));
//
//        inputStream = this.getClass().getResourceAsStream("/animations/3.json");
//        reader = new InputStreamReader(inputStream, "UTF-8");
//        unparsedAnimation = jsonReader.fromJson(reader, UnparsedAnimation.class);
//        this.futureAnimations.add(animationParser.getAnimationFromJSON(unparsedAnimation));
//
//        inputStream = this.getClass().getResourceAsStream("/animations/4.json");
//        reader = new InputStreamReader(inputStream, "UTF-8");
//        unparsedAnimation = jsonReader.fromJson(reader, UnparsedAnimation.class);
//        this.futureAnimations.add(animationParser.getAnimationFromJSON(unparsedAnimation));
//
//        inputStream = this.getClass().getResourceAsStream("/animations/5.json");
//        reader = new InputStreamReader(inputStream, "UTF-8");
//        unparsedAnimation = jsonReader.fromJson(reader, UnparsedAnimation.class);
//        this.futureAnimations.add(animationParser.getAnimationFromJSON(unparsedAnimation));
//
//        inputStream = this.getClass().getResourceAsStream("/animations/6.json");
//        reader = new InputStreamReader(inputStream, "UTF-8");
//        unparsedAnimation = jsonReader.fromJson(reader, UnparsedAnimation.class);
//        this.futureAnimations.add(animationParser.getAnimationFromJSON(unparsedAnimation));
//
//        inputStream = this.getClass().getResourceAsStream("/animations/7.json");
//        reader = new InputStreamReader(inputStream, "UTF-8");
//        unparsedAnimation = jsonReader.fromJson(reader, UnparsedAnimation.class);
//        this.futureAnimations.add(animationParser.getAnimationFromJSON(unparsedAnimation));

        this.currentAnimation = futureAnimations.get(0);
    }

    private Animation loadAnimation(String fileName) throws IOException {
        InputStream inputStream = this.getClass().getResourceAsStream("/animations/" + fileName);
        Reader reader = new InputStreamReader(inputStream, "UTF-8");
        UnparsedAnimation unparsedAnimation = jsonReader.fromJson(reader, UnparsedAnimation.class);
        System.out.println(unparsedAnimation.getUnparsedFrames());
        return animationParser.getAnimationFromJSON(unparsedAnimation);
    }

    @Override
    public void run() {
        System.out.println("run color mode");
        try {
            loadAnimations();
        } catch (IOException e) {
            e.printStackTrace();
        }
        while(!Thread.currentThread().isInterrupted() && !stopped){
            // calc time of animation
            int animationTime = (int) (60000 / currentBPM) * currentAnimation.getDuration();
            currentAnimation.setDurationOfFrames(animationTime);
            for (int animationCounter = 0; animationCounter < 128 / currentAnimation.getDuration(); animationCounter++) {
                if (stopped) return;
                for (ParsedFrame f: currentAnimation.getFrames()) {
                    int[][] changes = f.getParsedChanges();
                    for(int index = 0; index < changes.length; index ++) {
                        fuxStrip.setPixel(changes[index][0], changes[index][1], changes[index][2], changes[index][3]);
                    }
                    fuxStrip.render();
                    try {
                        if (stopped) return;
                        Thread.sleep(f.getDuration());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            clearLEDs();
//            this.currentAnimation = futureAnimations.get(wuerfel.nextInt(6) + 1);
        }
    }



    private void clearLEDs() {
        for (int i = 0; i < 268; i++) {
            this.fuxStrip.setPixel(i, 0, 0, 0);
        }
        this.fuxStrip.render();
    }
}
