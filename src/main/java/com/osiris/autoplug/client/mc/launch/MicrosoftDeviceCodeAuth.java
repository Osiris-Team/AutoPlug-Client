package com.osiris.autoplug.client.mc.launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Microsoft consumer device flow -> Xbox Live -> XSTS -> Minecraft Services.
 * Requires the application's own registered public client ID and Minecraft API access.
 * No borrowed client ID, password collection, embedded secret or token file is used.
 */
public final class MicrosoftDeviceCodeAuth {
    private final String clientId;
    private final LaunchTransport transport;
    private final Clock clock;
    private final Sleeper sleeper;

    public MicrosoftDeviceCodeAuth(String clientId, LaunchTransport transport) {
        this(clientId, transport, Clock.systemUTC(), Thread::sleep);
    }

    public MicrosoftDeviceCodeAuth(String clientId, LaunchTransport transport, Clock clock, Sleeper sleeper) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Configure a legitimately registered Microsoft public application client ID first");
        }
        this.clientId = clientId.trim();
        this.transport = transport;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public DeviceAuthorization begin(Cancellation cancellation) throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("scope", "XboxLive.signin offline_access");
        JsonObject response = form("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", form, cancellation);
        rejectError(response);
        try {
            long expires = response.get("expires_in").getAsLong();
            long interval = response.has("interval") ? response.get("interval").getAsLong() : 5;
            if (expires <= 0 || interval <= 0) throw new IOException("Invalid Microsoft device-code expiration");
            URI verification = URI.create(response.get("verification_uri").getAsString());
            if (!"https".equalsIgnoreCase(verification.getScheme())) throw new IOException("Device-code verification requires HTTPS");
            return new DeviceAuthorization(response.get("device_code").getAsString(), response.get("user_code").getAsString(),
                    verification, clock.instant().plusSeconds(expires), interval);
        } catch (RuntimeException error) {
            throw new IOException("Invalid Microsoft device authorization response", error);
        }
    }

    public AuthResult poll(DeviceAuthorization authorization, Cancellation cancellation) throws IOException {
        long interval = authorization.intervalSeconds;
        while (clock.instant().isBefore(authorization.expiresAt)) {
            pause(interval, cancellation);
            if (!clock.instant().isBefore(authorization.expiresAt)) break;
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", clientId);
            form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            form.put("device_code", authorization.deviceCode);
            JsonObject response = form("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", form, cancellation);
            if (response.has("error")) {
                String error = response.get("error").getAsString();
                if ("authorization_pending".equals(error)) continue;
                if ("slow_down".equals(error)) { interval += 5; continue; }
                throw new IOException("Microsoft sign-in was declined or the device code expired; start sign-in again");
            }
            return exchange(response, null, cancellation);
        }
        throw new IOException("Microsoft device code expired; start sign-in again");
    }

    public AuthResult refresh(String refreshToken, Cancellation cancellation) throws IOException {
        if (refreshToken == null || refreshToken.isEmpty()) throw new IOException("No refresh token supplied");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("scope", "XboxLive.signin offline_access");
        JsonObject response = form("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", form, cancellation);
        rejectError(response);
        return exchange(response, refreshToken, cancellation);
    }

    private AuthResult exchange(JsonObject oauth, String previousRefreshToken, Cancellation cancellation) throws IOException {
        try {
            String microsoftToken = oauth.get("access_token").getAsString();
            String refresh = oauth.has("refresh_token") ? oauth.get("refresh_token").getAsString() : previousRefreshToken;
            JsonObject properties = new JsonObject();
            properties.addProperty("AuthMethod", "RPS");
            properties.addProperty("SiteName", "user.auth.xboxlive.com");
            properties.addProperty("RpsTicket", "d=" + microsoftToken);
            JsonObject xboxRequest = new JsonObject();
            xboxRequest.add("Properties", properties);
            xboxRequest.addProperty("RelyingParty", "http://auth.xboxlive.com");
            xboxRequest.addProperty("TokenType", "JWT");
            JsonObject xbox = post("https://user.auth.xboxlive.com/user/authenticate", xboxRequest, cancellation);
            rejectError(xbox);
            String userHash = xbox.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
            JsonObject xstsProperties = new JsonObject();
            xstsProperties.addProperty("SandboxId", "RETAIL");
            JsonArray tokens = new JsonArray(); tokens.add(xbox.get("Token").getAsString());
            xstsProperties.add("UserTokens", tokens);
            JsonObject xstsRequest = new JsonObject();
            xstsRequest.add("Properties", xstsProperties);
            xstsRequest.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            xstsRequest.addProperty("TokenType", "JWT");
            JsonObject xsts = post("https://xsts.auth.xboxlive.com/xsts/authorize", xstsRequest, cancellation);
            rejectError(xsts);
            JsonObject claims = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject();
            if (!userHash.equals(claims.get("uhs").getAsString())) throw new IOException("Xbox account changed during sign-in");
            JsonObject minecraftRequest = new JsonObject();
            minecraftRequest.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xsts.get("Token").getAsString());
            JsonObject minecraft = post("https://api.minecraftservices.com/authentication/login_with_xbox", minecraftRequest, cancellation);
            rejectError(minecraft);
            String accessToken = minecraft.get("access_token").getAsString();
            Map<String, String> headers = Collections.singletonMap("Authorization", "Bearer " + accessToken);
            JsonObject entitlements = transport.json("GET", URI.create("https://api.minecraftservices.com/entitlements/mcstore"), headers, null, cancellation);
            rejectError(entitlements);
            boolean ownsJava = false;
            for (com.google.gson.JsonElement item : entitlements.getAsJsonArray("items")) {
                String name = item.getAsJsonObject().get("name").getAsString();
                if (name.equals("game_minecraft") || name.equals("product_minecraft")) ownsJava = true;
            }
            if (!ownsJava) throw new IOException("The signed-in account has no Minecraft Java entitlement");
            JsonObject profile = transport.json("GET", URI.create("https://api.minecraftservices.com/minecraft/profile"), headers, null, cancellation);
            rejectError(profile);
            MinecraftSession session = new MinecraftSession(profile.get("name").getAsString(), profile.get("id").getAsString(),
                    accessToken, "msa", clientId, claims.has("xid") ? claims.get("xid").getAsString() : "");
            long expires = minecraft.has("expires_in") ? minecraft.get("expires_in").getAsLong() : 3600;
            return new AuthResult(session, refresh, clock.instant().plusSeconds(expires));
        } catch (RuntimeException error) {
            throw new IOException("Microsoft/Xbox/Minecraft sign-in returned incomplete account data", error);
        }
    }

    private JsonObject post(String url, JsonObject body, Cancellation cancellation) throws IOException {
        return transport.json("POST", URI.create(url), Collections.singletonMap("Content-Type", "application/json"),
                body.toString().getBytes(StandardCharsets.UTF_8), cancellation);
    }

    private JsonObject form(String url, Map<String, String> form, Cancellation cancellation) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return transport.json("POST", URI.create(url), Collections.singletonMap("Content-Type", "application/x-www-form-urlencoded"),
                body.toString().getBytes(StandardCharsets.UTF_8), cancellation);
    }

    private void rejectError(JsonObject response) throws IOException {
        if (response.has("error") || response.has("XErr")) throw new IOException("Microsoft/Xbox/Minecraft sign-in failed; verify application access and account eligibility");
    }

    private void pause(long seconds, Cancellation cancellation) throws IOException {
        long remaining = seconds * 1000;
        try {
            while (remaining > 0) {
                cancellation.check();
                long step = Math.min(100, remaining);
                sleeper.sleep(step);
                remaining -= step;
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            cancellation.check();
        }
    }

    public interface Sleeper { void sleep(long milliseconds) throws InterruptedException; }

    public final class DeviceAuthorization {
        private final String deviceCode;
        private final String userCode;
        private final URI verificationUri;
        private final Instant expiresAt;
        private final long intervalSeconds;

        private DeviceAuthorization(String deviceCode, String userCode, URI verificationUri, Instant expiresAt, long intervalSeconds) {
            this.deviceCode = deviceCode; this.userCode = userCode; this.verificationUri = verificationUri;
            this.expiresAt = expiresAt; this.intervalSeconds = intervalSeconds;
        }
        public String getUserCode() { return userCode; }
        public URI getVerificationUri() { return verificationUri; }
        public Instant getExpiresAt() { return expiresAt; }
        @Override public String toString() { return "DeviceAuthorization[code redacted]"; }
    }

    public final class AuthResult {
        private final MinecraftSession session;
        private final String refreshToken;
        private final Instant expiresAt;
        private AuthResult(MinecraftSession session, String refreshToken, Instant expiresAt) {
            this.session = session; this.refreshToken = refreshToken; this.expiresAt = expiresAt;
        }
        public MinecraftSession getSession() { return session; }
        public String getRefreshToken() { return refreshToken; }
        public Instant getExpiresAt() { return expiresAt; }
        @Override public String toString() { return "AuthResult[tokens redacted]"; }
    }
}
