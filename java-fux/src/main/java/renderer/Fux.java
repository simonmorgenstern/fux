package renderer;

import server.WebSocket;

public class Fux {
    public static void main(String[] args)  {
        try {
            WebSocket ws = new WebSocket(80);
            ws.start();
        }
        catch (Exception e) {
            System.err.println("Error starting WebSocket server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
