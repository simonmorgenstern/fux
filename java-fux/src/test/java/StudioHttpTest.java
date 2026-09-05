import junit.framework.TestCase;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class StudioHttpTest extends TestCase {
    public void testSaveLoadPreviewAndInvalidRequests() throws Exception {
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createTempDirectory("fux-http-test").toString());
        HttpServer server = null;
        try {
            EffectEngine engine = new EffectEngine(null);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/studio", new StudioHttpHandler(engine)); server.start();
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/studio";
            MusicArrangement show = new MusicArrangementTest().show();
            Gson gson = new Gson();
            String path = base + "/arrangements/" + show.trackId;
            assertEquals(404, call(path, "GET", null).code);
            assertEquals(200, call(path, "PUT", gson.toJson(show)).code);
            assertEquals("Test", gson.fromJson(call(path, "GET", null).body, MusicArrangement.class).title);
            assertEquals(400, call(path, "PUT", "{").code);
            show.clips.get(0).lengthBeats = 1000;
            assertEquals(400, call(path, "PUT", gson.toJson(show)).code);
            assertEquals(4.0, gson.fromJson(call(path, "GET", null).body, MusicArrangement.class).clips.get(0).lengthBeats, 0.0001);
            show = new MusicArrangementTest().show(); show.title = "Unsaved preview";
            JsonObject request = new JsonObject(); request.addProperty("sessionId", "test");
            request.add("arrangement", gson.toJsonTree(show)); request.addProperty("positionSeconds", 1.25);
            Result preview = call(base + "/preview", "POST", request.toString());
            assertEquals(200, preview.code);
            assertEquals(268, gson.fromJson(preview.body, JsonObject.class).getAsJsonArray("pixels").size());
            assertEquals("Test", engine.getArrangementStore().get(show.trackId).title);
            assertEquals(ControlMode.RANDOM, engine.getMode());
            assertEquals(400, call(base + "/preview", "POST", "null").code);
            assertEquals(405, call(path, "DELETE", null).code);
            assertEquals(268, gson.fromJson(call(base + "/layout", "GET", null).body, JsonArray.class).size());
            assertEquals(400, call(base + "/arrangements/not-a-track", "PUT", "{}").code);
        } finally {
            if (server != null) server.stop(0);
            System.setProperty("user.home", home);
        }
    }
    static class Result { int code; String body; Result(int c, String b) { code = c; body = b; } }
    static Result call(String url, String method, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(3000); c.setReadTimeout(3000);
        if (body != null) {
            c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int status = c.getResponseCode();
        try (InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return new Result(status, new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally { c.disconnect(); }
    }
}
