package com.osiris.autoplug.client.mc.sharing;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Clean-room SSDP/IGD SOAP adapter. No network activity happens until share() is called. */
public final class UpnpPortMapper implements PortSharingService {
    private static final int MAX_XML_BYTES = 256 * 1024;
    public interface Discovery { Gateway discover() throws IOException; }
    public interface SoapTransport {
        String call(Gateway gateway, String action, Map<String, String> arguments) throws IOException;
    }

    public static final class Gateway {
        private final URL controlUrl;
        private final String serviceType;
        private final String localAddress;

        public Gateway(URL controlUrl, String serviceType, String localAddress) throws IOException {
            requireLocalUrl(controlUrl);
            if (!isServiceType(serviceType)) throw new IOException("Unsupported IGD service");
            if (!isPrivateIpv4(localAddress) || localAddress.startsWith("127.")) throw new IOException("A LAN IPv4 address is required");
            this.controlUrl = controlUrl;
            this.serviceType = serviceType;
            this.localAddress = localAddress;
        }
        public URL getControlUrl() { return controlUrl; }
        public String getServiceType() { return serviceType; }
        public String getLocalAddress() { return localAddress; }
    }

    public static final class SoapFault extends IOException {
        private final int code;
        public SoapFault(int code, String message) { super("UPnP error " + code + ": " + message); this.code = code; }
        public int getCode() { return code; }
    }

    private final Discovery discovery;
    private final SoapTransport transport;
    private final java.util.function.LongSupplier clock;

    public UpnpPortMapper() {
        HttpTransport http = new HttpTransport();
        this.discovery = new SsdpDiscovery(http);
        this.transport = http;
        this.clock = System::currentTimeMillis;
    }

    public UpnpPortMapper(Discovery discovery, SoapTransport transport) {
        this(discovery, transport, System::currentTimeMillis);
    }

    public UpnpPortMapper(Discovery discovery, SoapTransport transport, java.util.function.LongSupplier clock) {
        this.discovery = discovery;
        this.transport = transport;
        this.clock = clock;
    }

    @Override public Lease share(int localPort) throws IOException {
        if (localPort < 1 || localPort > 65535) throw new IOException("Invalid port");
        Gateway gateway = discovery.discover();
        String external = value(call(gateway, "GetExternalIPAddress", new LinkedHashMap<>()), "NewExternalIPAddress");
        if (!isPublicIpv4(external)) {
            throw new IOException("Router has no public IPv4 address (possibly double NAT or CGNAT)");
        }
        Map<String, String> identity = mappingIdentity(localPort);
        // Never overwrite/delete a mapping another application already owns.
        try {
            call(gateway, "GetSpecificPortMappingEntry", identity);
            throw new IOException("The requested external TCP port already has a mapping");
        } catch (SoapFault fault) {
            if (fault.getCode() != 714) throw fault; // NoSuchEntryInArray is the only free-port result.
        }
        Map<String, String> arguments = new LinkedHashMap<>(identity);
        arguments.put("NewInternalPort", Integer.toString(localPort));
        arguments.put("NewInternalClient", gateway.localAddress);
        arguments.put("NewEnabled", "1");
        arguments.put("NewPortMappingDescription", "AutoPlug-" + UUID.randomUUID());
        // A finite lease bounds exposure after a crash. Permanent-only routers use visible fallback.
        arguments.put("NewLeaseDuration", "3600");
        try { call(gateway, "AddPortMapping", arguments); }
        catch (IOException error) {
            // A timed-out HTTP reply does not prove that the router did not apply the request.
            try { deleteOwnedMapping(gateway, identity, arguments); }
            catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
        long expiresAt = clock.getAsLong() + 3600000;
        return new Lease() {
            private final AtomicBoolean removed = new AtomicBoolean();
            @Override public String getJoinAddress() { return external + ":" + localPort; }
            @Override public boolean isExpired() { return clock.getAsLong() >= expiresAt; }
            @Override public void close() throws IOException {
                if (removed.compareAndSet(false, true)) {
                    try { deleteOwnedMapping(gateway, identity, arguments); }
                    catch (IOException error) { removed.set(false); throw error; }
                }
            }
        };
    }

    private void deleteOwnedMapping(Gateway gateway, Map<String, String> identity,
                                    Map<String, String> created) throws IOException {
        Map<String, String> current;
        try { current = call(gateway, "GetSpecificPortMappingEntry", identity); }
        catch (SoapFault absent) { if (absent.getCode() == 714) return; else throw absent; }
        for (String field : new String[]{"NewPortMappingDescription", "NewInternalClient", "NewInternalPort"}) {
            if (!created.get(field).equals(current.get(field))) {
                throw new IOException("Router mapping ownership changed; refusing to delete another mapping");
            }
        }
        call(gateway, "DeletePortMapping", identity);
    }

    private Map<String, String> call(Gateway gateway, String action, Map<String, String> arguments) throws IOException {
        return parseResponse(transport.call(gateway, action, arguments), action);
    }

    private static Map<String, String> mappingIdentity(int port) {
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put("NewRemoteHost", "");
        arguments.put("NewExternalPort", Integer.toString(port));
        arguments.put("NewProtocol", "TCP");
        return arguments;
    }

    static String envelope(String service, String action, Map<String, String> arguments) throws IOException {
        if (!isServiceType(service) || !action.matches("[A-Za-z][A-Za-z0-9]*")) throw new IOException("Invalid SOAP action");
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:")
                .append(action).append(" xmlns:u=\"").append(service).append("\">");
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            if (!entry.getKey().matches("[A-Za-z][A-Za-z0-9]*")) throw new IOException("Invalid SOAP argument");
            xml.append('<').append(entry.getKey()).append('>').append(escape(entry.getValue()))
                    .append("</").append(entry.getKey()).append('>');
        }
        return xml.append("</u:").append(action).append("></s:Body></s:Envelope>").toString();
    }

    static Map<String, String> parseResponse(String xml) throws IOException {
        return parseResponse(xml, null);
    }

    private static Map<String, String> parseResponse(String xml, String expectedAction) throws IOException {
        Document document = parseXml(xml);
        NodeList faults = document.getElementsByTagNameNS("*", "Fault");
        if (faults.getLength() > 0) {
            String code = text(document.getDocumentElement(), "errorCode");
            String description = text(document.getDocumentElement(), "errorDescription");
            int number;
            try { number = Integer.parseInt(code); } catch (NumberFormatException error) { number = -1; }
            throw new SoapFault(number, description == null ? "Gateway rejected the request" : description);
        }
        NodeList bodies = document.getElementsByTagNameNS("*", "Body");
        if (bodies.getLength() != 1) throw new IOException("Malformed SOAP response");
        Element response = null;
        for (Node child = bodies.item(0).getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                if (response != null) throw new IOException("Ambiguous SOAP response");
                response = (Element) child;
            }
        }
        if (response == null) throw new IOException("Empty SOAP response");
        if (expectedAction != null && !(expectedAction + "Response").equals(response.getLocalName())) {
            throw new IOException("Unexpected UPnP response action");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Node child = response.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) result.put(child.getLocalName() == null ? child.getNodeName() : child.getLocalName(), child.getTextContent().trim());
        }
        return result;
    }

    static Gateway gatewayFromDescription(String xml, URL location, String localAddress) throws IOException {
        requireLocalUrl(location);
        Document document = parseXml(xml);
        URL base = location;
        String baseValue = text(document.getDocumentElement(), "URLBase");
        if (baseValue != null && !baseValue.isEmpty()) {
            base = new URL(location, baseValue);
            requireSameGateway(location, base);
        }
        NodeList services = document.getElementsByTagNameNS("*", "service");
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            String type = text(service, "serviceType");
            if (!isServiceType(type)) continue;
            String control = text(service, "controlURL");
            if (control == null || control.isEmpty()) continue;
            URL url = new URL(base, control);
            requireSameGateway(location, url);
            return new Gateway(url, type, localAddress);
        }
        throw new IOException("No WANIPConnection/WANPPPConnection service found");
    }

    private static String value(Map<String, String> values, String name) throws IOException {
        String value = values.get(name);
        if (value == null || value.isEmpty()) throw new IOException("Missing UPnP response field: " + name);
        return value;
    }

    private static String text(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private static Document parseXml(String xml) throws IOException {
        if (xml == null || xml.getBytes(StandardCharsets.UTF_8).length > MAX_XML_BYTES) throw new IOException("UPnP XML exceeds limit");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException error) throws org.xml.sax.SAXException { throw error; }
                @Override public void fatalError(org.xml.sax.SAXParseException error) throws org.xml.sax.SAXException { throw error; }
            });
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IOException("Invalid UPnP XML", error); }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static boolean isServiceType(String value) {
        return "urn:schemas-upnp-org:service:WANIPConnection:1".equals(value)
                || "urn:schemas-upnp-org:service:WANIPConnection:2".equals(value)
                || "urn:schemas-upnp-org:service:WANPPPConnection:1".equals(value);
    }

    private static int[] ipv4(String value) {
        if (value == null || !value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return null;
        String[] parts = value.split("\\.");
        int[] bytes = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].length() > 1 && parts[i].startsWith("0")) return null;
            bytes[i] = Integer.parseInt(parts[i]);
            if (bytes[i] > 255) return null;
        }
        return bytes;
    }

    private static boolean isPrivateIpv4(String value) {
        int[] ip = ipv4(value);
        return ip != null && (ip[0] == 10 || ip[0] == 192 && ip[1] == 168
                || ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31 || ip[0] == 169 && ip[1] == 254);
    }

    static boolean isPublicIpv4(String value) {
        int[] ip = ipv4(value);
        return ip != null && !isPrivateIpv4(value) && ip[0] != 0 && ip[0] != 127 && ip[0] < 224
                && !(ip[0] == 100 && ip[1] >= 64 && ip[1] <= 127)
                && !(ip[0] == 192 && ip[1] == 0 && (ip[2] == 0 || ip[2] == 2))
                && !(ip[0] == 198 && (ip[1] == 18 || ip[1] == 19 || ip[1] == 51 && ip[2] == 100))
                && !(ip[0] == 203 && ip[1] == 0 && ip[2] == 113);
    }

    private static void requireLocalUrl(URL url) throws IOException {
        if (!"http".equalsIgnoreCase(url.getProtocol()) || !isPrivateIpv4(url.getHost())
                || url.getUserInfo() != null || url.getRef() != null) throw new IOException("UPnP control must use a local IPv4 HTTP gateway");
    }

    private static void requireSameGateway(URL original, URL next) throws IOException {
        requireLocalUrl(next);
        if (!original.getHost().equals(next.getHost())) throw new IOException("UPnP control URL leaves discovered gateway");
    }

    /** Finite local multicast discovery, no internet IP-lookup service or DNS lookup. */
    public static final class SsdpDiscovery implements Discovery {
        private final HttpTransport http;
        public SsdpDiscovery(HttpTransport http) { this.http = http; }
        @Override public Gateway discover() throws IOException {
            InetAddress multicast = InetAddress.getByName("239.255.255.250");
            String request = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n";
            byte[] bytes = request.getBytes(StandardCharsets.US_ASCII);
            long deadline = System.nanoTime() + 3_000_000_000L;
            IOException last = null;
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.send(new DatagramPacket(bytes, bytes.length, multicast, 1900));
                while (System.nanoTime() < deadline) {
                    socket.setSoTimeout((int) Math.max(1, Math.min(1000, (deadline - System.nanoTime()) / 1_000_000)));
                    DatagramPacket response = new DatagramPacket(new byte[8192], 8192);
                    try { socket.receive(response); }
                    catch (SocketTimeoutException timeout) { continue; }
                    try {
                        if (!isPrivateIpv4(response.getAddress().getHostAddress())) continue;
                        String locationValue = location(new String(response.getData(), 0, response.getLength(), StandardCharsets.US_ASCII));
                        URL location = new URL(locationValue);
                        requireLocalUrl(location);
                        if (!response.getAddress().getHostAddress().equals(location.getHost())) continue;
                        String local;
                        try (DatagramSocket route = new DatagramSocket()) {
                            route.connect(new InetSocketAddress(response.getAddress(), 1900));
                            local = route.getLocalAddress().getHostAddress();
                        }
                        return gatewayFromDescription(http.get(location), location, local);
                    } catch (IOException error) { last = error; }
                }
            }
            throw new IOException("No usable UPnP gateway found within discovery timeout", last);
        }

        static String location(String response) throws IOException {
            String[] lines = response.split("\\r?\\n");
            if (lines.length == 0 || !lines[0].matches("HTTP/1\\.[01] 200(?: .*)?")) throw new IOException("Invalid SSDP response");
            String location = null;
            for (String line : lines) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).equalsIgnoreCase("location")) {
                    if (location != null) throw new IOException("Duplicate SSDP location");
                    location = line.substring(colon + 1).trim();
                }
            }
            if (location == null || location.isEmpty()) throw new IOException("SSDP response has no location");
            return location;
        }
    }

    public static final class HttpTransport implements SoapTransport {
        @Override public String call(Gateway gateway, String action, Map<String, String> arguments) throws IOException {
            byte[] body = envelope(gateway.serviceType, action, arguments).getBytes(StandardCharsets.UTF_8);
            HttpURLConnection connection = connection(gateway.controlUrl);
            try {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
                connection.setRequestProperty("SOAPAction", "\"" + gateway.serviceType + "#" + action + "\"");
                connection.setFixedLengthStreamingMode(body.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(body); }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                if (stream == null) throw new IOException("UPnP HTTP error " + status);
                String xml;
                try (InputStream input = stream) { xml = readXml(input); }
                if (status < 200 || status >= 300) {
                    parseResponse(xml); // Preserve structured UPnP fault codes, including free-port 714.
                    throw new IOException("UPnP HTTP error " + status);
                }
                return xml;
            } finally { connection.disconnect(); }
        }

        String get(URL url) throws IOException {
            HttpURLConnection connection = connection(url);
            try {
                if (connection.getResponseCode() != 200) throw new IOException("UPnP descriptor HTTP error");
                try (InputStream input = connection.getInputStream()) { return readXml(input); }
            } finally { connection.disconnect(); }
        }

        private HttpURLConnection connection(URL url) throws IOException {
            requireLocalUrl(url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setInstanceFollowRedirects(false);
            return connection;
        }

        private String readXml(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            long deadline = System.nanoTime() + 2_000_000_000L;
            while ((read = input.read(buffer)) != -1) {
                if (System.nanoTime() > deadline) throw new IOException("UPnP response timed out");
                if (bytes.size() + read > MAX_XML_BYTES) throw new IOException("UPnP response exceeds limit");
                bytes.write(buffer, 0, read);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
