package com.osiris.autoplug.client.launcher;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MicrosoftOAuthTest {
    static final String CLIENT = "00000000-1111-2222-3333-444444444444";
    static final class TestClock extends Clock {
        Instant instant = Instant.parse("2026-10-03T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
        void sleep(long millis) { instant = instant.plusMillis(millis); }
    }
    static final class Request {
        final String method, url, body; final Map<String,String> headers;
        Request(String method, URI url, Map<String,String> headers, byte[] body) { this.method = method; this.url = url.toString(); this.headers = headers; this.body = body == null ? "" : new String(body,StandardCharsets.UTF_8); }
    }
    static final class FixtureTransport implements MicrosoftOAuth.Transport {
        final Queue<MicrosoftOAuth.Response> responses = new ArrayDeque<>(); final List<Request> requests = new ArrayList<>();
        void add(int status, String json) { responses.add(new MicrosoftOAuth.Response(status, JsonParser.parseString(json).getAsJsonObject())); }
        @Override public MicrosoftOAuth.Response request(String method, URI uri, Map<String,String> headers, byte[] body) throws IOException {
            requests.add(new Request(method, uri, headers, body)); MicrosoftOAuth.Response result = responses.poll();
            if(result == null) throw new IOException("Unexpected authentication request"); return result;
        }
    }
    static void device(FixtureTransport transport) { transport.add(200,"{device_code:'FIXTURE-DEVICE-SECRET',user_code:'ABCD-EFGH',verification_uri:'https://www.microsoft.com/link',expires_in:900,interval:5}"); }
    static void completeChain(FixtureTransport transport) {
        transport.add(200,"{access_token:'FIXTURE-MS-ACCESS',refresh_token:'FIXTURE-MS-REFRESH',expires_in:3600}");
        transport.add(200,"{Token:'FIXTURE-XBOX-ACCESS'}");
        transport.add(200,"{Token:'FIXTURE-XSTS-ACCESS',DisplayClaims:{xui:[{uhs:'fixture-uhs',xid:'123456'}]}}");
        transport.add(200,"{access_token:'FIXTURE-MC-ACCESS',expires_in:7200}");
        transport.add(200,"{items:[{name:'game_minecraft'},{name:'product_minecraft'}]}");
        transport.add(200,"{id:'0123456789abcdef0123456789abcdef',name:'FixturePlayer'}");
    }
    @Test void devicePendingSlowDownAndWholeXboxMinecraftChainUseOfficialProtocols() throws Exception {
        FixtureTransport transport = new FixtureTransport(); device(transport); transport.add(400,"{error:'authorization_pending'}"); transport.add(400,"{error:'slow_down'}"); completeChain(transport);
        TestClock clock = new TestClock(); MicrosoftOAuth oauth = new MicrosoftOAuth(CLIENT,transport,clock,clock::sleep);
        MicrosoftOAuth.DeviceAuthorization device = oauth.beginDeviceAuthorization();
        assertEquals("ABCD-EFGH",device.getUserCode()); assertEquals(URI.create("https://www.microsoft.com/link"),device.getVerificationUri());
        assertFalse(device.toString().contains("FIXTURE-DEVICE"));
        LaunchAccount account = oauth.awaitMinecraftAccount(device,()->false);
        assertEquals("FixturePlayer",account.getName()); assertEquals("msa",account.getUserType()); assertEquals("123456",account.getXuid());
        assertEquals(CLIENT,account.getClientId()); assertEquals("FIXTURE-MC-ACCESS",account.getAccessToken());
        assertEquals("FIXTURE-MS-REFRESH",oauth.getRefreshToken()); assertEquals(Instant.parse("2026-10-03T02:00:20Z"),oauth.getAccessTokenExpiresAt());
        assertEquals(9,transport.requests.size()); Request authorization = transport.requests.get(0);
        assertEquals("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",authorization.url);
        assertTrue(authorization.body.contains("scope=XboxLive.signin+offline_access")); assertFalse(authorization.body.contains("client_secret"));
        assertTrue(transport.requests.get(1).body.contains("device_code=FIXTURE-DEVICE-SECRET"));
        JsonObject xbox = JsonParser.parseString(transport.requests.get(4).body).getAsJsonObject();
        assertEquals("d=FIXTURE-MS-ACCESS",xbox.getAsJsonObject("Properties").get("RpsTicket").getAsString());
        JsonObject xsts = JsonParser.parseString(transport.requests.get(5).body).getAsJsonObject();
        assertEquals("RETAIL",xsts.getAsJsonObject("Properties").get("SandboxId").getAsString());
        assertEquals("rp://api.minecraftservices.com/",xsts.get("RelyingParty").getAsString());
        JsonObject minecraft = JsonParser.parseString(transport.requests.get(6).body).getAsJsonObject();
        assertEquals("XBL3.0 x=fixture-uhs;FIXTURE-XSTS-ACCESS",minecraft.get("identityToken").getAsString());
        assertEquals("Bearer FIXTURE-MC-ACCESS",transport.requests.get(7).headers.get("Authorization"));
        assertTrue(transport.requests.get(7).url.endsWith("/entitlements/mcstore")); assertTrue(transport.requests.get(8).url.endsWith("/minecraft/profile"));
        assertFalse(oauth.toString().contains("FIXTURE")); oauth.clearSession(); assertNull(oauth.getRefreshToken()); assertNull(oauth.getAccessTokenExpiresAt());
    }
    @Test void cancellationDeclinedAndExpiryAreTerminalAndDoNotStartXboxRequests() throws Exception {
        FixtureTransport cancelled = new FixtureTransport(); device(cancelled); TestClock clock = new TestClock(); MicrosoftOAuth oauth = new MicrosoftOAuth(CLIENT,cancelled,clock,clock::sleep);
        MicrosoftOAuth.DeviceAuthorization authorization = oauth.beginDeviceAuthorization();
        assertThrows(MicrosoftOAuth.AuthenticationException.class,()->oauth.awaitMinecraftAccount(authorization,()->true)); assertEquals(1,cancelled.requests.size());
        FixtureTransport declined = new FixtureTransport(); device(declined); declined.add(400,"{error:'authorization_declined',error_description:'DO-NOT-LOG-TOKEN'}");
        MicrosoftOAuth other = new MicrosoftOAuth(CLIENT,declined,clock,clock::sleep); MicrosoftOAuth.DeviceAuthorization second = other.beginDeviceAuthorization();
        IOException error = assertThrows(IOException.class,()->other.awaitMinecraftAccount(second,()->false)); assertFalse(error.getMessage().contains("DO-NOT-LOG")); assertEquals(2,declined.requests.size());
        FixtureTransport expired = new FixtureTransport(); expired.add(200,"{device_code:'SECRET',user_code:'A',verification_uri:'https://www.microsoft.com/link',expires_in:1,interval:5}");
        MicrosoftOAuth timeout = new MicrosoftOAuth(CLIENT,expired,clock,clock::sleep); MicrosoftOAuth.DeviceAuthorization third = timeout.beginDeviceAuthorization();
        assertThrows(IOException.class,()->timeout.awaitMinecraftAccount(third,()->false)); assertEquals(1,expired.requests.size());
    }
    @Test void refreshRotatesTokenAndUsesNoDeviceFlow() throws Exception {
        FixtureTransport transport = new FixtureTransport(); completeChain(transport); TestClock clock = new TestClock(); MicrosoftOAuth oauth = new MicrosoftOAuth(CLIENT,transport,clock,clock::sleep);
        assertEquals("FixturePlayer",oauth.refresh("PREVIOUS-REFRESH").getName()); assertEquals("FIXTURE-MS-REFRESH",oauth.getRefreshToken());
        assertEquals(6,transport.requests.size()); assertTrue(transport.requests.get(0).url.endsWith("/token")); assertTrue(transport.requests.get(0).body.contains("grant_type=refresh_token"));
        assertTrue(transport.requests.get(0).body.contains("refresh_token=PREVIOUS-REFRESH")); assertFalse(transport.requests.get(0).body.contains("client_secret"));
    }
    @Test void accountWithoutEntitlementIsRejectedBeforeProfileLookup() throws Exception {
        FixtureTransport transport = new FixtureTransport(); transport.add(200,"{access_token:'MS'}"); transport.add(200,"{Token:'XBOX'}"); transport.add(200,"{Token:'XSTS',DisplayClaims:{xui:[{uhs:'hash'}]}}"); transport.add(200,"{access_token:'MC'}"); transport.add(200,"{items:[]}");
        TestClock clock = new TestClock(); MicrosoftOAuth oauth = new MicrosoftOAuth(CLIENT,transport,clock,clock::sleep);
        IOException error = assertThrows(IOException.class,()->oauth.refresh("REFRESH")); assertTrue(error.getMessage().contains("entitlement")); assertEquals(5,transport.requests.size()); assertNull(oauth.getRefreshToken());
    }
    @Test void xboxFamilyRestrictionHasActionableSecretFreeError() throws Exception {
        FixtureTransport transport = new FixtureTransport(); transport.add(200,"{access_token:'MS-TOKEN'}"); transport.add(200,"{Token:'XBOX-TOKEN'}"); transport.add(401,"{XErr:2148916238,Message:'SECRET-USER-DETAILS'}");
        TestClock clock = new TestClock(); MicrosoftOAuth oauth = new MicrosoftOAuth(CLIENT,transport,clock,clock::sleep);
        IOException error = assertThrows(IOException.class,()->oauth.refresh("REFRESH")); assertTrue(error.getMessage().contains("family")); assertFalse(error.getMessage().contains("SECRET"));
    }
    @Test void deviceFlowCannotCrossClientContextsAndHttpsTransportRejectsPlaintext() throws Exception {
        FixtureTransport transport = new FixtureTransport(); device(transport); TestClock clock = new TestClock(); MicrosoftOAuth first = new MicrosoftOAuth(CLIENT,transport,clock,clock::sleep);
        MicrosoftOAuth.DeviceAuthorization device = first.beginDeviceAuthorization(); MicrosoftOAuth other = new MicrosoftOAuth("another-client-id",transport,clock,clock::sleep);
        assertThrows(IOException.class,()->other.awaitMinecraftAccount(device,()->false)); assertEquals(1,transport.requests.size());
        assertThrows(IOException.class,()->new MicrosoftOAuth.HttpsTransport().request("POST",URI.create("http://localhost/auth"),Collections.emptyMap(),null));
    }
}
