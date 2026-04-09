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

            // Handle Ctrl+C via signal handler instead of shutdown hook.
            // Signal handlers run BEFORE JVM shutdown hooks, which is critical
            // because diozero registers its own shutdown hook that calls
            // ws2811_fini() (freeing native memory). If we used a shutdown hook,
            // diozero's hook could free the memory while our render thread is
            // still calling ws2811_render() → SIGSEGV.
            // By using a signal handler, we close the native strip first,
            // then call System.exit() which triggers diozero's hook as a no-op.
            try {
                sun.misc.Signal.handle(new sun.misc.Signal("INT"), sig -> {
                    System.out.println("Shutting down - clearing LEDs...");
                    ws.shutdown();
                    try {
                        ws.stop(1000);
                    } catch (Exception e) {
                        // Ignore errors during shutdown
                    }
                    System.exit(0);
                });
            } catch (IllegalArgumentException e) {
                // Signal handling not supported on this platform, fall back to shutdown hook
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("Shutting down - clearing LEDs...");
                    ws.shutdown();
                    try {
                        ws.stop(1000);
                    } catch (Exception ex) {
                        // Ignore errors during shutdown
                    }
                }));
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
