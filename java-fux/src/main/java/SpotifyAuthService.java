import com.google.gson.JsonObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Spotify Authorization Code flow, done once from a browser on the LAN.
 *
 * Credentials come from {@code ~/.fux/spotify.json} (or the SPOTIFY_CLIENT_ID /
 * SPOTIFY_CLIENT_SECRET / SPOTIFY_REDIRECT_URI environment variables):
 *
 * <pre>
 * {
 *   "clientId":     "…",
 *   "clientSecret": "…",
 *   "redirectUri":  "http://127.0.0.1:8080/callback"
 * }
 * </pre>
 *
 * The refresh token is stored in {@code ~/.fux/spotify_tokens.json} and the
 * access token is refreshed automatically, so the browser step is needed only
 * once. Note that Spotify only accepts HTTPS or loopback redirect URIs, which
 * is why {@link SpotifyAuthHttpHandler} also offers a paste-the-code form:
 * the redirect lands on 127.0.0.1 in the user's browser, not on the Pi.
 */
public class SpotifyAuthService {

    private static final String AUTHORIZE_URL = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SCOPES = "user-read-currently-playing user-read-playback-state";
    /**
     * Kept in the shape of Spotify's own documented example
     * ({@code http://127.0.0.1:8000/callback}): the dashboard's redirect-URI
     * validator is fussy about anything more elaborate, and since the redirect
     * is completed by pasting the code rather than by this server answering it,
     * the path carries no meaning anyway.
     */
    private static final String DEFAULT_REDIRECT_URI = "http://127.0.0.1:8080/callback";
    private static final int TIMEOUT_MS = 10000;
    /** Refresh a little early so a poll never races the expiry. */
    private static final long EXPIRY_MARGIN_MS = 60000;

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    private String accessToken;
    private String refreshToken;
    private long expiresAtMs;
    private String lastError;

    public SpotifyAuthService() {
        JsonObject config = FuxConfig.readJson(FuxConfig.file("spotify.json"));
        this.clientId = FuxConfig.setting(config, "clientId", "SPOTIFY_CLIENT_ID", null);
        this.clientSecret = FuxConfig.setting(config, "clientSecret", "SPOTIFY_CLIENT_SECRET", null);
        this.redirectUri = FuxConfig.setting(config, "redirectUri", "SPOTIFY_REDIRECT_URI", DEFAULT_REDIRECT_URI);
        loadTokens();
    }

    /** True once a client id and secret are present. */
    public boolean isConfigured() {
        return clientId != null && clientSecret != null;
    }

    /** True once the browser login has been completed at least once. */
    public synchronized boolean isAuthorized() {
        return refreshToken != null;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public synchronized String getLastError() {
        return lastError;
    }

    /** URL the user opens in a browser to grant access. */
    public String getAuthorizeUrl(String state) {
        return AUTHORIZE_URL
            + "?client_id=" + SimpleHttp.urlEncode(clientId)
            + "&response_type=code"
            + "&redirect_uri=" + SimpleHttp.urlEncode(redirectUri)
            + "&scope=" + SimpleHttp.urlEncode(SCOPES)
            + "&state=" + SimpleHttp.urlEncode(state);
    }

    /**
     * Exchanges an authorization code for tokens and persists the refresh token.
     *
     * @return null on success, otherwise a human-readable error
     */
    public synchronized String exchangeCode(String code) {
        if (!isConfigured()) {
            return "Spotify client id/secret not configured (see ~/.fux/spotify.json)";
        }
        Map<String, String> form = new HashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);

        try {
            SimpleHttp.Response response = SimpleHttp.postForm(TOKEN_URL, basicAuthHeaders(), form, TIMEOUT_MS);
            if (!response.isOk()) {
                lastError = "Token exchange failed (" + response.status + "): " + response.body;
                return lastError;
            }
            applyTokenResponse(response.body);
            saveTokens();
            lastError = null;
            System.out.println("Spotify authorization complete");
            return null;
        } catch (Exception e) {
            lastError = "Token exchange error: " + e.getMessage();
            return lastError;
        }
    }

    /**
     * A valid access token, refreshing if needed.
     *
     * @return null when not configured, not authorized, or the refresh failed
     */
    public synchronized String getAccessToken() {
        if (!isConfigured() || refreshToken == null) {
            return null;
        }
        if (accessToken != null && System.currentTimeMillis() < expiresAtMs - EXPIRY_MARGIN_MS) {
            return accessToken;
        }
        return refreshAccessToken();
    }

    /** Forces a refresh, used after a 401 from the API. */
    public synchronized String refreshAccessToken() {
        if (!isConfigured() || refreshToken == null) {
            return null;
        }
        Map<String, String> form = new HashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);

        try {
            SimpleHttp.Response response = SimpleHttp.postForm(TOKEN_URL, basicAuthHeaders(), form, TIMEOUT_MS);
            if (!response.isOk()) {
                lastError = "Token refresh failed (" + response.status + "): " + response.body;
                System.err.println(lastError);
                if (response.status == 400) {
                    // Refresh token revoked — force a new browser login.
                    refreshToken = null;
                    accessToken = null;
                    saveTokens();
                }
                return null;
            }
            applyTokenResponse(response.body);
            saveTokens();
            lastError = null;
            return accessToken;
        } catch (Exception e) {
            lastError = "Token refresh error: " + e.getMessage();
            System.err.println(lastError);
            return null;
        }
    }

    /** Drops stored tokens, requiring a fresh browser login. */
    public synchronized void logout() {
        accessToken = null;
        refreshToken = null;
        expiresAtMs = 0;
        saveTokens();
    }

    private Map<String, String> basicAuthHeaders() {
        String credentials = clientId + ":" + clientSecret;
        String encoded = java.util.Base64.getEncoder().encodeToString(
            credentials.getBytes(java.nio.charset.Charset.forName("UTF-8")));
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Basic " + encoded);
        return headers;
    }

    private void applyTokenResponse(String body) {
        JsonObject json = new com.google.gson.Gson().fromJson(body, JsonObject.class);
        if (json == null) {
            return;
        }
        if (json.has("access_token")) {
            accessToken = json.get("access_token").getAsString();
        }
        // Spotify omits refresh_token on refresh responses; keep the existing one.
        if (json.has("refresh_token") && !json.get("refresh_token").isJsonNull()) {
            refreshToken = json.get("refresh_token").getAsString();
        }
        int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600;
        expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L;
    }

    private File tokenFile() {
        return FuxConfig.file("spotify_tokens.json");
    }

    private void loadTokens() {
        JsonObject stored = FuxConfig.readJson(tokenFile());
        if (stored == null) {
            return;
        }
        if (stored.has("refreshToken") && !stored.get("refreshToken").isJsonNull()) {
            refreshToken = stored.get("refreshToken").getAsString();
        }
        if (stored.has("accessToken") && !stored.get("accessToken").isJsonNull()) {
            accessToken = stored.get("accessToken").getAsString();
        }
        if (stored.has("expiresAt")) {
            expiresAtMs = stored.get("expiresAt").getAsLong();
        }
        if (refreshToken != null) {
            System.out.println("Loaded Spotify refresh token from " + tokenFile());
        }
    }

    private void saveTokens() {
        JsonObject json = new JsonObject();
        json.addProperty("refreshToken", refreshToken);
        json.addProperty("accessToken", accessToken);
        json.addProperty("expiresAt", expiresAtMs);
        FuxConfig.writeJson(tokenFile(), json);
    }
}
