package com.osiris.autoplug.client.worlds;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import static org.junit.jupiter.api.Assertions.*;

class UpnpSharingServiceTest {
    @Test void explicitSharingRelaysLoopbackTrafficAndRemovesOnlyItsOwnedMapping() throws Exception {
        FakeGateway gateway = new FakeGateway();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket target = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Future<?> echo = executor.submit(() -> {
                try (Socket socket = target.accept()) {
                    socket.setSoTimeout(3000);
                    int value = socket.getInputStream().read(); socket.getOutputStream().write(value + 1); socket.getOutputStream().flush();
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            SharingService.Lease lease = new UpnpSharingService(() -> gateway).open(target.getLocalPort());
            try {
                assertEquals(1, gateway.additions.get());
                assertEquals("203.0.113.10:" + gateway.port, lease.address());
                try (Socket client = new Socket("127.0.0.1", gateway.port)) {
                    client.setSoTimeout(3000); client.getOutputStream().write(41); client.getOutputStream().flush();
                    assertEquals(42, client.getInputStream().read());
                }
                echo.get(5, TimeUnit.SECONDS);
            } finally { lease.close(); }
            lease.close();
            assertEquals(1, gateway.removals.get());
            assertThrows(IOException.class, () -> new Socket("127.0.0.1", gateway.port));
        } finally { executor.shutdownNow(); }
    }
    @Test void mappingFailureClosesForwardingListener() throws Exception {
        FakeGateway gateway = new FakeGateway() {
            @Override public void addMapping(int external, int internal, String description, int lease) throws IOException {
                port = internal; throw new IOException("Fixture denies mapping");
            }
        };
        assertThrows(IOException.class, () -> new UpnpSharingService(() -> gateway).open(25565));
        assertTrue(gateway.port > 0);
        assertThrows(IOException.class, () -> new Socket("127.0.0.1", gateway.port));
        assertEquals(0, gateway.removals.get());
    }
    @Test void detectsDoubleNatBeforeOpeningMapping() throws Exception {
        FakeGateway gateway = new FakeGateway() { @Override public String externalAddress() { return "100.64.0.2"; } };
        assertThrows(IOException.class, () -> new UpnpSharingService(() -> gateway).open(25565));
        assertEquals(0, gateway.additions.get());
    }
    @Test void gatewayXmlCannotReadExternalEntities() {
        byte[] payload = "<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///not-readable'>]><x>&secret;</x>".getBytes(StandardCharsets.UTF_8);
        assertThrows(Exception.class, () -> UpnpSharingService.xml(payload));
    }
    @Test void soapFixtureChecksOwnershipBeforeOverwritingOrDeletingMapping() throws Exception {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> owner = new AtomicReference<>();
        AtomicInteger deletes = new AtomicInteger();
        http.createContext("/control", exchange -> {
            String action = exchange.getRequestHeaders().getFirst("SOAPAction");
            String response; int code = 200;
            if (action.contains("GetExternalIPAddress")) response = "<NewExternalIPAddress>203.0.113.10</NewExternalIPAddress>";
            else if (action.contains("GetSpecificPortMappingEntry")) {
                if (owner.get() == null) { code = 500; response = "<errorCode>714</errorCode><errorDescription>NoSuchEntryInArray</errorDescription>"; }
                else response = "<NewInternalPort>25565</NewInternalPort><NewInternalClient>127.0.0.1</NewInternalClient><NewPortMappingDescription>" + owner.get() + "</NewPortMappingDescription>";
            } else if (action.contains("AddPortMapping")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(body.contains("<NewLeaseDuration>3600</NewLeaseDuration>"));
                assertTrue(body.contains("<NewInternalClient>127.0.0.1</NewInternalClient>"));
                owner.set("owned"); response = "<AddPortMappingResponse/>";
            } else { deletes.incrementAndGet(); owner.set(null); response = "<DeletePortMappingResponse/>"; }
            byte[] bytes = ("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>" + response + "</s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
        });
        http.start();
        try {
            UpnpSharingService.Gateway gateway = new UpnpSharingService.SoapGateway(new URL("http://127.0.0.1:" + http.getAddress().getPort() + "/control"),
                    "urn:schemas-upnp-org:service:WANIPConnection:1", InetAddress.getByName("127.0.0.1"));
            assertEquals("203.0.113.10", gateway.externalAddress());
            gateway.addMapping(25565, 25565, "owned", 3600);
            gateway.addMapping(25565, 25565, "owned", 3600);
            owner.set("another application");
            assertThrows(IOException.class, () -> gateway.addMapping(25565, 25565, "owned", 3600));
            gateway.removeMapping(25565, 25565, "owned");
            assertEquals(0, deletes.get());
            owner.set("owned"); gateway.removeMapping(25565, 25565, "owned");
            assertEquals(1, deletes.get());
        } finally { http.stop(0); }
    }
    private static class FakeGateway implements UpnpSharingService.Gateway {
        int port; String ownedDescription;
        final AtomicInteger additions = new AtomicInteger(), removals = new AtomicInteger();
        public InetAddress localAddress() { return InetAddress.getLoopbackAddress(); }
        public String externalAddress() { return "203.0.113.10"; }
        public void addMapping(int external, int internal, String description, int lease) throws IOException {
            assertEquals(external, internal); assertEquals(3600, lease);
            port = internal; ownedDescription = description; additions.incrementAndGet();
        }
        public void removeMapping(int external, int internal, String description) {
            if (ownedDescription == null) return;
            assertEquals(port, internal); assertEquals(ownedDescription, description); removals.incrementAndGet();
        }
    }
}
