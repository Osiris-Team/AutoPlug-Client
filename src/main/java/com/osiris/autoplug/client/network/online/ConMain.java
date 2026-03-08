package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.network.online.connections.*;
import com.osiris.jlib.logger.AL;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.jetbrains.annotations.Nullable;

import java.security.InvalidKeyException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConMain extends DefaultConnection {
    public final ConSendPublicDetails CON_PUBLIC_DETAILS = new ConSendPublicDetails();
    public final ConAutoPlugConsoleReceive CON_CONSOLE_RECEIVE = new ConAutoPlugConsoleReceive();
    public final ConAutoPlugConsoleSend CON_CONSOLE_SEND = new ConAutoPlugConsoleSend();
    public final ConSystemConsoleSend CON_SYSTEM_CONSOLE_SEND = new ConSystemConsoleSend();
    public final ConSystemConsoleReceive CON_SYSTEM_CONSOLE_RECEIVE = new ConSystemConsoleReceive();
    public final ConSendPrivateDetails CON_PRIVATE_DETAILS = new ConSendPrivateDetails();
    public final ConFileManager CON_FILE_MANAGER = new ConFileManager();

    public boolean isDone = false; // So that the log isn't a mess because of the processes which start right after this.
    public AtomicBoolean isUserActive = new AtomicBoolean(false);
    public boolean isUserActiveOld = false;

    public ConMain() {
        super((byte) 0);
        CON_PUBLIC_DETAILS.conMain = this;
        CON_CONSOLE_RECEIVE.conMain = this;
        CON_CONSOLE_SEND.conMain = this;
        CON_SYSTEM_CONSOLE_SEND.conMain = this;
        CON_SYSTEM_CONSOLE_RECEIVE.conMain = this;
        CON_PRIVATE_DETAILS.conMain = this;
        CON_FILE_MANAGER.conMain = this;
    }

    @Override
    public boolean open() {
        try {
            AL.info("Authenticating server...");
            super.open();
            AL.info("Authentication success!");
            CON_PUBLIC_DETAILS.open();
            isDone = true;
        } catch (Exception e) {
            isDone = true;
            AL.warn(e);
            if (e instanceof InvalidKeyException) return false;
            scheduleReconnect();
            return false;
        }

        channel.pipeline().addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS));
        channel.pipeline().addLast(new ReplayingDecoder<Void>() {
            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                boolean active = in.readBoolean();
                isUserActive.set(active);

                ByteBuf pong = ctx.alloc().buffer(1);
                pong.writeBoolean(true);
                ctx.writeAndFlush(pong);

                try {
                    if (active && !isUserActiveOld) {
                        AL.debug(ConMain.class, "Owner/Staff is online/active.");
                        DefaultConnection.exec.submit(() -> {
                            try {
                                if (!CON_CONSOLE_RECEIVE.isConnected()) CON_CONSOLE_RECEIVE.open();
                                if (!CON_CONSOLE_SEND.isConnected()) CON_CONSOLE_SEND.open();
                                if (!CON_SYSTEM_CONSOLE_RECEIVE.isConnected()) CON_SYSTEM_CONSOLE_RECEIVE.open();
                                if (!CON_SYSTEM_CONSOLE_SEND.isConnected()) CON_SYSTEM_CONSOLE_SEND.open();
                                if (!CON_FILE_MANAGER.isConnected()) CON_FILE_MANAGER.open();
                                if (!CON_PRIVATE_DETAILS.isConnected()) CON_PRIVATE_DETAILS.open();
                            } catch (Exception e) {
                                AL.warn(e);
                            }
                        });
                    } else if (!active && isUserActiveOld) {
                        AL.debug(ConMain.class, "Owner/Staff is offline/inactive.");
                        closeTempCons();
                    }
                    isUserActiveOld = active;
                } catch (Exception e) {
                    AL.warn(e);
                }
                checkpoint();
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof IdleStateEvent) {
                    AL.warn("Server connection timed out (no ping received). Assuming connection is dead.");
                    // Closing the context will automatically trigger channelInactive() below and schedule a reconnect
                    ctx.close();
                } else {
                    super.userEventTriggered(ctx, evt);
                }
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                if (!isClosing.get()) scheduleReconnect();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                if (!isClosing.get()) scheduleReconnect();
            }
        });

        return true;
    }

    @Override
    public void close() {
        closeTempCons();
        closePermCons();
    }

    public void closePermCons() {
        isUserActiveOld = false;
        isUserActive.set(false);
        super.close();
        try { if (CON_PUBLIC_DETAILS.isConnected()) CON_PUBLIC_DETAILS.close(); } catch (Exception ignored) {}
    }

    public void closeTempCons() {
        try { if (CON_CONSOLE_RECEIVE.isConnected()) CON_CONSOLE_RECEIVE.close(); } catch (Exception ignored) {}
        try { if (CON_CONSOLE_SEND.isConnected()) CON_CONSOLE_SEND.close(); } catch (Exception ignored) {}
        try { if (CON_PRIVATE_DETAILS.isConnected()) CON_PRIVATE_DETAILS.close(); } catch (Exception ignored) {}
        try { if (CON_FILE_MANAGER.isConnected()) CON_FILE_MANAGER.close(); } catch (Exception ignored) {}
        try { if (CON_SYSTEM_CONSOLE_RECEIVE.isConnected()) CON_SYSTEM_CONSOLE_RECEIVE.close(); } catch (Exception ignored) {}
        try { if (CON_SYSTEM_CONSOLE_SEND.isConnected()) CON_SYSTEM_CONSOLE_SEND.close(); } catch (Exception ignored) {}
    }
}