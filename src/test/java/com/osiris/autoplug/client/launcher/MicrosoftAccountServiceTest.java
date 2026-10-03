package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.FormBody;
import okhttp3.Request;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MicrosoftAccountServiceTest {
    @TempDir Path temporary;
    private static final String CLIENT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String UUID = "00000000000000000000000000000001";

    @Test void deviceLoginExchangesAllTokensAndChecksOwnership() throws Exception {
        Exchange transport = new Exchange();
        MicrosoftAccountService service = new MicrosoftAccountService(transport);
        MicrosoftAccountService.DeviceLogin login = service.beginLogin(CLIENT_ID);
        assertEquals("ABCD-EFGH", login.userCode);
        assertEquals("https://www.microsoft.com/link", login.verificationUri);
        MinecraftAccount account = service.completeLogin(login);
        assertEquals("LicensedPlayer", account.username);
        assertEquals("minecraft-token", account.accessToken);
        assertEquals("rotated-refresh", account.refreshToken);
        assertEquals("12345", account.xuid);
        assertFalse(account.needsRefresh());
        assertEquals(Arrays.asList("/consumers/oauth2/v2.0/devicecode", "/consumers/oauth2/v2.0/token", "/user/authenticate", "/xsts/authorize", "/authentication/login_with_xbox", "/entitlements/mcstore", "/minecraft/profile"), transport.paths);
    }

    @Test void refreshUsesRegisteredClientAndRotatesRefreshToken() throws Exception {
        Exchange transport = new Exchange();
        MinecraftAccount expired = new MinecraftAccount("LicensedPlayer", UUID, "expired", "old-refresh", CLIENT_ID, "12345", false, Instant.EPOCH);
        assertTrue(expired.needsRefresh());
        MinecraftAccount account = new MicrosoftAccountService(transport).refresh(expired);
        assertEquals("rotated-refresh", account.refreshToken);
        assertEquals("minecraft-token", account.accessToken);
        assertFalse(transport.paths.contains("/consumers/oauth2/v2.0/devicecode"));
    }

    @Test void unlicensedAccountNeverBecomesLaunchIdentity() {
        Exchange transport = new Exchange(); transport.licensed = false;
        MinecraftAccount expired = new MinecraftAccount("LicensedPlayer", UUID, "expired", "old-refresh", CLIENT_ID, "12345", false, Instant.EPOCH);
        IOException error = assertThrows(IOException.class, () -> new MicrosoftAccountService(transport).refresh(expired));
        assertTrue(error.getMessage().contains("entitlement"));
        assertFalse(transport.paths.contains("/minecraft/profile"));
    }

    @Test void accountPersistenceAndDiagnosticOutputDoNotExposeTokens() throws Exception {
        MinecraftAccount account = new MinecraftAccount("LicensedPlayer", UUID, "secret-minecraft", "secret-refresh", CLIENT_ID, "12345", false, Instant.now().plusSeconds(3600));
        AccountStore store = new AccountStore(temporary.resolve("accounts.json"));
        store.save(account); store.save(account);
        assertEquals(1, store.list().size()); assertEquals("secret-refresh", store.list().get(0).refreshToken);
        PreparedLaunch command = new PreparedLaunch(temporary.resolve("java"), temporary,
                Arrays.asList("Main", "--accessToken", account.accessToken), "1.21.1", 21);
        assertFalse(command.toString().contains("secret")); assertFalse(account.toString().contains("secret"));
        assertTrue(command.command().contains("secret-minecraft"));
        store.remove(UUID); assertTrue(store.list().isEmpty());
    }

    @Test void offlineAccountsHaveDeterministicDistinctIdentities() {
        MinecraftAccount one = MinecraftAccount.offline("PlayerOne");
        assertEquals(one.uuid, MinecraftAccount.offline("PlayerOne").uuid);
        assertNotEquals(one.uuid, MinecraftAccount.offline("PlayerTwo").uuid);
        assertFalse(one.needsRefresh()); assertTrue(one.offline);
        assertThrows(IllegalArgumentException.class, () -> MinecraftAccount.offline("bad player"));
    }

    private static final class Exchange implements MicrosoftAccountService.Transport {
        final List<String> paths = new ArrayList<>(); boolean licensed = true;
        @Override public JsonObject execute(Request request, boolean allowError) throws IOException {
            String path = request.url().encodedPath(); paths.add(path);
            if (path.endsWith("/devicecode")) {
                FormBody form = (FormBody) request.body(); assertEquals(CLIENT_ID, form.value(0));
                return json("{\"user_code\":\"ABCD-EFGH\",\"device_code\":\"device-secret\",\"verification_uri\":\"https://www.microsoft.com/link\",\"expires_in\":120,\"interval\":1}");
            }
            if (path.endsWith("/token")) {
                FormBody form = (FormBody) request.body(); assertEquals(CLIENT_ID, form.value(0));
                return json("{\"access_token\":\"microsoft-token\",\"refresh_token\":\"rotated-refresh\"}");
            }
            if (path.equals("/user/authenticate")) {
                assertTrue(body(request).contains("d=microsoft-token"));
                return json("{\"Token\":\"xbox-token\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"user-hash\"}]}}");
            }
            if (path.equals("/xsts/authorize")) {
                assertTrue(body(request).contains("xbox-token")); assertTrue(body(request).contains("rp://api.minecraftservices.com/"));
                return json("{\"Token\":\"xsts-token\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"user-hash\",\"xid\":\"12345\"}]}}");
            }
            if (path.equals("/authentication/login_with_xbox")) {
                assertEquals("XBL3.0 x=user-hash;xsts-token", JsonParser.parseString(body(request)).getAsJsonObject().get("identityToken").getAsString());
                return json("{\"access_token\":\"minecraft-token\",\"expires_in\":86400}");
            }
            assertEquals("Bearer minecraft-token", request.header("Authorization"));
            if (path.equals("/entitlements/mcstore")) return json(licensed ? "{\"items\":[{\"name\":\"game_minecraft\"}]}" : "{\"items\":[]}");
            if (path.equals("/minecraft/profile")) return json("{\"id\":\"" + UUID + "\",\"name\":\"LicensedPlayer\"}");
            throw new AssertionError("Unexpected endpoint " + path);
        }
        private static String body(Request request) throws IOException { Buffer buffer = new Buffer(); request.body().writeTo(buffer); return buffer.readUtf8(); }
        private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    }
}
