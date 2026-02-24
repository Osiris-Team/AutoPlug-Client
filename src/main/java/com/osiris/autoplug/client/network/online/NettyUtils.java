package com.osiris.autoplug.client.network.online;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.util.CharsetUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class NettyUtils {
    public static final String EOF = "\u001a";

    // Faster, cleaner, standard UTF-8.
    // NOT compatible with DataInputStream.readUTF().
    public static void writeUTF(ByteBuf buf, String s) {
        // 1. Calculate length in bytes
        int len = io.netty.buffer.ByteBufUtil.utf8Bytes(s);

        // 2. Write length (as standard int, allows > 65k chars)
        buf.writeInt(len);

        // 3. Write string
        buf.writeCharSequence(s, CharsetUtil.UTF_8);
    }

    public static String readUTF(ByteBuf buf) {
        // 1. Read length
        int len = buf.readInt();

        // 2. Read string
        String s = buf.readCharSequence(len, CharsetUtil.UTF_8).toString();
        return s;
    }

    /**
     * Safely chunks and writes massive file streams over Netty.
     */
    public static void writeStream(Channel channel, InputStream in) throws IOException {
        Base64.Encoder encoder = Base64.getEncoder();
        byte[] buffer = new byte[8192];
        int count;

        while ((count = in.read(buffer)) != -1) {
            // Note: Base64 expands data by ~33%.
            // 8192 bytes input -> approx 10924 bytes output.
            // DataOutputStream.writeUTF supports up to 65535 bytes, so this fits safely.

            byte[] chunk = Arrays.copyOf(buffer, count);
            String base64 = new String(encoder.encode(chunk), StandardCharsets.UTF_8);

            ByteBuf buf = channel.alloc().buffer();
            writeUTF(buf, base64);
            channel.writeAndFlush(buf);
        }

        ByteBuf eofBuf = channel.alloc().buffer();
        writeUTF(eofBuf, EOF);
        channel.writeAndFlush(eofBuf);
    }
}