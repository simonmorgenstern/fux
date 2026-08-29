import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Minimal blocking HTTP client built on {@link HttpURLConnection}.
 *
 * The Pi runs Java 8, so {@code java.net.http.HttpClient} is not available and
 * the project has no HTTP client dependency. Only what music mode needs: GET
 * with custom headers (for bearer tokens and ETags) and form POST (for the
 * OAuth token endpoint).
 */
public class SimpleHttp {

    public static class Response {
        public final int status;
        public final String body;
        public final String etag;
        /** Value of the {@code Retry-After} header in seconds, or 0 if absent. */
        public final int retryAfterSeconds;

        Response(int status, String body, String etag, int retryAfterSeconds) {
            this.status = status;
            this.body = body;
            this.etag = etag;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public boolean isOk() {
            return status >= 200 && status < 300;
        }
    }

    public static Response get(String url, Map<String, String> headers, int timeoutMs) throws Exception {
        return request("GET", url, headers, null, timeoutMs);
    }

    public static Response postForm(String url, Map<String, String> headers,
                                    Map<String, String> form, int timeoutMs) throws Exception {
        return request("POST", url, headers, encodeForm(form), timeoutMs);
    }

    private static Response request(String method, String url, Map<String, String> headers,
                                    byte[] body, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "fux-music-mode/1.0");
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    conn.setRequestProperty(header.getKey(), header.getValue());
                }
            }

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setFixedLengthStreamingMode(body.length);
                OutputStream out = conn.getOutputStream();
                try {
                    out.write(body);
                    out.flush();
                } finally {
                    out.close();
                }
            }

            int status = conn.getResponseCode();
            String payload = status == HttpURLConnection.HTTP_NO_CONTENT || status == HttpURLConnection.HTTP_NOT_MODIFIED
                ? ""
                : readStream(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            return new Response(status, payload, conn.getHeaderField("ETag"),
                parseRetryAfterSeconds(conn.getHeaderField("Retry-After"), 0));
        } finally {
            conn.disconnect();
        }
    }

    private static int parseRetryAfterSeconds(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] encodeForm(Map<String, String> form) throws Exception {
        StringBuilder sb = new StringBuilder();
        if (form != null) {
            for (Map.Entry<String, String> entry : form.entrySet()) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
            }
        }
        return sb.toString().getBytes("UTF-8");
    }

    private static String readStream(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
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
}
