/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.worlds.network;

import com.osiris.autoplug.client.worlds.VirtualWorldService;
import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in IPv4 IGD control. No discovery or router request occurs until map is called. */
public final class UpnpPortMapper implements VirtualWorldService.Sharing {
    public interface Discovery { List<Gateway> discover() throws IOException; }
    public static final class Gateway {
        public final URI description; public final InetAddress address, localAddress;
        public Gateway(URI description, InetAddress address, InetAddress localAddress) {
            this.description = Objects.requireNonNull(description); this.address = Objects.requireNonNull(address); this.localAddress = Objects.requireNonNull(localAddress);
        }
    }
    private final Discovery discovery;
    private final int leaseSeconds, renewSeconds;
    private static final int MAX_XML = 512 * 1024;
    public UpnpPortMapper() { this(UpnpPortMapper::discoverLocalGateways, 1800, 600); }
    public UpnpPortMapper(Discovery discovery, int leaseSeconds, int renewSeconds) {
        if (leaseSeconds < 3 || renewSeconds < 1 || renewSeconds >= leaseSeconds) throw new IllegalArgumentException("Invalid finite mapping lease");
        this.discovery = Objects.requireNonNull(discovery); this.leaseSeconds = leaseSeconds; this.renewSeconds = renewSeconds;
    }
    @Override public VirtualWorldService.Mapping map(int localPort) throws Exception {
        if (localPort < 1 || localPort > 65535) throw new IllegalArgumentException("Invalid server port");
        Exception last = null;
        for (Gateway gateway : discovery.discover()) {
            try {
                Service service = service(gateway);
                String external = text(call(service, "GetExternalIPAddress", Collections.emptyMap()), "NewExternalIPAddress");
                if (!publicIpv4(external)) throw new IOException("The router has no public IPv4 address (possibly CGNAT/double NAT). Use manual forwarding or an explicitly configured tunnel.");
                Map<String,String> key = key(localPort);
                try { call(service, "GetSpecificPortMappingEntry", key); throw new IOException("The requested router port already has a mapping; it will not be overwritten."); }
                catch (SoapFailure missing) { if (missing.code != 714 && missing.code != 713) throw missing; }
                String description = "AutoPlug-" + UUID.randomUUID();
                Map<String,String> args = new LinkedHashMap<>(key);
                args.put("NewInternalPort", String.valueOf(localPort)); args.put("NewInternalClient", gateway.localAddress.getHostAddress());
                args.put("NewEnabled", "1"); args.put("NewPortMappingDescription", description); args.put("NewLeaseDuration", String.valueOf(leaseSeconds));
                boolean attempted = false;
                try {
                    attempted = true; call(service, "AddPortMapping", args);
                    OwnedMapping mapping = new OwnedMapping(service, key, args, external + ":" + localPort);
                    mapping.requireOwnership(); mapping.startRenewal(); return mapping;
                } catch (Exception failure) {
                    if (attempted) try { removeOwned(service, key, args); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                    throw failure;
                }
            } catch (Exception failure) { last = failure; }
        }
        throw new IOException("Sharing failed: " + (last == null ? "no compatible local UPnP gateway was found" : last.getMessage()) + ". The world can still be played locally; check router settings or configure forwarding/tunneling yourself.", last);
    }
    private final class OwnedMapping implements VirtualWorldService.Mapping {
        final Service service; final Map<String,String> key, args; final String address;
        final AtomicBoolean closed = new AtomicBoolean(); final ScheduledExecutorService renewal;
        volatile Exception renewalFailure;
        OwnedMapping(Service service, Map<String,String> key, Map<String,String> args, String address) {
            this.service = service; this.key = key; this.args = args; this.address = address;
            renewal = Executors.newSingleThreadScheduledExecutor(task -> { Thread thread = new Thread(task, "AutoPlug-UPnP-lease"); thread.setDaemon(true); return thread; });
        }
        synchronized void requireOwnership() throws Exception { verifyOwned(call(service, "GetSpecificPortMappingEntry", key), args); }
        void startRenewal() { renewal.scheduleWithFixedDelay(() -> {
            synchronized (this) {
                if (closed.get()) return;
                try { requireOwnership(); call(service, "AddPortMapping", args); requireOwnership(); }
                catch (Exception failure) { renewalFailure = failure; renewal.shutdown(); }
            }
        }, renewSeconds, renewSeconds, TimeUnit.SECONDS); }
        @Override public String joinAddress() {
            if (closed.get()) throw new IllegalStateException("World sharing has stopped");
            if (renewalFailure != null) throw new IllegalStateException("UPnP lease renewal failed; sharing may have expired: " + renewalFailure.getMessage(), renewalFailure);
            return address;
        }
        @Override public synchronized void close() throws Exception {
            if (!closed.compareAndSet(false, true)) return;
            renewal.shutdownNow(); removeOwned(service, key, args);
        }
    }
    private static void removeOwned(Service service, Map<String,String> key, Map<String,String> args) throws Exception {
        Document entry;
        try { entry = call(service, "GetSpecificPortMappingEntry", key); }
        catch (SoapFailure missing) { if (missing.code == 714 || missing.code == 713) return; throw missing; }
        verifyIdentity(entry, args); call(service, "DeletePortMapping", key);
    }
    private static void verifyOwned(Document entry, Map<String,String> args) throws IOException {
        verifyIdentity(entry, args);
        String lease = text(entry, "NewLeaseDuration");
        try { if (Integer.parseInt(lease) <= 0) throw new IOException("Router did not honor the finite lease; permanent mappings are not accepted"); }
        catch (NumberFormatException bad) { throw new IOException("Router returned an invalid lease", bad); }
    }
    private static void verifyIdentity(Document entry, Map<String,String> args) throws IOException {
        for (String name : new String[]{"NewInternalPort", "NewInternalClient", "NewPortMappingDescription"}) if (!args.get(name).equals(text(entry, name))) throw new IOException("Router mapping ownership changed; refusing to renew or delete another mapping");
        if (!Arrays.asList("1", "true").contains(text(entry, "NewEnabled"))) throw new IOException("Router mapping was not enabled");
    }
    private static Map<String,String> key(int port) { Map<String,String> values = new LinkedHashMap<>(); values.put("NewRemoteHost", ""); values.put("NewExternalPort", String.valueOf(port)); values.put("NewProtocol", "TCP"); return values; }
    private static final class Service { final Gateway gateway; final URI control; final String type; Service(Gateway gateway, URI control, String type) { this.gateway = gateway; this.control = control; this.type = type; } }
    private static Service service(Gateway gateway) throws Exception {
        validateEndpoint(gateway.description, gateway.address);
        Document description = xml(request(gateway.description, null, null));
        URI base = gateway.description; String advertisedBase = text(description, "URLBase");
        if (!advertisedBase.isEmpty()) { base = gateway.description.resolve(advertisedBase); validateEndpoint(base, gateway.address); }
        NodeList services = description.getElementsByTagNameNS("*", "service");
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element)services.item(i); String type = text(service, "serviceType");
            if (type.matches("urn:schemas-upnp-org:service:WAN(IP|PPP)Connection:[12]")) {
                String controlPath = text(service, "controlURL"); if (controlPath.isEmpty()) continue;
                URI control = base.resolve(controlPath); validateEndpoint(control, gateway.address); return new Service(gateway, control, type);
            }
        }
        throw new IOException("Gateway description has no WANIP/WANPPP connection service");
    }
    static void validateEndpoint(URI endpoint, InetAddress peer) throws IOException {
        if (!("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme())) || endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null || endpoint.toString().length() > 4096) throw new IOException("Invalid UPnP control endpoint");
        // Literal addresses prevent DNS rebinding; every description/control request stays with its SSDP peer.
        String host = endpoint.getHost(); if (!host.matches("[0-9.]+") || !InetAddress.getByName(host).equals(peer)) throw new IOException("UPnP control endpoint must remain on the responding gateway");
        if (!(peer instanceof Inet4Address)) throw new IOException("IPv6-only UPnP needs manual sharing configuration");
    }
    private static Document call(Service service, String action, Map<String,String> arguments) throws Exception {
        StringBuilder body = new StringBuilder("<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:").append(action).append(" xmlns:u=\"").append(service.type).append("\">");
        for (Map.Entry<String,String> argument : arguments.entrySet()) body.append('<').append(argument.getKey()).append('>').append(escape(argument.getValue())).append("</").append(argument.getKey()).append('>');
        body.append("</u:").append(action).append("></s:Body></s:Envelope>");
        Document result = xml(request(service.control, service.type + "#" + action, body.toString().getBytes(StandardCharsets.UTF_8)));
        String fault = text(result, "errorCode");
        if (!fault.isEmpty()) { int code; try { code = Integer.parseInt(fault); } catch (NumberFormatException bad) { code = -1; } throw new SoapFailure(code, "UPnP " + action + " error " + code + ": " + text(result, "errorDescription")); }
        if (result.getElementsByTagNameNS("*", action + "Response").getLength() != 1) throw new IOException("Malformed UPnP " + action + " response");
        return result;
    }
    private static final class SoapFailure extends IOException { final int code; SoapFailure(int code, String message) { super(message); this.code = code; } }
    private static byte[] request(URI endpoint, String action, byte[] body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)endpoint.toURL().openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
        try {
            if (body != null) { connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setFixedLengthStreamingMode(body.length); connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\""); connection.setRequestProperty("SOAPAction", '"' + action + '"'); try (OutputStream output = connection.getOutputStream()) { output.write(body); } }
            int status = connection.getResponseCode(); if (status >= 300 && status < 400) throw new IOException("UPnP redirects are not followed");
            if (connection.getContentLengthLong() > MAX_XML) throw new IOException("UPnP XML response is too large");
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream(); if (stream == null) throw new IOException("Empty UPnP HTTP response (" + status + ")");
            try (InputStream input = stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) { if (bytes.size() + count > MAX_XML) throw new IOException("UPnP XML response is too large"); bytes.write(buffer, 0, count); } return bytes.toByteArray(); }
        } finally { connection.disconnect(); }
    }
    static Document xml(byte[] bytes) throws Exception {
        if (bytes.length > MAX_XML) throw new IOException("UPnP XML response is too large");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true); factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); factory.setFeature("http://xml.org/sax/features/external-general-entities", false); factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
    private static String text(Node node, String localName) {
        NodeList items = node instanceof Document ? ((Document)node).getElementsByTagNameNS("*", localName) : ((Element)node).getElementsByTagNameNS("*", localName);
        return items.getLength() == 0 ? "" : items.item(0).getTextContent().trim();
    }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    static boolean publicIpv4(String value) {
        if (value == null || !value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        String[] parts = value.split("\\."); int[] octets = new int[4]; for (int i = 0; i < 4; i++) { octets[i] = Integer.parseInt(parts[i]); if (octets[i] > 255) return false; }
        int a = octets[0], b = octets[1];
        return !(a == 0 || a == 10 || a == 127 || a >= 224 || a == 169 && b == 254 || a == 172 && b >= 16 && b <= 31 || a == 192 && b == 168 || a == 100 && b >= 64 && b <= 127 || a == 192 && b == 0 || a == 198 && (b == 18 || b == 19 || b == 51) || a == 203 && b == 0);
    }
    static List<Gateway> discoverLocalGateways() throws IOException {
        List<Gateway> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        byte[] search = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        try (MulticastSocket socket = new MulticastSocket(0)) {
            socket.setTimeToLive(1); socket.setSoTimeout(300);
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); int sent = 0;
            while (interfaces.hasMoreElements() && sent < 8) {
                NetworkInterface network = interfaces.nextElement(); if (!network.isUp() || network.isLoopback() || !network.supportsMulticast()) continue;
                boolean ipv4 = Collections.list(network.getInetAddresses()).stream().anyMatch(address -> address instanceof Inet4Address && address.isSiteLocalAddress()); if (!ipv4) continue;
                try { socket.setNetworkInterface(network); socket.send(new DatagramPacket(search, search.length, InetAddress.getByName("239.255.255.250"), 1900)); sent++; } catch (IOException unavailable) { }
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline && result.size() < 16) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("UPnP discovery cancelled");
                DatagramPacket packet = new DatagramPacket(new byte[8192], 8192);
                try { socket.receive(packet); } catch (SocketTimeoutException again) { continue; }
                InetAddress peer = packet.getAddress(); if (!(peer instanceof Inet4Address) || !(peer.isSiteLocalAddress() || peer.isLinkLocalAddress())) continue;
                String response = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.US_ASCII); if (!response.startsWith("HTTP/1.1 200 ")) continue;
                for (String line : response.split("\r?\n")) if (line.toLowerCase(Locale.ROOT).startsWith("location:")) {
                    try {
                        URI location = URI.create(line.substring(9).trim()); validateEndpoint(location, peer);
                        if (seen.add(location.toString())) try (DatagramSocket route = new DatagramSocket()) { route.connect(peer, 1900); InetAddress local = route.getLocalAddress(); if (local instanceof Inet4Address && !local.isAnyLocalAddress() && !local.isLoopbackAddress()) result.add(new Gateway(location, peer, local)); }
                    } catch (IOException | IllegalArgumentException invalid) { }
                }
            }
        }
        return result;
    }
}
