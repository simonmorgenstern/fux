import com.github.mbelling.ws281x.LedStripType;
import com.github.mbelling.ws281x.Ws281xLedStrip;
import com.google.gson.Gson;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;


public class WebSocket extends WebSocketServer {
    private ArrayList<ParsedFrame> parsedFrames;
    private Gson gson;
    private FrameParser frameParser;
    private static Ws281xLedStrip fuxStrip = new Ws281xLedStrip(268, 18, 800000, 10, 100, 0, false, LedStripType.WS2811_STRIP_GRB, true);
    private static Ws281xLedStrip sideStrip = new Ws281xLedStrip(120, 13, 800000, 10, 200, 1, false, LedStripType.WS2811_STRIP_GRB, true);
    private static MusicMode musicModeRunner = new MusicMode(fuxStrip);
    private static Thread musicModeThread;

    public WebSocket(int port) throws UnknownHostException {
        super(new InetSocketAddress(port));
        this.gson = new Gson();
        this.frameParser = new FrameParser();
        this.parsedFrames = new ArrayList<>();
        Ws281xLedStrip fuxStrip = new Ws281xLedStrip(268, 18, 800000, 10, 100, 0, false, LedStripType.WS2811_STRIP_GRB, true);
        // Ws281xLedStrip sideStrip = new Ws281xLedStrip(120, 13, 800000, 10, 100, 1, false, LedStripType.WS2811_STRIP_GRB, true);
    }

    @Override
    public void onOpen(org.java_websocket.WebSocket webSocket, ClientHandshake clientHandshake) {
        webSocket.send("Welcome to the server!");
        this.broadcast("new connection: " + clientHandshake.getResourceDescriptor());
        System.out.println(webSocket.getRemoteSocketAddress().getAddress().getHostAddress() + " entered the room");
    }

    @Override
    public void onClose(org.java_websocket.WebSocket webSocket, int i, String s, boolean b) {
        this.broadcast(webSocket + " has left the room");
        System.out.println(webSocket + " has left the room");
    }

    @Override
    public void onMessage(org.java_websocket.WebSocket webSocket, String message) {
        this.broadcast(message);
        System.out.println(webSocket + ": " + message);

        if (message.matches("start:\\d+")) {
            for (ParsedFrame frame: this.parsedFrames) {
                int[][] changes = frame.getParsedChanges();
                for(int index = 0; index < changes.length; index ++) {
                   fuxStrip.setPixel(changes[index][0], changes[index][1], changes[index][2], changes[index][3]);
                }
                try {
                    Thread.sleep(frame.getDuration());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                fuxStrip.render();
            }
            this.parsedFrames.clear();
        } 
        else if (message.matches("musicModeOn:\\d+")) {
            if (musicModeThread != null && musicModeThread.isAlive()) return; // guard clause

            System.out.println("start music mode");
            musicModeRunner.stopped = false;
            musicModeThread = new Thread(musicModeRunner);
            musicModeThread.start();
        } 
        else if (message.matches("musicModeOff")) {
            stopMusicMode();
        } 
        else {
            ParsedFrame parsedFrame = frameParser.getParsedFrame(message);
            this.parsedFrames.add(parsedFrame);
        }
    }

    private void startMusicMode(int bpm) {
        if (musicModeThread != null && musicModeThread.isAlive()) return; // guard clause
        musicModeRunner.changeBPM(bpm);
        System.out.println("start music mode");
        musicModeRunner.stopped = false;
        musicModeThread = new Thread(musicModeRunner);
        musicModeThread.start();
    }

    private void stopMusicMode() {
        if (!musicModeThread.isAlive()) return; // guard clause
        musicModeRunner.stopped = true;
        musicModeThread.interrupt();
        for (int i = 0; i < 268; i++) {
            fuxStrip.setPixel(i, 0, 0, 0);
            if (i < 120) {
                sideStrip.setPixel(i, 0, 0, 0);
            }
        }
        fuxStrip.render();
        sideStrip.render();
    }

    @Override
    public void onError(org.java_websocket.WebSocket webSocket, Exception error) {
        error.printStackTrace();
        if (webSocket != null) {
            // some errors like port binding failed may not be assignable to a specific websocket
        }
    }

    @Override
    public void onStart() {
        System.out.println("Server started!");
        setConnectionLostTimeout(0);
    }
}
