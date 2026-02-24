package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.console.Commands;
import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.jlib.logger.AL;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ConAutoPlugConsoleReceive extends DefaultConnection {
    public ConAutoPlugConsoleReceive() { super((byte) 1); }

    @Override
    public boolean open() throws Exception {
        if (!new WebConfig().online_console.asBoolean()) return false;
        super.open();

        channel.pipeline().addLast(new LineBasedFrameDecoder(10000));
        channel.pipeline().addLast(new StringDecoder(StandardCharsets.UTF_8));
        channel.pipeline().addLast(new SimpleChannelInboundHandler<String>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, String line) {
                if (!Commands.execute(line)) {
                    Thread.startVirtualThread(() -> {
                        try {
                            Server.submitCommand(line);
                        } catch (Exception e) {
                            AL.warn(e);
                        }
                    });
                }
            }
        });

        return true;
    }
}