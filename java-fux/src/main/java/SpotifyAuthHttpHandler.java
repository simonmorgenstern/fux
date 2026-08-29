import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * One-time Spotify login for music mode.
 *
 * <ul>
 *   <li>{@code GET /api/spotify/login} — a small page with the authorize link
 *       and a paste-the-code form</li>
 *   <li>{@code GET /api/spotify/callback?code=…} — token exchange, used when
 *       the redirect can actually reach this server</li>
 *   <li>{@code POST /api/spotify/callback} — same exchange from the paste form</li>
 *   <li>{@code GET /api/spotify/status} — configuration and connection state</li>
 *   <li>{@code POST /api/spotify/logout} — forget the stored tokens</li>
 * </ul>
 *
 * The paste form exists because Spotify only accepts HTTPS or loopback redirect
 * URIs: the browser lands on {@code http://127.0.0.1:8080/…}, which is the
 * user's own machine rather than the Pi, so the code has to be carried across
 * by hand unless the login is done from a browser on the Pi itself.
 */
public class SpotifyAuthHttpHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(SpotifyAuthHttpHandler.class);

    private final EffectEngine effectEngine;

    public SpotifyAuthHttpHandler(EffectEngine effectEngine) {
        this.effectEngine = effectEngine;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (path.endsWith("/login")) {
                handleLogin(exchange);
            } else if (path.endsWith("/callback")) {
                handleCallback(exchange, method);
            } else if (path.endsWith("/status")) {
                handleStatus(exchange);
            } else if (path.endsWith("/logout") && "POST".equals(method)) {
                auth().logout();
                sendJson(exchange, 200, "{\"success\":true,\"authorized\":false}");
            } else {
                sendJson(exchange, 404, "{\"error\":\"Unknown Spotify endpoint\"}");
            }
        } catch (Exception e) {
            logger.error("Spotify auth request failed: {}", e.getMessage(), e);
            sendJson(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private SpotifyAuthService auth() {
        return effectEngine.getMusicSyncService().getAuth();
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        SpotifyAuthService auth = auth();
        if (!auth.isConfigured()) {
            sendHtml(exchange, 500, page("Spotify not configured",
                "<p>Create <code>~/.fux/spotify.json</code> on the fux with your app credentials:</p>"
                    + "<pre>{\n"
                    + "  \"clientId\": \"…\",\n"
                    + "  \"clientSecret\": \"…\",\n"
                    + "  \"redirectUri\": \"" + escapeHtml(auth.getRedirectUri()) + "\"\n"
                    + "}</pre>"
                    + "<p>Then add the same redirect URI to the app in the Spotify developer "
                    + "dashboard and reload this page.</p>"));
            return;
        }

        String authorizeUrl = auth.getAuthorizeUrl("fux");
        String body =
            "<p>Step 1 — approve access:</p>"
            + "<p><a class=\"button\" href=\"" + escapeHtml(authorizeUrl) + "\">Connect Spotify</a></p>"
            + "<p>Step 2 — Spotify redirects to <code>" + escapeHtml(auth.getRedirectUri()) + "</code>. "
            + "If that page fails to load (it points at your own machine, not the fux), copy the "
            + "<code>code</code> value out of the address bar and paste it here:</p>"
            + "<form method=\"POST\" action=\"/api/spotify/callback\">"
            + "<input type=\"text\" name=\"code\" placeholder=\"Authorization code\" size=\"60\" />"
            + "<button type=\"submit\">Finish</button>"
            + "</form>"
            + "<p class=\"muted\">Currently " + (auth.isAuthorized() ? "connected" : "not connected") + ".</p>";
        sendHtml(exchange, 200, page("Connect fux to Spotify", body));
    }

    private void handleCallback(HttpExchange exchange, String method) throws IOException {
        String code;
        if ("POST".equals(method)) {
            code = parseParams(readBody(exchange)).get("code");
        } else {
            Map<String, String> query = parseParams(exchange.getRequestURI().getRawQuery());
            if (query.containsKey("error")) {
                sendHtml(exchange, 400, page("Authorization declined",
                    "<p>Spotify returned: <code>" + escapeHtml(query.get("error")) + "</code></p>"));
                return;
            }
            code = query.get("code");
        }

        if (code == null || code.trim().isEmpty()) {
            sendHtml(exchange, 400, page("Missing code",
                "<p>No authorization code was supplied. Start again at "
                    + "<a href=\"/api/spotify/login\">/api/spotify/login</a>.</p>"));
            return;
        }

        String error = auth().exchangeCode(code.trim());
        if (error != null) {
            sendHtml(exchange, 502, page("Could not connect", "<p>" + escapeHtml(error) + "</p>"));
            return;
        }

        sendHtml(exchange, 200, page("Spotify connected",
            "<p>fux can now read what you are playing. Switch the app to <strong>Music</strong> mode "
                + "and the LEDs will follow the beat.</p>"));
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        MusicSyncService service = effectEngine.getMusicSyncService();
        SpotifyAuthService auth = service.getAuth();

        JsonObject json = new JsonObject();
        json.addProperty("configured", auth.isConfigured());
        json.addProperty("authorized", auth.isAuthorized());
        json.addProperty("polling", service.isRunning());
        json.addProperty("redirectUri", auth.getRedirectUri());
        json.addProperty("loginUrl", "/api/spotify/login");
        if (auth.getLastError() != null) {
            json.addProperty("error", auth.getLastError());
        }

        NowPlaying track = service.getNowPlaying();
        if (track.hasTrack()) {
            JsonObject nowPlaying = new JsonObject();
            nowPlaying.addProperty("trackId", track.trackId);
            nowPlaying.addProperty("title", track.title);
            nowPlaying.addProperty("artist", track.artist);
            nowPlaying.addProperty("playing", track.playing);
            double bpm = service.getBeatClock().getKnownBpm();
            if (bpm > 0) {
                nowPlaying.addProperty("bpm", bpm);
            }
            nowPlaying.addProperty("offsetMs", service.getBeatClock().getOffsetMs());
            json.add("nowPlaying", nowPlaying);
        }

        sendJson(exchange, 200, json.toString());
    }

    // MARK: - Helpers

    private String readBody(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        InputStream in = exchange.getRequestBody();
        try {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        } finally {
            in.close();
        }
        return new String(buffer.toByteArray(), "UTF-8");
    }

    private Map<String, String> parseParams(String raw) {
        Map<String, String> params = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int split = pair.indexOf('=');
            if (split <= 0) {
                continue;
            }
            try {
                params.put(
                    URLDecoder.decode(pair.substring(0, split), "UTF-8"),
                    URLDecoder.decode(pair.substring(split + 1), "UTF-8")
                );
            } catch (Exception ignored) {
                // Skip undecodable pairs
            }
        }
        return params;
    }

    private String page(String title, String body) {
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>" + escapeHtml(title) + "</title><style>"
            + "body{font-family:-apple-system,system-ui,sans-serif;max-width:40rem;margin:3rem auto;"
            + "padding:0 1.25rem;line-height:1.5;color:#222}"
            + "h1{font-size:1.4rem}code,pre{background:#f4f4f6;border-radius:4px;padding:.15rem .3rem}"
            + "pre{padding:.75rem;overflow-x:auto}"
            + ".button{display:inline-block;background:#1db954;color:#fff;text-decoration:none;"
            + "padding:.6rem 1.1rem;border-radius:999px;font-weight:600}"
            + "input{padding:.5rem;margin-right:.5rem}button{padding:.5rem 1rem}"
            + ".muted{color:#777;font-size:.9rem}"
            + "</style></head><body><h1>" + escapeHtml(title) + "</h1>" + body + "</body></html>";
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
        exchange.close();
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
        exchange.close();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
