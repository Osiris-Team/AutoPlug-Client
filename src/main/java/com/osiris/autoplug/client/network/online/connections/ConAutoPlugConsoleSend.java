package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.jlib.events.MessageEvent;
import com.osiris.jlib.logger.AL;
import com.osiris.jlib.logger.Message;
import com.osiris.jlib.logger.MessageFormatter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.nio.charset.StandardCharsets;

public class ConAutoPlugConsoleSend extends DefaultConnection {
    private static Channel staticChannel;

    public static boolean isDebug;
    static {
        try {
            isDebug = new LoggerConfig().debug.asBoolean();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ConAutoPlugConsoleSend() { super((byte) 2); }

    public static final MessageEvent<Message> onMessageEvent = message -> {
        try { send(MessageFormatter.formatForAnsiConsole(message)); }
        catch (Exception e) { AL.warn("Failed to send message!", e); }
    };

    public static void send(String message) {
        if (staticChannel != null && staticChannel.isActive()) {
            String finalMsg = message.contains(System.lineSeparator()) ? message : message + "\n";
            ByteBuf buf = staticChannel.alloc().buffer();
            buf.writeCharSequence(finalMsg, StandardCharsets.UTF_8);
            staticChannel.writeAndFlush(buf);
        }
    }

    @Override
    public boolean open() throws Exception {
        if (!new WebConfig().online_console.asBoolean()) return false;
        super.open();
        staticChannel = this.channel;
        if (!AL.actionsOnMessageEvent.contains(onMessageEvent)) AL.actionsOnMessageEvent.add(onMessageEvent);
        return true;
    }

    @Override
    public void close() {
        try { AL.actionsOnMessageEvent.remove(onMessageEvent); } catch (Exception ignored) {}
        super.close();
    }
}