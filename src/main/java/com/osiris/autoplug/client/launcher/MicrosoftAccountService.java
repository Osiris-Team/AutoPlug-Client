package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

/** Microsoft device authorization and the Xbox-to-Minecraft token exchange. No application ID is borrowed. */
public class MicrosoftAccountService {
    private static final String AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    private static final String SCOPES = "XboxLive.signin offline_access";
    private static final MediaType JSON = MediaType.parse("application/json");
    interface Transport { JsonObject execute(Request request, boolean allowError) throws IOException; }
    private final Transport transport;
    public MicrosoftAccountService() { this(MicrosoftAccountService::execute); }
    MicrosoftAccountService(Transport transport) { this.transport = transport; }

    public static final class DeviceLogin {
        public final String userCode;
        public final String verificationUri;
        public final String message;
        public final Instant expiresAt;
        private final String clientId;
        private final String deviceCode;
        private final int intervalSeconds;
        DeviceLogin(String clientId, JsonObject json) {
            this.clientId = clientId;
            userCode = json.get("user_code").getAsString();
            verificationUri = json.get("verification_uri").getAsString();
            message = json.has("message") ? json.get("message").getAsString() : "Open " + verificationUri + " and enter " + userCode;
            deviceCode = json.get("device_code").getAsString();
            intervalSeconds = json.has("interval") ? Math.min(60, Math.max(1, json.get("interval").getAsInt())) : 5;
            expiresAt = Instant.now().plusSeconds(json.get("expires_in").getAsLong());
        }
    }

    public DeviceLogin beginLogin(String clientId) throws IOException {
        if (clientId == null || !clientId.matches("[A-Za-z0-9-]{8,128}"))
            throw new IllegalArgumentException("Configure AutoPlug's registered Microsoft public application client ID first.");
        JsonObject response = post(AUTHORITY + "devicecode", new FormBody.Builder()
                .add("client_id", clientId).add("scope", SCOPES).build(), false);
        return new DeviceLogin(clientId, response);
    }

    /** Run on a worker thread. Interrupting the worker cancels polling. */
    public MinecraftAccount completeLogin(DeviceLogin login) throws IOException, InterruptedException {
        Objects.requireNonNull(login, "login");
        int interval = login.intervalSeconds;
        while (Instant.now().isBefore(login.expiresAt)) {
            Thread.sleep(interval * 1000L);
            JsonObject result = post(AUTHORITY + "token", new FormBody.Builder()
                    .add("client_id", login.clientId)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .add("device_code", login.deviceCode).build(), true);
            if (!result.has("error")) return exchange(login.clientId, result);
            String error = result.get("error").getAsString();
            if (error.equals("authorization_pending")) continue;
            if (error.equals("slow_down")) { interval = Math.min(60, interval + 5); continue; }
            throw authError(error);
        }
        throw new IOException("Microsoft sign-in expired. Start sign-in again.");
    }

    public MinecraftAccount refresh(MinecraftAccount account) throws IOException {
        if (account.offline) return account;
        if (account.refreshToken.isEmpty()) throw new IOException("Microsoft sign-in is required again.");
        JsonObject result = post(AUTHORITY + "token", new FormBody.Builder()
                .add("client_id", account.clientId).add("scope", SCOPES)
                .add("grant_type", "refresh_token").add("refresh_token", account.refreshToken).build(), false);
        if (!result.has("refresh_token")) result.addProperty("refresh_token", account.refreshToken);
        return exchange(account.clientId, result);
    }

    private MinecraftAccount exchange(String clientId, JsonObject microsoft) throws IOException {
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + microsoft.get("access_token").getAsString());
        JsonObject userRequest = new JsonObject();
        userRequest.add("Properties", properties);
        userRequest.addProperty("RelyingParty", "http://auth.xboxlive.com");
        userRequest.addProperty("TokenType", "JWT");
        JsonObject xbox = postJson("https://user.auth.xboxlive.com/user/authenticate", userRequest);

        JsonArray tokens = new JsonArray();
        tokens.add(xbox.get("Token").getAsString());
        JsonObject xstsProperties = new JsonObject();
        xstsProperties.addProperty("SandboxId", "RETAIL");
        xstsProperties.add("UserTokens", tokens);
        JsonObject xstsRequest = new JsonObject();
        xstsRequest.add("Properties", xstsProperties);
        xstsRequest.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsRequest.addProperty("TokenType", "JWT");
        JsonObject xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsRequest);
        JsonObject claims = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject();
        String userHash = claims.get("uhs").getAsString();
        String xuid = claims.has("xid") ? claims.get("xid").getAsString() : "";
        JsonObject minecraftRequest = new JsonObject();
        minecraftRequest.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xsts.get("Token").getAsString());
        JsonObject minecraft = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", minecraftRequest);
        String token = minecraft.get("access_token").getAsString();
        JsonObject entitlements = getAuthorized("https://api.minecraftservices.com/entitlements/mcstore", token);
        if (!entitlements.has("items") || entitlements.getAsJsonArray("items").size() == 0)
            throw new IOException("This Microsoft account has no Minecraft Java Edition entitlement.");
        JsonObject profile = getAuthorized("https://api.minecraftservices.com/minecraft/profile", token);
        return new MinecraftAccount(profile.get("name").getAsString(), profile.get("id").getAsString(), token,
                microsoft.has("refresh_token") ? microsoft.get("refresh_token").getAsString() : "", clientId, xuid, false,
                Instant.now().plusSeconds(minecraft.get("expires_in").getAsLong()));
    }

    private JsonObject postJson(String url, JsonObject body) throws IOException {
        return post(url, RequestBody.create(JSON, body.toString()), false);
    }
    private JsonObject post(String url, RequestBody body, boolean allowError) throws IOException {
        return transport.execute(new Request.Builder().url(url).header("Accept", "application/json")
                .header("x-xbl-contract-version", "1").post(body).build(), allowError);
    }
    private JsonObject getAuthorized(String url, String token) throws IOException {
        return transport.execute(new Request.Builder().url(url).header("Authorization", "Bearer " + token).build(), false);
    }
    private static JsonObject execute(Request request, boolean allowError) throws IOException {
        try (Response response = LauncherFiles.HTTP.newCall(request).execute()) {
            if (response.body() == null) throw new IOException("Empty authentication response.");
            JsonObject result;
            try { result = JsonParser.parseString(response.body().string()).getAsJsonObject(); }
            catch (RuntimeException e) { throw new IOException("Invalid authentication response (HTTP " + response.code() + ")."); }
            if (!response.isSuccessful() && !(allowError && result.has("error"))) {
                if (result.has("XErr")) {
                    long xboxError = result.get("XErr").getAsLong();
                    if (xboxError == 2148916233L) throw new IOException("Create an Xbox profile for this Microsoft account before signing in.");
                    if (xboxError == 2148916238L) throw new IOException("This child account needs an Xbox family organizer's permission.");
                }
                if (response.code() == 403 && request.url().host().equals("api.minecraftservices.com"))
                    throw new IOException("Minecraft services rejected access. Check that AutoPlug's Microsoft app registration is approved and the account is eligible.");
                if (result.has("error") && result.get("error").isJsonPrimitive()) throw authError(result.get("error").getAsString());
                throw new IOException("Authentication failed (HTTP " + response.code() + "). Check account ownership and Microsoft app registration.");
            }
            return result;
        }
    }
    private static IOException authError(String code) {
        if (code.equals("authorization_declined") || code.equals("access_denied")) return new IOException("Microsoft sign-in was cancelled.");
        if (code.equals("expired_token")) return new IOException("Microsoft sign-in expired. Try again.");
        if (code.equals("invalid_grant")) return new IOException("Microsoft sign-in is required again.");
        // Do not surface remote token-bearing response bodies in logs or dialogs.
        return new IOException("Microsoft authentication failed. Check the registered client ID and try signing in again.");
    }
}
