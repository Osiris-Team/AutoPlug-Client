package com.osiris.autoplug.client.mc.sharing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class UpnpPortMapperTest {
    private static final String SERVICE = "urn:schemas-upnp-org:service:WANIPConnection:1";

    @Test void explicitShareCreatesFiniteTcpMappingAndDeletesOnlyThatMapping() throws Exception {
        List<String> actions = new ArrayList<>(); List<Map<String, String>> arguments = new ArrayList<>();
        Map<String, String> created = new LinkedHashMap<>();
        UpnpPortMapper mapper = new UpnpPortMapper(() -> gateway(), (gateway, action, values) -> {
            actions.add(action); arguments.add(new LinkedHashMap<>(values));
            if (action.equals("GetExternalIPAddress")) return response(action, "<NewExternalIPAddress>8.8.8.8</NewExternalIPAddress>");
            if (action.equals("GetSpecificPortMappingEntry")) return created.isEmpty() ? fault(714) : response(action, ownedFields(created));
            if (action.equals("AddPortMapping")) created.putAll(values);
            return response(action, "");
        });
        assertTrue(actions.isEmpty());
        PortSharingService.Lease lease = mapper.share(25577);
        assertEquals("8.8.8.8:25577", lease.getJoinAddress());
        assertEquals("TCP", arguments.get(2).get("NewProtocol"));
        assertEquals("192.168.1.5", arguments.get(2).get("NewInternalClient"));
        assertEquals("3600", arguments.get(2).get("NewLeaseDuration"));
        assertEquals("25577", arguments.get(2).get("NewInternalPort"));
        lease.close(); lease.close();
        assertEquals(java.util.Arrays.asList("GetExternalIPAddress", "GetSpecificPortMappingEntry", "AddPortMapping", "GetSpecificPortMappingEntry", "DeletePortMapping"), actions);
        assertEquals(arguments.get(1), arguments.get(4));
    }

    @Test void rejectsPrivateCgnatAndNonliteralExternalAddressesBeforeOpeningAnything() throws Exception {
        for (String address : new String[]{"10.0.0.1", "100.64.0.1", "192.168.1.2", "172.16.0.1", "127.0.0.1", "0.0.0.0", "224.1.2.3", "2001:db8::1", "example.invalid", "192.0.2.1"}) {
            AtomicInteger calls = new AtomicInteger();
            UpnpPortMapper mapper = new UpnpPortMapper(() -> gateway(), (gateway, action, values) -> {
                calls.incrementAndGet(); return response(action, "<NewExternalIPAddress>" + address + "</NewExternalIPAddress>");
            });
            assertThrows(IOException.class, () -> mapper.share(25577), address);
            assertEquals(1, calls.get());
        }
    }

    @Test void neverOverwritesOrDeletesAnExistingMapping() throws Exception {
        List<String> actions = new ArrayList<>();
        UpnpPortMapper mapper = new UpnpPortMapper(() -> gateway(), (gateway, action, values) -> {
            actions.add(action);
            return response(action, action.equals("GetExternalIPAddress")
                    ? "<NewExternalIPAddress>8.8.8.8</NewExternalIPAddress>" : "<NewInternalClient>192.168.1.9</NewInternalClient>");
        });
        assertThrows(IOException.class, () -> mapper.share(25577));
        assertEquals(java.util.Arrays.asList("GetExternalIPAddress", "GetSpecificPortMappingEntry"), actions);
    }

    @Test void rejectedOrPermanentOnlyMappingNeverReturnsAJoinAddress() throws Exception {
        List<String> actions = new ArrayList<>();
        UpnpPortMapper mapper = new UpnpPortMapper(() -> gateway(), (gateway, action, values) -> {
            actions.add(action);
            if (action.equals("GetExternalIPAddress")) return response(action, "<NewExternalIPAddress>8.8.8.8</NewExternalIPAddress>");
            return fault(action.equals("GetSpecificPortMappingEntry") ? 714 : 725);
        });
        UpnpPortMapper.SoapFault error = assertThrows(UpnpPortMapper.SoapFault.class, () -> mapper.share(25577));
        assertEquals(725, error.getCode()); assertFalse(actions.contains("DeletePortMapping"));
    }

    @Test void cleanupFailureIsReportedAndCanBeRetried() throws Exception {
        AtomicInteger deletions = new AtomicInteger();
        Map<String, String> created = new LinkedHashMap<>();
        UpnpPortMapper mapper = new UpnpPortMapper(() -> gateway(), (gateway, action, values) -> {
            if (action.equals("GetExternalIPAddress")) return response(action, "<NewExternalIPAddress>8.8.8.8</NewExternalIPAddress>");
            if (action.equals("GetSpecificPortMappingEntry")) return created.isEmpty() ? fault(714) : response(action, ownedFields(created));
            if (action.equals("AddPortMapping")) created.putAll(values);
            if (action.equals("DeletePortMapping") && deletions.incrementAndGet() == 1) throw new IOException("fixture router timeout");
            return response(action, "");
        });
        PortSharingService.Lease lease = mapper.share(25577);
        assertThrows(IOException.class, lease::close); lease.close(); assertEquals(2, deletions.get());
    }

    @Test void parsesRelativeGatewayControlUrlsAndKnownServiceVersions() throws Exception {
        for (String service : new String[]{SERVICE, "urn:schemas-upnp-org:service:WANIPConnection:2", "urn:schemas-upnp-org:service:WANPPPConnection:1"}) {
            String xml = "<root xmlns='urn:schemas-upnp-org:device-1-0'><device><serviceList><service><serviceType>" + service
                    + "</serviceType><controlURL>/control</controlURL></service></serviceList></device></root>";
            UpnpPortMapper.Gateway parsed = UpnpPortMapper.gatewayFromDescription(xml, new URL("http://192.168.1.1:5000/root.xml"), "192.168.1.5");
            assertEquals("http://192.168.1.1:5000/control", parsed.getControlUrl().toString());
            assertEquals(service, parsed.getServiceType());
        }
    }

    @Test void rejectsDescriptorsRedirectingControlOutsideTheDiscoveredRouter() throws Exception {
        String prefix = "<root><service><serviceType>" + SERVICE + "</serviceType><controlURL>";
        for (String control : new String[]{"http://8.8.8.8/control", "http://192.168.1.9/control", "file:///tmp/control"}) {
            assertThrows(IOException.class, () -> UpnpPortMapper.gatewayFromDescription(prefix + control + "</controlURL></service></root>",
                    new URL("http://192.168.1.1/root.xml"), "192.168.1.5"));
        }
        assertThrows(IOException.class, () -> new UpnpPortMapper.Gateway(new URL("http://example.invalid/control"), SERVICE, "192.168.1.5"));
        assertThrows(IOException.class, () -> new UpnpPortMapper.Gateway(new URL("http://192.168.1.1/control"), SERVICE, "127.0.0.1"));
    }

    @Test void rejectsXmlEntitiesOversizedAndAmbiguousResponses() throws Exception {
        assertThrows(IOException.class, () -> UpnpPortMapper.parseResponse("<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///fixture-never-read'>]><a>&x;</a>"));
        assertThrows(IOException.class, () -> UpnpPortMapper.parseResponse(new String(new char[262145]).replace('\0', 'x')));
        assertThrows(IOException.class, () -> UpnpPortMapper.parseResponse("<root/>"));
        assertThrows(IOException.class, () -> UpnpPortMapper.parseResponse("<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><a/><b/></s:Body></s:Envelope>"));
    }

    @Test void soapEncodesValuesAndRejectsInjectedActionNames() throws Exception {
        Map<String, String> values = new LinkedHashMap<>(); values.put("NewPortMappingDescription", "a&<b>");
        String xml = UpnpPortMapper.envelope(SERVICE, "AddPortMapping", values);
        assertTrue(xml.contains("a&amp;&lt;b&gt;"));
        assertTrue(xml.contains("<u:AddPortMapping"));
        assertThrows(IOException.class, () -> UpnpPortMapper.envelope(SERVICE, "Add\"Bad", values));
    }

    @Test void parsesSsdpHeadersCaseInsensitivelyAndRejectsAmbiguousLocations() throws Exception {
        assertEquals("http://192.168.1.1/root.xml", UpnpPortMapper.SsdpDiscovery.location("HTTP/1.1 200 OK\r\nLoCaTiOn: http://192.168.1.1/root.xml\r\n\r\n"));
        assertThrows(IOException.class, () -> UpnpPortMapper.SsdpDiscovery.location("HTTP/1.1 200 OK\r\nLOCATION: one\r\nLOCATION: two\r\n"));
        assertThrows(IOException.class, () -> UpnpPortMapper.SsdpDiscovery.location("HTTP/1.1 404 Missing\r\nLOCATION: one\r\n"));
        assertThrows(IOException.class, () -> UpnpPortMapper.SsdpDiscovery.location("HTTP/1.1 200 OK\r\n"));
    }

    @Test void unexpectedSoapActionCannotCountAsSuccessfulPortOpening() throws Exception {
        UpnpPortMapper mapper = new UpnpPortMapper(() -> gateway(), (gateway, action, values) -> response("Unrelated", ""));
        assertThrows(IOException.class, () -> mapper.share(25577));
    }

    @Test void cleanupRefusesAMappingReplacedByAnotherApplication() throws Exception {
        Map<String, String> created = new LinkedHashMap<>(); AtomicInteger deleted = new AtomicInteger();
        UpnpPortMapper mapper = new UpnpPortMapper(() -> gateway(), (gateway, action, values) -> {
            if (action.equals("GetExternalIPAddress")) return response(action, "<NewExternalIPAddress>8.8.8.8</NewExternalIPAddress>");
            if (action.equals("GetSpecificPortMappingEntry")) return created.isEmpty() ? fault(714) : response(action, ownedFields(created));
            if (action.equals("AddPortMapping")) created.putAll(values);
            if (action.equals("DeletePortMapping")) deleted.incrementAndGet();
            return response(action, "");
        });
        PortSharingService.Lease lease = mapper.share(25577);
        created.put("NewPortMappingDescription", "another application");
        assertThrows(IOException.class, lease::close); assertEquals(0, deleted.get());
    }

    @Test void uncertainAddResponseChecksAndCleansItsOwnAppliedMapping() throws Exception {
        Map<String, String> created = new LinkedHashMap<>(); AtomicInteger deleted = new AtomicInteger();
        UpnpPortMapper mapper = new UpnpPortMapper(() -> gateway(), (gateway, action, values) -> {
            if (action.equals("GetExternalIPAddress")) return response(action, "<NewExternalIPAddress>8.8.8.8</NewExternalIPAddress>");
            if (action.equals("GetSpecificPortMappingEntry")) return created.isEmpty() ? fault(714) : response(action, ownedFields(created));
            if (action.equals("AddPortMapping")) { created.putAll(values); throw new IOException("fixture lost response"); }
            if (action.equals("DeletePortMapping")) deleted.incrementAndGet();
            return response(action, "");
        });
        assertThrows(IOException.class, () -> mapper.share(25577)); assertEquals(1, deleted.get());
    }

    @Test void leaseReportsExpiryWithoutClaimingIndefinitePortExposure() throws Exception {
        Map<String, String> created = new LinkedHashMap<>(); java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1000);
        UpnpPortMapper mapper = new UpnpPortMapper(() -> gateway(), (gateway, action, values) -> {
            if (action.equals("GetExternalIPAddress")) return response(action, "<NewExternalIPAddress>8.8.8.8</NewExternalIPAddress>");
            if (action.equals("GetSpecificPortMappingEntry")) return created.isEmpty() ? fault(714) : response(action, ownedFields(created));
            if (action.equals("AddPortMapping")) created.putAll(values);
            return response(action, "");
        }, clock::get);
        PortSharingService.Lease lease = mapper.share(25577); assertFalse(lease.isExpired());
        clock.addAndGet(3600000); assertTrue(lease.isExpired()); lease.close();
    }

    private static String ownedFields(Map<String, String> values) {
        return "<NewPortMappingDescription>" + values.get("NewPortMappingDescription") + "</NewPortMappingDescription>"
                + "<NewInternalClient>" + values.get("NewInternalClient") + "</NewInternalClient>"
                + "<NewInternalPort>" + values.get("NewInternalPort") + "</NewInternalPort>";
    }

    private static UpnpPortMapper.Gateway gateway() throws IOException {
        return new UpnpPortMapper.Gateway(new URL("http://192.168.1.1:5000/control"), SERVICE, "192.168.1.5");
    }

    private static String response(String action, String fields) {
        return "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><u:" + action + "Response xmlns:u='" + SERVICE + "'>"
                + fields + "</u:" + action + "Response></s:Body></s:Envelope>";
    }

    private static String fault(int code) {
        return "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><s:Fault><detail><UPnPError xmlns='urn:schemas-upnp-org:control-1-0'><errorCode>"
                + code + "</errorCode><errorDescription>fixture rejection</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>";
    }
}
