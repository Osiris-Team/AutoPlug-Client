package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.jlib.logger.AL;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

import java.nio.charset.StandardCharsets;

public class ConSystemConsoleReceive extends DefaultConnection {

    public ConSystemConsoleReceive() {
        super((byte) 8);
    }

    @Override
    public boolean open() throws Exception {
        if (!new WebConfig().online_system_console.asBoolean()) {
            AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' not connected, because not enabled in the web-config.");
            return false;
        }

        super.open();

        channel.pipeline().addLast(new LineBasedFrameDecoder(10000));
        channel.pipeline().addLast(new StringDecoder(StandardCharsets.UTF_8));
        channel.pipeline().addLast(new SimpleChannelInboundHandler<String>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, String line) {
                AL.info("Received Web-Command for S-Console: " + line);
                if (ConSystemConsoleSend.asyncTerminal == null) {
                    AL.warn("Failed to execute '" + line + "' because there is no system terminal active.");
                    return;
                }
                Thread.startVirtualThread(() -> {
                    ConSystemConsoleSend.asyncTerminal.sendCommands(line);
                });
            }
        });

        AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' connected.");
        return true;
    }
}