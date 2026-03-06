package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.configs.SystemConfig;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.jlib.logger.AL;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.security.InvalidKeyException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class DefaultConnection implements AutoCloseable {
    public static final String NO_KEY = "NO_KEY";
    public static final EventLoopGroup GROUP = new NioEventLoopGroup(2);

    public final byte conType;
    public byte errorCode = 0;
    public Channel channel;
    public AtomicBoolean isClosing = new AtomicBoolean(false);
    private boolean useSsl = false;

    public Thread reconnectThread = null;

    public DefaultConnection(byte con_type) {
        this.conType = con_type;
    }

    public synchronized boolean open() throws Exception {
        AL.debug(this.getClass(), "open()");
        try {
            _open();
        } catch (Exception e) {
            if (errorCode == 2) {
                AL.debug(this.getClass(), "Server reports duplicate key. Waiting 10s for old session to clear...");
                Thread.sleep(10000);
                _open();
            } else {
                throw e;
            }
        }
        throwError();
        return errorCode == 0;
    }

    protected int getReconnectDelay() {
        return 30000;
    }

    protected synchronized void scheduleReconnect() {
        if (isClosing.get()) return;

        if (reconnectThread != null) {
            try { reconnectThread.interrupt(); } catch (Exception ignored) {}
        }

        reconnectThread = new Thread(() -> {
            try {
                int delay = getReconnectDelay();
                AL.warn(this.getClass().getSimpleName()+ " Connection problems! Reconnecting in " + delay / 1000 + " seconds...");
                Thread.sleep(delay);

                if (!open()) scheduleReconnect();
            } catch (Exception e) {
                AL.warn("Reconnect error", e);
            }
        });

        reconnectThread.start();
        close();
    }

    private synchronized int _open() throws Exception {
        isClosing.set(false);
        errorCode = 0;

        if (channel != null && channel.isActive()) {
            try { channel.close().sync(); } catch (Exception ignored) {}
        }

        String serverKey = new GeneralConfig().server_key.asString();
        if (serverKey == null || serverKey.equals("INSERT_KEY_HERE") || serverKey.equals("null") || serverKey.equals(NO_KEY))
            throw new InvalidKeyException("No valid key provided. Register your server at " + GD.OFFICIAL_WEBSITE);

        serverKey = serverKey.trim();
        final String finalKey = serverKey;

        SystemConfig systemConfig = new SystemConfig();
        String ip = systemConfig.autoplug_web_ip.asString();
        int port = systemConfig.autoplug_web_port.asInt();
        useSsl = systemConfig.autoplug_web_ssl.asBoolean();

        AL.debug(this.getClass(), "[CON_TYPE: " + conType + "] Connecting to AutoPlug-Web (" + ip + ":" + port + ")...");

        CompletableFuture<Byte> authFuture = new CompletableFuture<>();

        Bootstrap b = new Bootstrap();
        b.group(GROUP)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {

                        if (useSsl) {
                            SslContext sslCtx = SslContextBuilder.forClient()
                                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                    .build();
                            ch.pipeline().addLast(sslCtx.newHandler(ch.alloc(), ip, port));
                        }

                        ch.pipeline().addLast(new AuthHandler(finalKey, conType, authFuture));

                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                if (!isClosing.get()) scheduleReconnect();
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                if (!isClosing.get()) scheduleReconnect();
                            }
                        });
                    }
                });

        try {
            channel = b.connect(ip, port).sync().channel();
            this.errorCode = authFuture.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Throttled")) {
                int punishment = Integer.parseInt(e.getMessage().split(":")[1].trim());
                AL.debug(this.getClass(), "[CON_TYPE: " + conType + "] Throttled! Retrying in " + punishment / 1000 + "s.");
                Thread.sleep(punishment + 250);
                return _open();
            }
            throw e;
        }

        return errorCode;
    }

    private void throwError() throws Exception {
        if (errorCode == 0) AL.debug(this.getClass(), "[CON_TYPE: " + conType + "] Authenticated server successfully!");
        else if (errorCode == 1) throw new Exception("Authentication failed: No matching server key found!");
        else if (errorCode == 2) throw new Exception("Authentication failed: Another client with this key is already connected!");
        else if (errorCode == 3) throw new Exception("Authentication failed: Main-Con must be established first!");
        else throw new Exception("Authentication failed with unknown error code: " + errorCode);
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    @Override
    public synchronized void close() {
        AL.debug(this.getClass(), "close()");
        isClosing.set(true);
        if (channel != null && channel.isActive()) {
            channel.close();
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "isEncrypted=" + useSsl +
                ", isConnected=" + isConnected() +
                ", isClosing=" + isClosing.get() +
                ", errorCode=" + errorCode +
                '}';
    }

    public static class AuthHandler extends ByteToMessageDecoder {
        private final String key;
        private final byte type;
        private final CompletableFuture<Byte> future;
        private boolean sentKey = false;

        public AuthHandler(String key, byte type, CompletableFuture<Byte> future) {
            this.key = key;
            this.type = type;
            this.future = future;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

            if (!sentKey) {
                if (in.readableBytes() < 4) return;

                int punishment = in.readInt();
                if (punishment > 0) {
                    future.completeExceptionally(new Exception("Throttled:" + punishment));
                    ctx.close();
                    return;
                }

                ByteBuf buf = ctx.alloc().buffer();
                NettyUtils.writeUTF(buf, key);
                buf.writeByte(type);
                ctx.writeAndFlush(buf);

                sentKey = true;
            } else {
                if (in.readableBytes() < 1) return;

                byte error = in.readByte();
                ctx.pipeline().remove(this);
                future.complete(error);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            future.completeExceptionally(cause);
            ctx.close();
        }
    }
}