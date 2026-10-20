package com.osiris.autoplug.client.launcher;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Public-client OAuth device authorization, Xbox Live/XSTS and Minecraft Services. Tokens remain in memory. */
public final class MicrosoftOAuth {
    private static final String AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    private static final String SCOPES = "XboxLive.signin offline_access";
    public interface Transport { Response request(String method, URI uri, Map<String, String> headers, byte[] body) throws IOException; }
    public interface Sleeper { void sleep(long milliseconds) throws InterruptedException; }
    public static final class Response {
        public final int status; public final JsonObject json;
        public Response(int status, JsonObject json) { this.status = status; this.json = json; }
        @Override public String toString() { return "OAuthResponse{HTTP " + status + "}"; }
    }
    public static final class DeviceAuthorization {
        private final String userCode, deviceCode;
        private final URI verificationUri;
        private final Instant expiresAt;
        private final int intervalSeconds;
        private final String clientId;
        private DeviceAuthorization(String userCode, String deviceCode, URI uri, Instant expiresAt, int interval, String clientId) {
            this.userCode = userCode; this.deviceCode = deviceCode; this.verificationUri = uri;
            this.expiresAt = expiresAt; this.intervalSeconds = interval; this.clientId = clientId;
        }
        public String getUserCode() { return userCode; }
        public URI getVerificationUri() { return verificationUri; }
        public Instant getExpiresAt() { return expiresAt; }
        public int getIntervalSeconds() { return intervalSeconds; }
        @Override public String toString() { return "DeviceAuthorization{expires " + expiresAt + "}"; }
    }
    public static final class AuthenticationException extends IOException {
        public AuthenticationException(String reason) { super(reason); }
    }
    public static final class HttpsTransport implements Transport {
        @Override public Response request(String method, URI uri, Map<String, String> headers, byte[] body) throws IOException {
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null) throw new IOException("Authentication requires HTTPS");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method); for (Map.Entry<String, String> header : headers.entrySet()) connection.setRequestProperty(header.getKey(), header.getValue());
            try {
                if (body != null) { connection.setDoOutput(true); try (OutputStream out = connection.getOutputStream()) { out.write(body); } }
                int status = connection.getResponseCode();
                InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                JsonObject json = new JsonObject();
                if (stream != null) try (InputStream in = stream) {
                    try { json = JsonParser.parseString(new String(LauncherDownloads.readBounded(in, 1024 * 1024), StandardCharsets.UTF_8)).getAsJsonObject(); }
                    catch (RuntimeException e) { throw new AuthenticationException("Authentication service returned invalid JSON"); }
                }
                return new Response(status, json);
            } finally { connection.disconnect(); }
        }
    }
    private final String clientId;
    private final Transport transport;
    private final Clock clock;
    private final Sleeper sleeper;
    private volatile String refreshToken;
    private volatile Instant accessTokenExpiresAt;
    public MicrosoftOAuth(String clientId) { this(clientId, new HttpsTransport(), Clock.systemUTC(), Thread::sleep); }
    public MicrosoftOAuth(String clientId, Transport transport, Clock clock, Sleeper sleeper) {
        if (clientId == null || !clientId.matches("[A-Za-z0-9-]{8,128}")) throw new IllegalArgumentException("Configure your registered Microsoft OAuth public-client application ID");
        this.clientId = clientId; this.transport = Objects.requireNonNull(transport); this.clock = Objects.requireNonNull(clock); this.sleeper = Objects.requireNonNull(sleeper);
    }
    /** Call only in response to the account owner's explicit sign-in action. */
    public DeviceAuthorization beginDeviceAuthorization() throws IOException {
        Response response = form(AUTHORITY + "devicecode", pairs("client_id", clientId, "scope", SCOPES));
        requireSuccess(response, "Microsoft device authorization");
        try {
            String device = response.json.get("device_code").getAsString(); String user = response.json.get("user_code").getAsString();
            URI verification = URI.create(response.json.get("verification_uri").getAsString());
            if (!"https".equals(verification.getScheme())) throw new AuthenticationException("Invalid Microsoft verification URI");
            int expiry = response.json.get("expires_in").getAsInt(); if (expiry < 1 || expiry > 86400) throw new AuthenticationException("Invalid device authorization expiry");
            int interval = response.json.has("interval") ? Math.max(1, response.json.get("interval").getAsInt()) : 5;
            return new DeviceAuthorization(user, device, verification, clock.instant().plusSeconds(expiry), interval, clientId);
        } catch (RuntimeException e) { throw new AuthenticationException("Incomplete Microsoft device authorization response"); }
    }
    public LaunchAccount awaitMinecraftAccount(DeviceAuthorization authorization, BooleanSupplier cancelled) throws IOException, InterruptedException {
        Objects.requireNonNull(authorization); Objects.requireNonNull(cancelled);
        if (!clientId.equals(authorization.clientId)) throw new AuthenticationException("Device authorization belongs to another application");
        int interval = authorization.intervalSeconds;
        while (clock.instant().isBefore(authorization.expiresAt)) {
            waitCancellable(Math.min(interval * 1000L, Math.max(0, Duration.between(clock.instant(), authorization.expiresAt).toMillis())), cancelled);
            if (!clock.instant().isBefore(authorization.expiresAt)) break;
            Response response = form(AUTHORITY + "token", pairs("grant_type", "urn:ietf:params:oauth:grant-type:device_code", "client_id", clientId, "device_code", authorization.deviceCode));
            if (response.status >= 200 && response.status < 300) return exchangeMinecraft(response.json, null);
            String error = NativeLauncher.string(response.json, "error", "unknown_error");
            if ("authorization_pending".equals(error)) continue;
            if ("slow_down".equals(error)) { interval += 5; continue; }
            if ("authorization_declined".equals(error) || "access_denied".equals(error)) throw new AuthenticationException("Microsoft sign-in was declined");
            if ("expired_token".equals(error) || "bad_verification_code".equals(error)) throw new AuthenticationException("Microsoft device authorization expired or became invalid");
            throw new AuthenticationException("Microsoft token request failed (HTTP " + response.status + ")");
        }
        throw new AuthenticationException("Microsoft device authorization expired");
    }
    public LaunchAccount refresh(String token) throws IOException {
        if (token == null || token.isEmpty()) throw new AuthenticationException("No Microsoft refresh token is available");
        Response response = form(AUTHORITY + "token", pairs("grant_type", "refresh_token", "client_id", clientId, "refresh_token", token, "scope", SCOPES));
        requireSuccess(response, "Microsoft refresh"); return exchangeMinecraft(response.json, token);
    }
    /** Storage is the caller's responsibility. This class never writes credentials to disk. */
    public String getRefreshToken() { return refreshToken; }
    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public void clearSession() { refreshToken = null; accessTokenExpiresAt = null; }
    private LaunchAccount exchangeMinecraft(JsonObject microsoft, String previousRefresh) throws IOException {
        String microsoftToken = required(microsoft, "access_token", "Microsoft access token is missing");
        JsonObject userProperties = new JsonObject(); userProperties.addProperty("AuthMethod", "RPS");
        userProperties.addProperty("SiteName", "user.auth.xboxlive.com"); userProperties.addProperty("RpsTicket", "d=" + microsoftToken);
        JsonObject xboxBody = new JsonObject(); xboxBody.add("Properties", userProperties); xboxBody.addProperty("RelyingParty", "http://auth.xboxlive.com"); xboxBody.addProperty("TokenType", "JWT");
        Response xbox = jsonPost("https://user.auth.xboxlive.com/user/authenticate", xboxBody); requireSuccess(xbox, "Xbox Live user authentication");
        String xboxToken = required(xbox.json, "Token", "Xbox Live user token is missing");
        JsonObject xstsProperties = new JsonObject(); xstsProperties.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray(); userTokens.add(xboxToken); xstsProperties.add("UserTokens", userTokens);
        JsonObject xstsBody = new JsonObject(); xstsBody.add("Properties", xstsProperties); xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/"); xstsBody.addProperty("TokenType", "JWT");
        Response xsts = jsonPost("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody);
        if (xsts.status == 401 && xsts.json.has("XErr")) {
            long code = xsts.json.get("XErr").getAsLong();
            if (code == 2148916233L) throw new AuthenticationException("This Microsoft account has no Xbox profile; create one before signing in");
            if (code == 2148916238L) throw new AuthenticationException("This child account requires its Xbox family settings to allow access");
            throw new AuthenticationException("Xbox account settings prevent Minecraft authentication");
        }
        requireSuccess(xsts, "Xbox XSTS authorization");
        JsonObject claim;
        try { claim = xsts.json.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject(); }
        catch (RuntimeException e) { throw new AuthenticationException("Xbox XSTS user identity is missing"); }
        String userHash = required(claim, "uhs", "Xbox XSTS user hash is missing");
        JsonObject login = new JsonObject(); login.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + required(xsts.json, "Token", "Xbox XSTS token is missing"));
        Response minecraft = jsonPost("https://api.minecraftservices.com/authentication/login_with_xbox", login); requireSuccess(minecraft, "Minecraft authentication");
        String access = required(minecraft.json, "access_token", "Minecraft access token is missing");
        Response entitlements = get("https://api.minecraftservices.com/entitlements/mcstore", access); requireSuccess(entitlements, "Minecraft entitlement check");
        boolean ownsGame = false;
        if (entitlements.json.has("items")) for (JsonElement item : entitlements.json.getAsJsonArray("items")) {
            String name = NativeLauncher.string(item.getAsJsonObject(), "name", "");
            ownsGame |= name.equals("game_minecraft") || name.equals("product_minecraft");
        }
        if (!ownsGame) throw new AuthenticationException("This account does not have a Minecraft Java entitlement");
        Response profile = get("https://api.minecraftservices.com/minecraft/profile", access); requireSuccess(profile, "Minecraft profile lookup");
        String name = required(profile.json, "name", "Minecraft profile name is missing"); String uuid = required(profile.json, "id", "Minecraft profile UUID is missing");
        LaunchAccount account;
        try { account = LaunchAccount.microsoft(name, uuid, access, NativeLauncher.string(claim, "xid", ""), clientId); }
        catch (IllegalArgumentException e) { throw new AuthenticationException("Minecraft profile identity is invalid"); }
        refreshToken = NativeLauncher.string(microsoft, "refresh_token", previousRefresh);
        int seconds = minecraft.json.has("expires_in") ? minecraft.json.get("expires_in").getAsInt() : 3600;
        accessTokenExpiresAt = clock.instant().plusSeconds(Math.max(1, seconds));
        return account;
    }
    private void waitCancellable(long milliseconds, BooleanSupplier cancelled) throws InterruptedException, AuthenticationException {
        for (long remaining = milliseconds; remaining > 0; remaining -= 250) {
            if (cancelled.getAsBoolean()) throw new AuthenticationException("Microsoft sign-in cancelled");
            sleeper.sleep(Math.min(remaining, 250));
        }
        if (cancelled.getAsBoolean()) throw new AuthenticationException("Microsoft sign-in cancelled");
    }
    private Response form(String url, Map<String, String> parameters) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> item : parameters.entrySet()) { if (body.length() > 0) body.append('&'); body.append(URLEncoder.encode(item.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(item.getValue(), "UTF-8")); }
        return transport.request("POST", URI.create(url), pairs("Content-Type", "application/x-www-form-urlencoded", "Accept", "application/json"), body.toString().getBytes(StandardCharsets.UTF_8));
    }
    private Response jsonPost(String url, JsonObject json) throws IOException {
        Map<String,String> headers = pairs("Content-Type", "application/json", "Accept", "application/json");
        if (URI.create(url).getHost().endsWith(".xboxlive.com")) headers.put("x-xbl-contract-version", "1");
        return transport.request("POST", URI.create(url), headers, json.toString().getBytes(StandardCharsets.UTF_8));
    }
    private Response get(String url, String token) throws IOException { return transport.request("GET", URI.create(url), pairs("Authorization", "Bearer " + token, "Accept", "application/json"), null); }
    private static Map<String, String> pairs(String... strings) { Map<String, String> result = new LinkedHashMap<>(); for (int i = 0; i < strings.length; i += 2) result.put(strings[i], strings[i + 1]); return result; }
    private static void requireSuccess(Response response, String step) throws AuthenticationException { if (response.status < 200 || response.status >= 300) throw new AuthenticationException(step + " failed (HTTP " + response.status + ")"); }
    private static String required(JsonObject json, String key, String reason) throws AuthenticationException { if (!json.has(key) || json.get(key).isJsonNull() || json.get(key).getAsString().isEmpty()) throw new AuthenticationException(reason); return json.get(key).getAsString(); }
    @Override public String toString() { return "MicrosoftOAuth{public client}"; }
}
