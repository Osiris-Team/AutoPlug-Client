/* Original MIT fixture code. This is not a Minecraft server or client. */
package com.osiris.autoplug.client.worlds;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small real child JVMs speaking only Minecraft status, used to exercise process ownership. */
public final class WorldFixtureProcesses {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length > 0 && arguments[0].equals("client")) { client(arguments); return; }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get("server.properties"))) { properties.load(input); }
        if (!Files.readAllLines(Paths.get("eula.txt")).contains("eula=true")) throw new IOException("Fixture EULA flag missing");
        String mode = Files.exists(Paths.get("fixture-mode.txt")) ? Files.readAllLines(Paths.get("fixture-mode.txt")).get(0) : "normal";
        if (mode.equals("exit")) { System.out.println("Fixture intentionally failed before readiness"); System.exit(7); }
        int port = Integer.parseInt(properties.getProperty("server-port"));
        ServerSocket listener = new ServerSocket(); listener.bind(new InetSocketAddress("127.0.0.1", port));
        AtomicBoolean alive = new AtomicBoolean(true);
        Thread commandReader = new Thread(() -> {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String command;
                while ((command = input.readLine()) != null) if (command.equals("stop") && !mode.equals("stubborn")) {
                    Files.write(Paths.get("fixture-stopped.txt"), Collections.singletonList("graceful stop")); alive.set(false); listener.close(); return;
                }
            } catch (IOException ignored) { }
        }, "fixture-stop-reader"); commandReader.setDaemon(true); commandReader.start();
        char[] longLine = new char[5000]; Arrays.fill(longLine, 'x');
        for (int i = 0; i < 100; i++) System.out.println(i + ":" + new String(longLine));
        Files.write(Paths.get("fixture-ready.txt"), Collections.singletonList(Integer.toString(port)));
        try {
            while (alive.get()) try (Socket connection = listener.accept()) {
                if (mode.equals("silent")) { Thread.sleep(1800); continue; }
                connection.setSoTimeout(2000);
                DataInputStream input = new DataInputStream(connection.getInputStream());
                int handshake = readVarInt(input); byte[] ignored = new byte[handshake]; input.readFully(ignored);
                int request = readVarInt(input); ignored = new byte[request]; input.readFully(ignored);
                byte[] json = "{\"version\":{\"name\":\"Paper 1.20.4\",\"protocol\":765},\"players\":{\"max\":8,\"online\":0},\"description\":{\"text\":\"Original AutoPlug world fixture\"}}".getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream packet = new DataOutputStream(bytes);
                writeVarInt(packet, 0); writeVarInt(packet, json.length); packet.write(json);
                DataOutputStream output = new DataOutputStream(connection.getOutputStream()); writeVarInt(output, bytes.size()); output.write(bytes.toByteArray()); output.flush();
            }
        } catch (SocketException stopped) { if (alive.get()) throw stopped; }
        finally { listener.close(); }
    }
    private static void client(String[] arguments) throws Exception {
        Properties record = new Properties(); record.setProperty("address", arguments[1]); record.setProperty("workingDirectory", System.getProperty("user.dir"));
        try (OutputStream output = Files.newOutputStream(Paths.get("fixture-client.properties"))) { record.store(output, "Original offline fixture client"); }
        // Validate a real TCP connection to the service's chosen localhost endpoint before exiting.
        String[] endpoint = arguments[1].split(":");
        try (Socket connected = new Socket(endpoint[0], Integer.parseInt(endpoint[1]))) {
            if (!connected.isConnected()) throw new IOException("Client fixture did not connect");
            connected.setSoTimeout(2000); connected.getOutputStream().write(new byte[]{1, 0, 1, 0}); connected.getOutputStream().flush();
            DataInputStream input = new DataInputStream(connected.getInputStream()); int responseLength = readVarInt(input); byte[] response = new byte[responseLength]; input.readFully(response);
        }
        char[] noise = new char[200000]; Arrays.fill(noise, 'y'); System.out.println(new String(noise));
        Thread.sleep(Long.parseLong(arguments[2]));
    }
    private static int readVarInt(DataInputStream input) throws IOException {
        int result = 0; for (int shift = 0; shift < 35; shift += 7) { int next = input.readUnsignedByte(); result |= (next & 127) << shift; if ((next & 128) == 0) return result; } throw new IOException("Invalid fixture VarInt");
    }
    private static void writeVarInt(DataOutputStream output, int value) throws IOException { do { int next = value & 127; value >>>= 7; output.writeByte(value == 0 ? next : next | 128); } while (value != 0); }
}
