/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 – Minecraft Client Launcher.
 */

package com.osiris.autoplug.client.minecraft.server;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the Minecraft {@code servers.dat} file in its native NBT format.
 * <p>
 * The format is a compressed (gzip) or uncompressed NBT TAG_Compound containing
 * a TAG_List named "servers" where each entry is a TAG_Compound with at least
 * "name" and "ip" TAG_String fields.
 * <p>
 * This implementation handles the file directly using Java's {@link DataInputStream}/
 * {@link DataOutputStream} to stay dependency-free (no third-party NBT library).
 * Unknown tags are read verbatim and preserved on write.
 */
public class ServersDat {

    // NBT type constants
    private static final byte TAG_END       = 0;
    private static final byte TAG_BYTE      = 1;
    private static final byte TAG_SHORT     = 2;
    private static final byte TAG_INT       = 3;
    private static final byte TAG_LONG      = 4;
    private static final byte TAG_FLOAT     = 5;
    private static final byte TAG_DOUBLE    = 6;
    private static final byte TAG_BYTE_ARRAY= 7;
    private static final byte TAG_STRING    = 8;
    private static final byte TAG_LIST      = 9;
    private static final byte TAG_COMPOUND  = 10;
    private static final byte TAG_INT_ARRAY = 11;
    private static final byte TAG_LONG_ARRAY= 12;

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Reads {@code servers.dat} and returns a mutable list of server entries.
     * The file may be gzip-compressed or plain NBT.
     */
    @NotNull
    public static List<ServerEntry> read(@NotNull File serversDat) throws IOException {
        List<ServerEntry> servers = new ArrayList<>();
        if (!serversDat.exists()) return servers;

        InputStream raw = new BufferedInputStream(new FileInputStream(serversDat));
        // Detect gzip magic bytes
        raw.mark(2);
        boolean isGzip = (raw.read() == 0x1F && raw.read() == 0x8B);
        raw.reset();
        DataInputStream din = new DataInputStream(
                isGzip ? new java.util.zip.GZIPInputStream(raw) : raw);

        // Root TAG_Compound
        byte rootType = din.readByte();
        if (rootType != TAG_COMPOUND)
            throw new IOException("servers.dat: expected root TAG_Compound, got " + rootType);
        skipString(din); // root name (usually empty)
        readCompoundInto(din, servers);
        din.close();
        return servers;
    }

    /**
     * Writes the list of server entries back to {@code servers.dat}.
     * The file is written uncompressed (vanilla reads both).
     * A temp-then-rename strategy ensures the file is never left half-written.
     */
    public static void write(@NotNull File serversDat, @NotNull List<ServerEntry> servers)
            throws IOException {
        serversDat.getParentFile().mkdirs();
        File tmp = new File(serversDat.getAbsolutePath() + ".tmp");
        try (DataOutputStream dout = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(tmp)))) {
            // Root TAG_Compound
            dout.writeByte(TAG_COMPOUND);
            writeString(dout, "");  // root name

            // TAG_List "servers"
            dout.writeByte(TAG_LIST);
            writeString(dout, "servers");
            dout.writeByte(TAG_COMPOUND);   // list element type
            dout.writeInt(servers.size());

            for (ServerEntry s : servers) {
                writeString(dout, s.name, "name");
                writeString(dout, s.ip,   "ip");
                if (s.icon != null) writeString(dout, s.icon, "icon");
                dout.writeByte(TAG_BYTE);
                writeString(dout, "acceptTextures");
                dout.writeByte(s.acceptTextures);
                // extra unknown tags
                for (java.util.Map.Entry<String, String> e : s.extraTags.entrySet())
                    writeString(dout, e.getValue(), e.getKey());
                dout.writeByte(TAG_END); // end of compound
            }

            dout.writeByte(TAG_END); // end root compound
        }
        Files.move(tmp.toPath(), serversDat.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // ── NBT parsing helpers ──────────────────────────────────────────────────

    private static void readCompoundInto(DataInputStream din, List<ServerEntry> servers)
            throws IOException {
        // Read the "servers" TAG_List inside the root compound
        byte tagType;
        while ((tagType = din.readByte()) != TAG_END) {
            String tagName = readString(din);
            if (tagType == TAG_LIST && "servers".equals(tagName)) {
                byte elemType = din.readByte();
                int count = din.readInt();
                for (int i = 0; i < count; i++) {
                    if (elemType == TAG_COMPOUND) servers.add(readServerEntry(din));
                    else skipTag(din, elemType);
                }
            } else {
                skipTag(din, tagType);
            }
        }
    }

    private static ServerEntry readServerEntry(DataInputStream din) throws IOException {
        String name = "", ip = "", icon = null;
        byte acceptTextures = 0;
        java.util.Map<String, String> extra = new java.util.LinkedHashMap<>();

        byte tagType;
        while ((tagType = din.readByte()) != TAG_END) {
            String key = readString(din);
            if (tagType == TAG_STRING) {
                String val = readString(din);
                switch (key) {
                    case "name":           name           = val; break;
                    case "ip":             ip             = val; break;
                    case "icon":           icon           = val; break;
                    default:               extra.put(key, val); break;
                }
            } else if (tagType == TAG_BYTE && "acceptTextures".equals(key)) {
                acceptTextures = din.readByte();
            } else {
                skipTag(din, tagType);
            }
        }
        ServerEntry e = new ServerEntry(name, ip);
        e.icon           = icon;
        e.acceptTextures = acceptTextures;
        e.extraTags.putAll(extra);
        return e;
    }

    // ── Skip helpers ─────────────────────────────────────────────────────────

    private static void skipTag(DataInputStream din, byte type) throws IOException {
        switch (type) {
            case TAG_BYTE:        din.readByte();    break;
            case TAG_SHORT:       din.readShort();   break;
            case TAG_INT:         din.readInt();     break;
            case TAG_LONG:        din.readLong();    break;
            case TAG_FLOAT:       din.readFloat();   break;
            case TAG_DOUBLE:      din.readDouble();  break;
            case TAG_BYTE_ARRAY:  din.skipBytes(din.readInt()); break;
            case TAG_STRING:      readString(din);   break;
            case TAG_LIST: {
                byte elem = din.readByte(); int cnt = din.readInt();
                for (int i = 0; i < cnt; i++) skipTag(din, elem);
                break;
            }
            case TAG_COMPOUND:    while (din.readByte() != TAG_END) { readString(din); } break;
            case TAG_INT_ARRAY:   din.skipBytes(din.readInt() * 4); break;
            case TAG_LONG_ARRAY:  din.skipBytes(din.readInt() * 8); break;
        }
    }

    // ── Read/write primitives ────────────────────────────────────────────────

    private static String readString(DataInputStream din) throws IOException {
        int len = din.readUnsignedShort();
        byte[] buf = new byte[len];
        din.readFully(buf);
        return new String(buf, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void skipString(DataInputStream din) throws IOException { readString(din); }

    private static void writeString(DataOutputStream dout, String s) throws IOException {
        byte[] buf = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        dout.writeShort(buf.length);
        dout.write(buf);
    }

    /** Writes a TAG_String tag with given name and value. */
    private static void writeString(DataOutputStream dout, String value, String tagName)
            throws IOException {
        dout.writeByte(TAG_STRING);
        writeString(dout, tagName);
        writeString(dout, value);
    }
}
