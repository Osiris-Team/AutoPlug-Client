package com.osiris.autoplug.client.browser;

import com.osiris.autoplug.client.utils.MineStat;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class MineStatJsonTest {
    @Test void readsStringAndNestedArrayMotdsWithUnicodeAndProtocol() throws Exception {
        for (String description : new String[]{"\"§aWelcome 世界\"", "{\"text\":\"§aWelcome \",\"extra\":[\"世界\",{\"text\":\"\"}]}"}) {
            String json = "{\"description\":" + description + ",\"version\":{\"name\":\"Paper 1.21.1\",\"protocol\":767},\"players\":{\"online\":3,\"max\":20}}";
            MineStat ping = fixture(output -> response(output, json));
            assertEquals(MineStat.Retval.SUCCESS, ping.pingResult); assertTrue(ping.isServerUp());
            assertEquals("Welcome 世界", ping.getStrippedMotd()); assertEquals("Paper 1.21.1", ping.getVersion());
            assertEquals(767, ping.getProtocol()); assertEquals(3, ping.getCurrentPlayers()); assertEquals(20, ping.getMaximumPlayers());
        }
    }

    @Test void rejectsOversizedOrInvalidJsonPacketsWithoutAllocation() throws Exception {
        MineStat oversized = fixture(output -> { varInt(output, Integer.MAX_VALUE); output.flush(); });
        assertEquals(MineStat.Retval.UNKNOWN, oversized.pingResult); assertFalse(oversized.isServerUp());
        MineStat malformed = fixture(output -> response(output, "{\"description\":\"hi\",\"players\":null}"));
        assertEquals(MineStat.Retval.UNKNOWN, malformed.pingResult); assertFalse(malformed.isServerUp());
        MineStat wrongPacket = fixture(output -> { output.write(new byte[]{3, 1, 1, 'x'}); output.flush(); });
        assertEquals(MineStat.Retval.UNKNOWN, wrongPacket.pingResult);
    }

    @Test void slowServerCannotBlockTheBrowserIndefinitely() throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(4), () -> {
            MineStat ping = fixture(output -> { try { Thread.sleep(1400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
            assertEquals(MineStat.Retval.TIMEOUT, ping.pingResult); assertFalse(ping.isServerUp());
        });
    }

    private MineStat fixture(Reply reply) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(3000);
            Future<?> future = executor.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    int length = readVarInt(input); assertTrue(length > 3 && length < 512);
                    byte[] handshake = new byte[length]; input.readFully(handshake);
                    assertEquals(1, input.readUnsignedByte()); assertEquals(0, input.readUnsignedByte());
                    reply.send(new DataOutputStream(socket.getOutputStream()));
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            MineStat result = new MineStat(server.getInetAddress().getHostAddress(), server.getLocalPort(), 1, MineStat.Request.JSON);
            future.get(4, TimeUnit.SECONDS); return result;
        } finally { executor.shutdownNow(); }
    }
    private void response(DataOutputStream output, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8); ByteArrayOutputStream packet = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(packet); data.writeByte(0); varInt(data, payload.length); data.write(payload);
        varInt(output, packet.size());
        // Deliberately fragmented packet exercises readFully instead of relying on one socket read.
        for (byte value : packet.toByteArray()) { output.write(value); output.flush(); }
    }
    private static int readVarInt(DataInputStream input) throws IOException { int result = 0; for (int shift = 0; shift < 35; shift += 7) { int value = input.readUnsignedByte(); result |= (value & 127) << shift; if ((value & 128) == 0) return result; } throw new IOException("Invalid varint"); }
    private static void varInt(DataOutputStream output, int value) throws IOException { do { int next = value & 127; value >>>= 7; output.writeByte(value == 0 ? next : next | 128); } while (value != 0); }
    @FunctionalInterface private interface Reply { void send(DataOutputStream output) throws IOException; }
}
