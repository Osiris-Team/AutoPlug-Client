package com.osiris.autoplug.client.worlds.network;

import com.osiris.autoplug.client.worlds.VirtualWorldService;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.Test;
import org.w3c.dom.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UpnpPortMapperTest {
    @Test void realLoopbackSoapFlowAddsFiniteLeaseAndDeletesOnlyItsOwnMapping() throws Exception {
        try (GatewayFixture fixture = new GatewayFixture()) {
            VirtualWorldService.Mapping mapping = fixture.mapper(30, 10).map(25577);
            assertEquals("8.8.4.4:25577", mapping.joinAddress()); assertEquals("30", fixture.entry.get("NewLeaseDuration")); assertEquals("TCP", fixture.entry.get("NewProtocol")); assertEquals("127.0.0.1", fixture.entry.get("NewInternalClient"));
            assertTrue(fixture.entry.get("NewPortMappingDescription").startsWith("AutoPlug-")); assertEquals(1, fixture.adds.get());
            mapping.close(); mapping.close(); assertNull(fixture.entry); assertEquals(1, fixture.deletes.get()); assertThrows(IllegalStateException.class, mapping::joinAddress);
        }
    }
    @Test void cgnatAndPrivateWanAddressesDoNotCreateRouterMappings() throws Exception {
        try (GatewayFixture fixture = new GatewayFixture()) {
            for (String address : new String[]{"100.64.1.2", "192.168.1.1", "0.0.0.0", "999.1.1.1"}) { fixture.external = address; assertThrows(IOException.class, () -> fixture.mapper(30, 10).map(25577)); }
            assertEquals(0, fixture.adds.get()); assertEquals(0, fixture.deletes.get());
        }
    }
    @Test void existingOrReplacedMappingsAreNeverOverwrittenOrDeleted() throws Exception {
        try (GatewayFixture fixture = new GatewayFixture()) {
            fixture.entry = entry("Other-application"); assertThrows(IOException.class, () -> fixture.mapper(30, 10).map(25577)); assertEquals(0, fixture.adds.get()); assertEquals(0, fixture.deletes.get());
            fixture.entry = null; VirtualWorldService.Mapping mapping = fixture.mapper(30, 10).map(25577); fixture.entry = entry("Replacement");
            assertThrows(IOException.class, mapping::close); assertEquals("Replacement", fixture.entry.get("NewPortMappingDescription")); assertEquals(0, fixture.deletes.get());
        }
    }
    @Test void permanentLeaseIsRejectedAndOurUnexpectedMappingIsRemoved() throws Exception {
        try (GatewayFixture fixture = new GatewayFixture()) {
            fixture.permanentLease = true; assertThrows(IOException.class, () -> fixture.mapper(30, 10).map(25577)); assertEquals(1, fixture.adds.get()); assertEquals(1, fixture.deletes.get()); assertNull(fixture.entry);
        }
    }
    @Test void untrustedDescriptionCannotRedirectControlOrReadExternalEntities() throws Exception {
        try (GatewayFixture fixture = new GatewayFixture()) {
            fixture.control = "http://8.8.8.8/elsewhere"; assertThrows(IOException.class, () -> fixture.mapper(30, 10).map(25577)); assertEquals(0, fixture.soapRequests.get());
            fixture.control = "/control"; fixture.badXml = true; assertThrows(IOException.class, () -> fixture.mapper(30, 10).map(25577)); assertEquals(0, fixture.soapRequests.get());
        }
        assertThrows(Exception.class, () -> UpnpPortMapper.xml("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///not-an-owner-file'>]><x>&e;</x>".getBytes(StandardCharsets.UTF_8)));
    }
    @Test void renewalFailureBecomesVisibleAndFiniteMappingStillGetsCleanedUp() throws Exception {
        try (GatewayFixture fixture = new GatewayFixture()) {
            VirtualWorldService.Mapping mapping = fixture.mapper(3, 1).map(25577); fixture.rejectRenewal = true;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4); boolean failed = false;
            while (System.nanoTime() < deadline) { try { mapping.joinAddress(); } catch (IllegalStateException renewalFailed) { failed = true; break; } Thread.sleep(25); }
            assertTrue(failed, "The renewal failure must be visible instead of retaining a stale public address"); mapping.close(); assertEquals(1, fixture.deletes.get());
        }
    }
    @Test void emptyDiscoveryAndInvalidPortFailBeforeAnyControlRequest() {
        UpnpPortMapper mapper = new UpnpPortMapper(Collections::emptyList, 30, 10);
        assertThrows(IllegalArgumentException.class, () -> mapper.map(0)); assertTrue(assertThrows(IOException.class, () -> mapper.map(25577)).getMessage().contains("no compatible local UPnP gateway"));
    }
    private static Map<String,String> entry(String name) { Map<String,String> values = new LinkedHashMap<>(); values.put("NewInternalPort", "25577"); values.put("NewInternalClient", "127.0.0.1"); values.put("NewEnabled", "1"); values.put("NewPortMappingDescription", name); values.put("NewLeaseDuration", "30"); return values; }
    private static final class GatewayFixture implements AutoCloseable {
        final HttpServer server; final InetAddress loopback; final AtomicInteger adds = new AtomicInteger(), deletes = new AtomicInteger(), soapRequests = new AtomicInteger();
        volatile Map<String,String> entry; volatile String external = "8.8.4.4", control = "/control"; volatile boolean permanentLease, rejectRenewal, badXml;
        GatewayFixture() throws Exception {
            loopback = InetAddress.getByName("127.0.0.1"); server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
            server.createContext("/description", exchange -> reply(exchange, 200, badXml ? "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///not-an-owner-file'>]><x>&e;</x>" : "<root xmlns='urn:schemas-upnp-org:device-1-0'><device><serviceList><service><serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType><controlURL>" + control + "</controlURL></service></serviceList></device></root>"));
            server.createContext("/control", exchange -> {
                soapRequests.incrementAndGet();
                try {
                    String header = exchange.getRequestHeaders().getFirst("SOAPAction"); String action = header.substring(header.indexOf('#') + 1).replace("\"", "");
                    assertEquals("POST", exchange.getRequestMethod()); Document request = UpnpPortMapper.xml(exchange.getRequestBody().readAllBytes());
                    Map<String,String> result = new LinkedHashMap<>();
                    if (action.equals("GetExternalIPAddress")) result.put("NewExternalIPAddress", external);
                    else if (action.equals("GetSpecificPortMappingEntry")) { if (entry == null) { fault(exchange, 714); return; } result.putAll(entry); }
                    else if (action.equals("AddPortMapping")) {
                        if (rejectRenewal && adds.get() > 0) { fault(exchange, 718); return; }
                        Map<String,String> newEntry = new LinkedHashMap<>(); NodeList nodes = request.getElementsByTagNameNS("urn:schemas-upnp-org:service:WANIPConnection:1", action); NodeList arguments = nodes.item(0).getChildNodes();
                        for (int i = 0; i < arguments.getLength(); i++) if (arguments.item(i) instanceof Element) newEntry.put(arguments.item(i).getNodeName(), arguments.item(i).getTextContent());
                        if (permanentLease) newEntry.put("NewLeaseDuration", "0"); entry = newEntry; adds.incrementAndGet();
                    } else if (action.equals("DeletePortMapping")) { entry = null; deletes.incrementAndGet(); }
                    else throw new IOException("Unexpected fixture SOAP action: " + action);
                    StringBuilder response = new StringBuilder("<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><u:").append(action).append("Response xmlns:u='urn:schemas-upnp-org:service:WANIPConnection:1'>");
                    for (Map.Entry<String,String> value : result.entrySet()) response.append('<').append(value.getKey()).append('>').append(value.getValue()).append("</").append(value.getKey()).append('>');
                    reply(exchange, 200, response.append("</u:").append(action).append("Response></s:Body></s:Envelope>").toString());
                } catch (Throwable error) { reply(exchange, 500, "<error>" + error.getClass().getSimpleName() + "</error>"); }
            }); server.start();
        }
        UpnpPortMapper mapper(int lease, int renewal) { return new UpnpPortMapper(() -> Collections.singletonList(new UpnpPortMapper.Gateway(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/description"), loopback, loopback)), lease, renewal); }
        private static void fault(HttpExchange exchange, int code) throws IOException { reply(exchange, 500, "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><s:Fault><detail><UPnPError><errorCode>" + code + "</errorCode><errorDescription>Fixture failure</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"); }
        private static void reply(HttpExchange exchange, int status, String text) throws IOException { byte[] bytes = text.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", "text/xml"); exchange.sendResponseHeaders(status, bytes.length); try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); } }
        @Override public void close() { server.stop(0); }
    }
}
