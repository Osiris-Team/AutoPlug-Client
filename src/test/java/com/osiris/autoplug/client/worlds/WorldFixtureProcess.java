package com.osiris.autoplug.client.worlds;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** A real child JVM used only by offline lifecycle tests; all sockets bind loopback. */
public final class WorldFixtureProcess {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("client")) { Thread.sleep(60000); return; }
        if (args[0].equals("exit")) return;
        Files.write(Paths.get("fixture.pid"), Long.toString(ProcessHandle.current().pid()).getBytes(StandardCharsets.UTF_8));
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get("server.properties"))) { properties.load(input); }
        if (!"127.0.0.1".equals(properties.getProperty("server-ip"))) throw new IllegalStateException("Fixture must be local");
        try (ServerSocket server = new ServerSocket(Integer.parseInt(properties.getProperty("server-port")), 8,
                InetAddress.getByName("127.0.0.1"))) {
            Thread input = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Files.write(Paths.get("commands.log"), (line + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        if (line.equals("stop")) {
                            Files.write(Paths.get("stopped.txt"), "graceful".getBytes(StandardCharsets.UTF_8)); server.close(); return;
                        }
                    }
                } catch (IOException ignored) { }
            });
            input.setDaemon(true); input.start();
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(2000);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    int length = varint(in); byte[] handshake = new byte[length]; in.readFully(handshake);
                    in.readUnsignedByte(); in.readUnsignedByte();
                    byte[] json = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},\"players\":{\"max\":20,\"online\":0},\"description\":{\"text\":\"Offline fixture\"}}".getBytes(StandardCharsets.UTF_8);
                    ByteArrayOutputStream packet = new ByteArrayOutputStream();
                    packet.write(0); varint(packet, json.length); packet.write(json);
                    OutputStream out = socket.getOutputStream(); varint(out, packet.size()); packet.writeTo(out); out.flush();
                } catch (SocketException e) { if (!server.isClosed()) throw e; }
            }
        }
    }
    private static int varint(InputStream input) throws IOException {
        int value = 0;
        for (int i = 0; i < 5; i++) { int b = input.read(); if (b < 0) throw new EOFException(); value |= (b & 127) << (i * 7); if ((b & 128) == 0) return value; }
        throw new IOException("Invalid varint");
    }
    private static void varint(OutputStream output, int value) throws IOException {
        do { int b = value & 127; value >>>= 7; output.write(value == 0 ? b : b | 128); } while (value != 0);
    }
}
