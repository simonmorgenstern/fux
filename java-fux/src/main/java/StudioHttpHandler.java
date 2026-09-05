import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.awt.Color;
import java.util.*;

/** LAN API for Fux Studio. Draft sessions are isolated from saved/live playback. */
public class StudioHttpHandler implements HttpHandler {
    private final EffectEngine engine;
    private final Gson gson = new Gson();
    private final PixelCoordinates coordinates = PixelCoordinates.loadWithFallback();
    private final Map<String, Session> sessions = new HashMap<>();
    private class Session {
        JsonElement json;
        MusicArrangement show;
        ArrangementRenderer renderer = new ArrangementRenderer(coordinates);
        long used;
    }
    public StudioHttpHandler(EffectEngine engine) { this.engine = engine; }
    public synchronized void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/api/studio/connect") && method.equals("POST")) {
                engine.getMusicSyncService().start();
                send(exchange, 200, status());
            } else if (path.equals("/api/studio/status") && method.equals("GET")) {
                engine.getMusicSyncService().start();
                send(exchange, 200, status());
            } else if (path.equals("/api/studio/layout") && method.equals("GET")) {
                if (coordinates == null) throw new IOException("LED coordinates unavailable");
                List<Map<String, Object>> points = new ArrayList<>();
                for (int i = 0; i < Math.min(268, coordinates.getCount()); i++) {
                    Map<String, Object> point = new HashMap<>();
                    point.put("index", i); point.put("x", coordinates.get(i).getX()); point.put("y", coordinates.get(i).getY());
                    points.add(point);
                }
                send(exchange, 200, points);
            } else if (path.startsWith("/api/studio/arrangements/")) {
                String id = path.substring("/api/studio/arrangements/".length());
                MusicArrangement.require(id.matches("[A-Za-z0-9]{22}"), "Invalid Spotify track ID");
                if (method.equals("GET")) {
                    MusicArrangement show = engine.getArrangementStore().get(id);
                    if (show == null) send(exchange, 404, error("No saved arrangement"));
                    else send(exchange, 200, show);
                } else if (method.equals("PUT")) {
                    MusicArrangement show = gson.fromJson(read(exchange), MusicArrangement.class);
                    MusicArrangement.require(show != null && id.equals(show.trackId), "Track ID does not match");
                    engine.getArrangementStore().save(show);
                    send(exchange, 200, show);
                } else send(exchange, 405, error("Use GET or PUT"));
            } else if (path.equals("/api/studio/preview") && method.equals("POST")) {
                JsonObject request = gson.fromJson(read(exchange), JsonObject.class);
                MusicArrangement.require(request != null && request.has("sessionId") && request.has("arrangement") && request.has("positionSeconds"), "Incomplete preview request");
                String id = request.get("sessionId").getAsString();
                MusicArrangement.require(id.matches("[A-Za-z0-9-]{1,64}"), "Invalid preview session");
                long now = System.currentTimeMillis();
                Iterator<Session> iterator = sessions.values().iterator();
                while (iterator.hasNext()) {
                    Session s = iterator.next();
                    if (now - s.used > 30000) { s.renderer.close(); iterator.remove(); }
                }
                Session session = sessions.get(id);
                if (session == null) {
                    if (sessions.size() >= 8) { send(exchange, 429, error("Too many preview sessions")); return; }
                    session = new Session(); sessions.put(id, session);
                }
                session.used = now;
                JsonElement json = request.get("arrangement");
                if (!json.equals(session.json)) {
                    MusicArrangement show = gson.fromJson(json, MusicArrangement.class);
                    MusicArrangement.require(show != null, "Missing arrangement"); show.validate();
                    session.show = show; session.json = json;
                }
                double time = request.get("positionSeconds").getAsDouble();
                MusicArrangement.require(MusicArrangement.finite(time) && time >= 0 && time <= session.show.durationSeconds, "Position outside song");
                Map<Integer, Color> frame = session.renderer.render(session.show, time);
                int[] rgb = new int[268];
                for (Map.Entry<Integer, Color> pixel : frame.entrySet()) {
                    if (pixel.getKey() >= 0 && pixel.getKey() < rgb.length) rgb[pixel.getKey()] = pixel.getValue().getRGB() & 0xffffff;
                }
                Map<String, Object> response = new HashMap<>();
                response.put("pixels", rgb); response.put("positionSeconds", time);
                send(exchange, 200, response);
            } else send(exchange, 404, error("Unknown Studio endpoint"));
        } catch (RuntimeException e) {
            send(exchange, 400, error(e.getMessage() == null ? "Invalid request" : e.getMessage()));
        } catch (IOException e) {
            send(exchange, 500, error("Studio storage or rendering unavailable"));
            System.err.println("Studio: " + e.getMessage());
        } finally { exchange.close(); }
    }
    private Map<String, Object> status() {
        MusicSyncService service = engine.getMusicSyncService();
        Map<String, Object> state = new HashMap<>();
        state.put("apiVersion", 1); state.put("music", service.snapshot());
        state.put("durationSeconds", service.getNowPlaying().durationMs / 1000.0);
        state.put("mode", engine.getMode().toString());
        return state;
    }
    private String read(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int n;
        while ((n = exchange.getRequestBody().read(buffer)) != -1) {
            MusicArrangement.require(out.size() + n <= 512 * 1024, "Request too large"); out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
    private Map<String, String> error(String message) { return Collections.singletonMap("error", message); }
    private void send(HttpExchange exchange, int code, Object body) throws IOException {
        byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, bytes.length); exchange.getResponseBody().write(bytes);
    }
}
