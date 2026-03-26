public class Fux {
    public static void main(String[] args)  {
        try {
            // Use port 8081 for WebSocket (port 80 requires sudo on macOS)
            // Check for command-line arg override: java -jar ... --port 8000
            int port = 8081;
            for (int i = 0; i < args.length; i++) {
                if ("--port".equals(args[i]) && i + 1 < args.length) {
                    try {
                        port = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid port number: " + args[i + 1]);
                    }
                }
            }
            
            WebSocket ws = new WebSocket(port);
            ws.start();
            System.out.println("Fux running on port: " + port);
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
