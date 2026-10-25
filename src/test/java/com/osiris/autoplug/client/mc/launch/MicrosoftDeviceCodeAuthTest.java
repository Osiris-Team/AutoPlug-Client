package com.osiris.autoplug.client.mc.launch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MicrosoftDeviceCodeAuthTest {
    @Test void requiresAnApplicationOwnedClientIdInsteadOfEmbeddingSomeoneElses() {
        assertThrows(IllegalArgumentException.class, () -> new MicrosoftDeviceCodeAuth("", new QueueTransport()));
    }

    @Test void deviceFlowPollsPendingAndSlowDownThenExchangesAllTokensInMemory() throws Exception {
        QueueTransport transport = new QueueTransport(); MutableClock clock = new MutableClock();
        transport.add("{\"device_code\":\"synthetic-device\",\"user_code\":\"TEST-CODE\",\"verification_uri\":\"https://microsoft.com/devicelogin\",\"expires_in\":300,\"interval\":1}");
        transport.add("{\"error\":\"authorization_pending\"}"); transport.add("{\"error\":\"slow_down\"}");
        successfulResponses(transport, true);
        MicrosoftDeviceCodeAuth auth = new MicrosoftDeviceCodeAuth("fixture-public-client", transport, clock, clock::advance);
        MicrosoftDeviceCodeAuth.DeviceAuthorization authorization = auth.begin(new Cancellation());
        assertEquals("TEST-CODE", authorization.getUserCode());
        assertFalse(authorization.toString().contains("TEST-CODE"));
        MicrosoftDeviceCodeAuth.AuthResult result = auth.poll(authorization, new Cancellation());
        assertEquals("FixturePlayer", result.getSession().getUsername());
        assertEquals("msa", result.getSession().getUserType());
        assertEquals("mc-fixture-token", result.getSession().getAccessToken());
        assertEquals("refresh-fixture", result.getRefreshToken());
        assertEquals(8, clock.elapsedSeconds()); // 1 pending + 1 slowdown + 6 final poll.
        assertTrue(transport.bodies.get(0).contains("client_id=fixture-public-client"));
        assertTrue(transport.bodies.stream().anyMatch(body -> body.contains("d=oauth-fixture-token")));
        assertTrue(transport.bodies.stream().anyMatch(body -> body.contains("XBL3.0 x=fixture-hash;xsts-fixture-token")));
        assertFalse(result.toString().contains("refresh-fixture"));
        assertEquals("Bearer mc-fixture-token", transport.authorizationHeaders.get(transport.authorizationHeaders.size() - 1));
    }

    @Test void rejectsAccountsWithoutMinecraftJavaOwnershipBeforeReturningASession() throws Exception {
        QueueTransport transport = new QueueTransport(); successfulResponses(transport, false);
        MicrosoftDeviceCodeAuth auth = new MicrosoftDeviceCodeAuth("fixture-public-client", transport);
        assertThrows(IOException.class, () -> auth.refresh("old-refresh", new Cancellation()));
        assertFalse(transport.urls.contains("https://api.minecraftservices.com/minecraft/profile"));
    }

    @Test void refreshKeepsTheExistingRefreshTokenWhenTheProviderDoesNotRotateIt() throws Exception {
        QueueTransport transport = new QueueTransport(); successfulResponses(transport, true);
        transport.responses.removeFirst();
        transport.responses.addFirst(json("{\"access_token\":\"oauth-fixture-token\"}"));
        MicrosoftDeviceCodeAuth.AuthResult result = new MicrosoftDeviceCodeAuth("fixture-public-client", transport)
                .refresh("previous-refresh", new Cancellation());
        assertEquals("previous-refresh", result.getRefreshToken());
        assertTrue(transport.bodies.get(0).contains("grant_type=refresh_token"));
    }

    @Test void declinedAuthorizationAndExpiryDoNotContinueToXbox() throws Exception {
        QueueTransport transport = new QueueTransport(); MutableClock clock = new MutableClock();
        transport.add("{\"device_code\":\"fixture\",\"user_code\":\"CODE\",\"verification_uri\":\"https://microsoft.com/devicelogin\",\"expires_in\":2,\"interval\":1}");
        transport.add("{\"error\":\"authorization_declined\"}");
        MicrosoftDeviceCodeAuth auth = new MicrosoftDeviceCodeAuth("fixture-client", transport, clock, clock::advance);
        MicrosoftDeviceCodeAuth.DeviceAuthorization authorization = auth.begin(new Cancellation());
        assertThrows(IOException.class, () -> auth.poll(authorization, new Cancellation()));
        assertEquals(2, transport.urls.size());
    }

    @Test void cancellationPreventsSendingTokensOrWaitingForThePollingInterval() throws Exception {
        QueueTransport transport = new QueueTransport(); Cancellation cancellation = new Cancellation(); cancellation.cancel();
        assertThrows(IOException.class, () -> new MicrosoftDeviceCodeAuth("fixture-client", transport).refresh("synthetic", cancellation));
        assertTrue(transport.urls.isEmpty());
    }

    private void successfulResponses(QueueTransport transport, boolean ownership) {
        transport.add("{\"access_token\":\"oauth-fixture-token\",\"refresh_token\":\"refresh-fixture\"}");
        transport.add("{\"Token\":\"xbox-fixture-token\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"fixture-hash\"}]}}");
        transport.add("{\"Token\":\"xsts-fixture-token\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"fixture-hash\",\"xid\":\"12345\"}]}}");
        transport.add("{\"access_token\":\"mc-fixture-token\",\"expires_in\":3600}");
        transport.add(ownership ? "{\"items\":[{\"name\":\"game_minecraft\"}]}" : "{\"items\":[]}");
        if (ownership) transport.add("{\"name\":\"FixturePlayer\",\"id\":\"0123456789abcdef0123456789abcdef\"}");
    }

    private JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }

    private final class QueueTransport implements LaunchTransport {
        final ArrayDeque<JsonObject> responses = new ArrayDeque<>();
        final List<String> bodies = new ArrayList<>(); final List<String> urls = new ArrayList<>();
        final List<String> authorizationHeaders = new ArrayList<>();
        void add(String text) { responses.add(MicrosoftDeviceCodeAuthTest.this.json(text)); }
        @Override public JsonObject json(String method, URI uri, Map<String, String> headers, byte[] body, Cancellation cancellation) throws IOException {
            cancellation.check(); urls.add(uri.toString());
            bodies.add(body == null ? "" : new String(body, StandardCharsets.UTF_8));
            authorizationHeaders.add(headers.get("Authorization"));
            if (responses.isEmpty()) throw new IOException("No synthetic authentication response");
            return responses.removeFirst();
        }
        @Override public void download(URI uri, Path path, Cancellation cancellation) throws IOException { throw new IOException("Authentication fixture never downloads"); }
    }

    private final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-17T00:00:00Z");
        void advance(long milliseconds) { now = now.plusMillis(milliseconds); }
        long elapsedSeconds() { return java.time.Duration.between(Instant.parse("2026-10-17T00:00:00Z"), now).getSeconds(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
