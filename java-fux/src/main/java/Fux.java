public class Fux {
    public static void main(String[] args)  {
        try {
            WebSocket ws = new WebSocket(80);
            ws.start();
//            System.out.println("WebSocket started on port: 8080");
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
