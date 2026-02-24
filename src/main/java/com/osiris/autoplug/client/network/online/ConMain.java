package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.network.online.connections.*;
import com.osiris.jlib.logger.AL;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.timeout.IdleStateHandler;

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
    public int msUntilRetry = 30000;

    public ConMain() {
        super((byte) 0);
    }

    @Override
    public boolean open() {
        try {
            AL.info("Authenticating server...");
            super.open();
            AL.info("Authentication success!");
            CON_PUBLIC_DETAILS.open();
            msUntilRetry = 30000;
            isDone = true;
        } catch (Exception e) {
            isDone = true;
            AL.warn(e);
            if (e instanceof InvalidKeyException) return false;
            scheduleReconnect();
            return false;
        }

        // Add IdleStateHandler FIRST: Wait some seconds for a read. (0 means disable write/all idle checking)
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
                        // Offload to a virtual thread so Netty can keep working
                        Thread.startVirtualThread(() -> {
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

    private void scheduleReconnect() {
        if (isClosing.get()) return;
        new Thread(() -> {
            try {
                AL.warn("Connection problems! Reconnecting in " + msUntilRetry / 1000 + " seconds...");
                Thread.sleep(msUntilRetry);
                msUntilRetry += 30000;
                if (!open()) scheduleReconnect();
            } catch (Exception e) {
                AL.warn("Reconnect error", e);
            }
        }).start();
        close();
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