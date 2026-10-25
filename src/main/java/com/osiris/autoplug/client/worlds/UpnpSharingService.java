package com.osiris.autoplug.client.worlds;

import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional IGD sharing. The Minecraft process itself always stays on loopback. */
public final class UpnpSharingService implements SharingService {
    public interface Gateway {
        InetAddress localAddress();
        String externalAddress() throws Exception;
        void addMapping(int externalPort, int internalPort, String description, int leaseSeconds) throws Exception;
        void removeMapping(int externalPort, int internalPort, String description) throws Exception;
    }
    @FunctionalInterface public interface Discovery { Gateway discover() throws Exception; }
    private final Discovery discovery;
    public UpnpSharingService() { this(new IgdDiscovery()); }
    public UpnpSharingService(Discovery discovery) { this.discovery = Objects.requireNonNull(discovery); }

    @Override public Lease open(int loopbackPort) throws Exception {
        Gateway gateway = discovery.discover();
        if (gateway == null) throw new IOException("No UPnP internet gateway found");
        String externalAddress = gateway.externalAddress();
        InetAddress address = InetAddress.getByName(externalAddress);
        byte[] bytes = address.getAddress();
        if (!(address instanceof Inet4Address) || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || ((bytes[0] & 255) == 100 && (bytes[1] & 255) >= 64 && (bytes[1] & 255) <= 127))
            throw new IOException("The router has no public IPv4 address (double NAT or carrier-grade NAT)");
        Forwarder forwarder = new Forwarder(gateway.localAddress(), loopbackPort);
        String description = "AutoPlug-" + UUID.randomUUID().toString().substring(0, 12);
        int externalPort = forwarder.port();
        try {
            gateway.addMapping(externalPort, forwarder.port(), description, 3600);
            forwarder.start();
            return new MappingLease(gateway, forwarder, externalAddress + ":" + externalPort, externalPort, description);
        } catch (Exception e) {
            forwarder.close();
            // A lost response can mean AddPortMapping succeeded. Ownership is checked before deletion.
            try { gateway.removeMapping(externalPort, forwarder.port(), description); } catch (Exception cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }

    private static final class MappingLease implements Lease {
        private final Gateway gateway;
        private final Forwarder forwarder;
        private final String address;
        private final int externalPort;
        private final String description;
        private final ScheduledExecutorService renewer = Executors.newSingleThreadScheduledExecutor(daemon("world-UPnP-renew"));
        private final AtomicBoolean closed = new AtomicBoolean();
        MappingLease(Gateway gateway, Forwarder forwarder, String address, int externalPort, String description) {
            this.gateway = gateway; this.forwarder = forwarder; this.address = address;
            this.externalPort = externalPort; this.description = description;
            renewer.scheduleWithFixedDelay(() -> {
                synchronized (this) {
                    if (closed.get()) return;
                    try { gateway.addMapping(externalPort, forwarder.port(), description, 3600); }
                    catch (Exception e) { try { close(); } catch (Exception ignored) { } }
                }
            }, 1800, 1800, TimeUnit.SECONDS);
        }
        public String address() { return address; }
        public boolean isActive() { return !closed.get(); }
        public synchronized void close() throws Exception {
            if (!closed.compareAndSet(false, true)) return;
            renewer.shutdownNow();
            forwarder.close();
            gateway.removeMapping(externalPort, forwarder.port(), description);
        }
    }

    /** Bounded connection count; no public listener is opened until an explicit share request. */
    static final class Forwarder implements AutoCloseable {
        private final ServerSocket listener;
        private final int port;
        private final int targetPort;
        private final ExecutorService workers = Executors.newCachedThreadPool(daemon("world-share"));
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final Semaphore connections = new Semaphore(32);
        Forwarder(InetAddress address, int targetPort) throws IOException {
            listener = new ServerSocket(0, 16, address);
            port = listener.getLocalPort();
            this.targetPort = targetPort;
        }
        int port() { return port; }
        void start() {
            workers.execute(() -> {
                while (!listener.isClosed()) {
                    try {
                        Socket incoming = listener.accept();
                        if (!connections.tryAcquire()) { incoming.close(); continue; }
                        sockets.add(incoming);
                        workers.execute(() -> forward(incoming));
                    } catch (IOException | RejectedExecutionException e) { close(); return; }
                }
            });
        }
        private void forward(Socket incoming) {
            Socket target = new Socket();
            sockets.add(target);
            try {
                target.connect(new InetSocketAddress("127.0.0.1", targetPort), 3000);
                incoming.setSoTimeout(120000); target.setSoTimeout(120000);
                Future<?> outward = workers.submit(() -> copy(incoming, target));
                copy(target, incoming);
                outward.cancel(true);
            } catch (IOException | RejectedExecutionException ignored) {
            } finally {
                closeSocket(incoming); closeSocket(target);
                sockets.remove(incoming); sockets.remove(target); connections.release();
            }
        }
        private static void copy(Socket from, Socket to) {
            try {
                byte[] buffer = new byte[16384]; int count;
                InputStream input = from.getInputStream(); OutputStream output = to.getOutputStream();
                while ((count = input.read(buffer)) != -1) { output.write(buffer, 0, count); output.flush(); }
                to.shutdownOutput();
            } catch (IOException ignored) { closeSocket(from); closeSocket(to); }
        }
        public void close() {
            try { listener.close(); } catch (IOException ignored) { }
            for (Socket socket : sockets) closeSocket(socket);
            workers.shutdownNow();
        }
        private static void closeSocket(Socket socket) { try { socket.close(); } catch (IOException ignored) { } }
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> { Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread; };
    }

    static final class IgdDiscovery implements Discovery {
        public Gateway discover() throws Exception {
            String request = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n";
            byte[] data = request.getBytes(StandardCharsets.US_ASCII);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(2000);
                socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("239.255.255.250"), 1900));
                long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (System.nanoTime() < end) {
                    DatagramPacket reply = new DatagramPacket(new byte[8192], 8192);
                    try { socket.receive(reply); } catch (SocketTimeoutException e) { break; }
                    InetAddress router = reply.getAddress();
                    if (!router.isSiteLocalAddress()) continue;
                    String response = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.US_ASCII);
                    for (String line : response.split("\r?\n")) {
                        if (!line.toLowerCase(Locale.ROOT).startsWith("location:")) continue;
                        try {
                            URL location = checkedUrl(new URL(line.substring(9).trim()), router);
                            Document description = xml(http(location, null, null));
                            NodeList services = description.getElementsByTagNameNS("*", "service");
                            for (int i = 0; i < services.getLength(); i++) {
                                Element service = (Element) services.item(i);
                                String type = value(service, "serviceType");
                                if (type != null && (type.matches("urn:schemas-upnp-org:service:WANIPConnection:[12]")
                                        || type.equals("urn:schemas-upnp-org:service:WANPPPConnection:1"))) {
                                    URL control = checkedUrl(new URL(location, value(service, "controlURL")), router);
                                    try (DatagramSocket route = new DatagramSocket()) {
                                        route.connect(router, 1900);
                                        return new SoapGateway(control, type, route.getLocalAddress());
                                    }
                                }
                            }
                        } catch (IOException ignored) { /* Try another advertised gateway. */ }
                    }
                }
            }
            throw new IOException("No supported UPnP gateway responded");
        }
    }

    static final class SoapGateway implements Gateway {
        private final URL control;
        private final String type;
        private final InetAddress local;
        SoapGateway(URL control, String type, InetAddress local) { this.control = control; this.type = type; this.local = local; }
        public InetAddress localAddress() { return local; }
        public String externalAddress() throws Exception { return value(call("GetExternalIPAddress", ""), "NewExternalIPAddress"); }
        public void addMapping(int external, int internal, String description, int lease) throws Exception {
            String keys = element("NewRemoteHost", "") + element("NewExternalPort", external) + element("NewProtocol", "TCP");
            try {
                Element existing = call("GetSpecificPortMappingEntry", keys);
                if (!Integer.toString(internal).equals(value(existing, "NewInternalPort"))
                        || !local.getHostAddress().equals(value(existing, "NewInternalClient"))
                        || !description.equals(value(existing, "NewPortMappingDescription")))
                    throw new IOException("The requested external port already belongs to another mapping");
            } catch (SoapFault e) { if (!"714".equals(e.code)) throw e; }
            call("AddPortMapping", keys + element("NewInternalPort", internal)
                    + element("NewInternalClient", local.getHostAddress()) + element("NewEnabled", 1)
                    + element("NewPortMappingDescription", description) + element("NewLeaseDuration", lease));
        }
        public void removeMapping(int external, int internal, String description) throws Exception {
            String keys = element("NewRemoteHost", "") + element("NewExternalPort", external) + element("NewProtocol", "TCP");
            try {
                Element existing = call("GetSpecificPortMappingEntry", keys);
                if (Integer.toString(internal).equals(value(existing, "NewInternalPort"))
                        && local.getHostAddress().equals(value(existing, "NewInternalClient"))
                        && description.equals(value(existing, "NewPortMappingDescription"))) call("DeletePortMapping", keys);
            } catch (SoapFault e) { if (!"714".equals(e.code)) throw e; }
        }
        private Element call(String action, String fields) throws Exception {
            String envelope = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:"
                    + action + " xmlns:u=\"" + type + "\">" + fields + "</u:" + action + "></s:Body></s:Envelope>";
            Element root = xml(http(control, type + "#" + action, envelope.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
            String error = value(root, "errorCode");
            if (error != null) throw new SoapFault(error, value(root, "errorDescription"));
            return root;
        }
    }

    static final class SoapFault extends IOException {
        final String code;
        SoapFault(String code, String message) { super("UPnP " + code + ": " + message); this.code = code; }
    }
    private static URL checkedUrl(URL url, InetAddress router) throws IOException {
        if (!"http".equals(url.getProtocol()) || url.getUserInfo() != null
                || !InetAddress.getByName(url.getHost()).equals(router)) throw new IOException("Invalid gateway endpoint");
        // Pin the discovered LAN IP rather than resolving an advertised hostname again.
        return new URL("http", router.getHostAddress(), url.getPort(), url.getFile());
    }
    static Document xml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
    private static String value(Element root, String name) {
        NodeList values = root.getElementsByTagNameNS("*", name);
        return values.getLength() == 0 ? null : values.item(0).getTextContent().trim();
    }
    private static String element(String name, Object value) {
        String text = String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<" + name + ">" + text + "</" + name + ">";
    }
    private static byte[] http(URL url, String action, byte[] body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(2000); connection.setReadTimeout(3000); connection.setInstanceFollowRedirects(false);
        try {
            if (body != null) {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
                connection.setRequestProperty("SOAPAction", "\"" + action + "\"");
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            }
            int code = connection.getResponseCode();
            InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (input == null || (code != 200 && code != 500)) throw new IOException("Gateway HTTP status " + code);
            try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > 1024 * 1024) throw new IOException("Gateway response too large");
                    out.write(buffer, 0, count);
                }
                return out.toByteArray();
            }
        } finally { connection.disconnect(); }
    }
}
