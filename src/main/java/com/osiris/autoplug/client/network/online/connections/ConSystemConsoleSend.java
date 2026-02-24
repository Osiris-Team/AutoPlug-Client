package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.terminal.AsyncTerminal;
import com.osiris.jlib.logger.AL;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class ConSystemConsoleSend extends DefaultConnection {
    public static AsyncTerminal asyncTerminal;
    public static BufferedWriter asyncTerminalLogWriter;

    private static Channel staticChannel;

    public ConSystemConsoleSend() {
        super((byte) 7);
    }

    public static void send(@NotNull String message) {
        try {
            if (asyncTerminalLogWriter != null) {
                asyncTerminalLogWriter.write(message + "\n");
                asyncTerminalLogWriter.flush();
            }
        } catch (Exception e) {
            AL.warn("Failed to write to " + GD.SYSTEM_LATEST_LOG, e);
        }

        try {
            if (staticChannel != null && staticChannel.isActive()) {
                String finalMsg = message.contains(System.lineSeparator()) ? message : message + "\n";
                ByteBuf buf = staticChannel.alloc().buffer();
                buf.writeCharSequence(finalMsg, StandardCharsets.UTF_8);
                staticChannel.writeAndFlush(buf);
            }
        } catch (Exception ignored) { }
    }

    @Override
    public boolean open() throws Exception {
        if (!new WebConfig().online_system_console.asBoolean()) {
            AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' not connected, because not enabled in the web-config.");
            return false;
        }

        super.open();
        staticChannel = this.channel;

        if (asyncTerminal != null) asyncTerminal.close();
        if (asyncTerminalLogWriter != null) asyncTerminalLogWriter.close();
        if (GD.SYSTEM_LATEST_LOG.exists()) GD.SYSTEM_LATEST_LOG.delete();
        GD.SYSTEM_LATEST_LOG.getParentFile().mkdirs();
        GD.SYSTEM_LATEST_LOG.createNewFile();
        asyncTerminalLogWriter = new BufferedWriter(new FileWriter(GD.SYSTEM_LATEST_LOG));

        send("Connected to AutoPlug-Web at " + new Date());
        send("Current working directory: " + GD.WORKING_DIR);

        asyncTerminal = new AsyncTerminal(null, ConSystemConsoleSend::send, ConSystemConsoleSend::send);

        AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' connected.");
        return true;
    }

    @Override
    public void close() {
        try { if (asyncTerminal != null) asyncTerminal.close(); } catch (Exception ignored) { }
        try { if (asyncTerminalLogWriter != null) asyncTerminalLogWriter.close(); } catch (Exception ignored) { }
        super.close();
    }
}