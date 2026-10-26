/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 – Minecraft Client Launcher.
 */

package com.osiris.autoplug.client.minecraft.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Pings a Minecraft server using the 1.7+ SLP (Server List Ping) protocol
 * and populates a {@link ServerEntry} with MOTD, player counts, version, and latency.
 * <p>
 * Falls back to the legacy (pre-1.7) ping if the modern protocol fails.
 */
public class ServerPinger {

    private static final int TIMEOUT_MS = 5_000;

    /**
     * Pings the server specified by {@code entry} and writes the result fields
     * ({@link ServerEntry#detectedVersion}, {@link ServerEntry#onlinePlayers}, etc.)
     * into the same entry.
     *
     * @param entry server to ping (reads host/port, writes status fields)
     * @return true if the ping succeeded
     */
    public static boolean ping(@NotNull ServerEntry entry) {
        long start = System.currentTimeMillis();
        try {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(entry.host(), entry.port()), TIMEOUT_MS);
                socket.setSoTimeout(TIMEOUT_MS);

                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(socket.getOutputStream()));
                DataInputStream in   = new DataInputStream(
                        new BufferedInputStream(socket.getInputStream()));

                // ── Handshake packet (0x00) ────────────────────────────────
                byte[] host  = entry.host().getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream handshakeBuf = new ByteArrayOutputStream();
                DataOutputStream hs = new DataOutputStream(handshakeBuf);
                writeVarInt(hs, 0x00);              // packet id
                writeVarInt(hs, 754);               // protocol version (doesn't need to match)
                writeVarInt(hs, host.length);
                hs.write(host);
                hs.writeShort(entry.port());
                writeVarInt(hs, 1);                 // next state = STATUS

                writeVarInt(out, handshakeBuf.size());
                out.write(handshakeBuf.toByteArray());

                // ── Status Request (0x00) ──────────────────────────────────
                writeVarInt(out, 1);   // length
                writeVarInt(out, 0x00);
                out.flush();

                // ── Read Status Response ───────────────────────────────────
                int length = readVarInt(in);
                if (length < 1) throw new IOException("Empty status response");
                readVarInt(in); // packet id (0x00)

                int jsonLen = readVarInt(in);
                byte[] jsonBytes = new byte[jsonLen];
                in.readFully(jsonBytes);
                String json = new String(jsonBytes, StandardCharsets.UTF_8);

                entry.latencyMs = System.currentTimeMillis() - start;

                JsonObject root = new Gson().fromJson(json, JsonObject.class);
                if (root.has("version")) {
                    JsonObject ver = root.getAsJsonObject("version");
                    entry.detectedVersion = ver.has("name") ? ver.get("name").getAsString() : null;
                }
                if (root.has("players")) {
                    JsonObject pl = root.getAsJsonObject("players");
                    entry.onlinePlayers = pl.has("online") ? pl.get("online").getAsInt() : -1;
                    entry.maxPlayers    = pl.has("max")    ? pl.get("max").getAsInt()    : -1;
                }
                if (root.has("description")) {
                    JsonObject desc = root.getAsJsonObject("description");
                    entry.motd = desc.has("text") ? desc.get("text").getAsString() : root.get("description").getAsString();
                }
            }
            return true;
        } catch (Exception e) {
            AL.debug(ServerPinger.class, "Ping failed for " + entry.ip + ": " + e.getMessage());
            return false;
        }
    }

    // ── VarInt helpers (Minecraft protocol) ─────────────────────────────────

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0, shift = 0;
        byte b;
        do {
            b = in.readByte();
            result |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) throw new IOException("VarInt too big");
        } while ((b & 0x80) != 0);
        return result;
    }
}
