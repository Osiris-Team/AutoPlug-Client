package com.osiris.autoplug.client.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerStatusIconIntegrationTest {
    @TempDir Path directory;

    @Test void browserReadsEmbeddedIconFromTheSameStatusResponse() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB); image.setRGB(1, 2, 0x336699);
        ByteArrayOutputStream png = new ByteArrayOutputStream(); ImageIO.write(image, "png", png);
        ServerStatus result = ping("data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray()));
        assertTrue(result.online); assertNotNull(result.icon); assertEquals(0xFF336699, result.icon.getRGB(1, 2));
        assertEquals("Welcome", result.motd); assertEquals(3, result.players);
    }

    @Test void invalidOptionalFaviconDoesNotHideAnOtherwiseOnlineServer() throws Exception {
        ServerStatus result = ping("https://example.invalid/untrusted.png");
        assertTrue(result.online); assertNull(result.icon); assertEquals("1.21.1", result.version);
    }

    private ServerStatus ping(String favicon) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(4000);
            Future<?> reply = executor.submit(() -> {
                try (Socket client = server.accept()) {
                    client.setSoTimeout(4000); DataInputStream input = new DataInputStream(client.getInputStream());
                    int handshake = readVarInt(input); assertTrue(handshake > 3 && handshake < 512);
                    byte[] data = new byte[handshake]; input.readFully(data);
                    assertEquals(1, input.readUnsignedByte()); assertEquals(0, input.readUnsignedByte());
                    String json = "{\"description\":\"Welcome\",\"version\":{\"name\":\"1.21.1\",\"protocol\":767},"
                            + "\"players\":{\"online\":3,\"max\":20},\"favicon\":\"" + favicon + "\"}";
                    byte[] body = json.getBytes(StandardCharsets.UTF_8); ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DataOutputStream packet = new DataOutputStream(bytes); packet.writeByte(0); writeVarInt(packet, body.length); packet.write(body);
                    DataOutputStream output = new DataOutputStream(client.getOutputStream()); writeVarInt(output, bytes.size()); output.write(bytes.toByteArray()); output.flush();
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            String host = server.getInetAddress().getHostAddress();
            String address = (host.contains(":") ? "[" + host + "]" : host) + ":" + server.getLocalPort();
            ServerStatus result = new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")).ping(new SavedServer("Fixture", address));
            reply.get(5, TimeUnit.SECONDS); return result;
        } finally { executor.shutdownNow(); }
    }
    private static int readVarInt(DataInputStream input) throws IOException {
        int result = 0; for (int shift = 0; shift < 35; shift += 7) { int value = input.readUnsignedByte(); result |= (value & 127) << shift; if ((value & 128) == 0) return result; }
        throw new IOException("Invalid varint");
    }
    private static void writeVarInt(DataOutputStream output, int value) throws IOException {
        do { int next = value & 127; value >>>= 7; output.writeByte(value == 0 ? next : next | 128); } while (value != 0);
    }
}
