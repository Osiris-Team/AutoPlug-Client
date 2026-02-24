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
    public static final EventLoopGroup GROUP = new NioEventLoopGroup(2); // Shared event loop

    public final byte conType;
    public byte errorCode = 0;
    public Channel channel;
    public AtomicBoolean isClosing = new AtomicBoolean(false);
    private boolean useSsl = false;

    public DefaultConnection(byte con_type) {
        this.conType = con_type;
    }

    public synchronized boolean open() throws Exception {
        AL.debug(this.getClass(), "open()");
        try {
            _open();
        } catch (Exception e) {
            // If the server thinks we are a duplicate (error 2), wait and retry once.
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

    private synchronized int _open() throws Exception {
        isClosing.set(false);
        errorCode = 0;

        // FIX: Ensure previous channel is completely closed synchronously before starting a new one.
        if (channel != null && channel.isActive()) {
            try {
                channel.close().sync();
            } catch (Exception ignored) {}
        }

        String serverKey = new GeneralConfig().server_key.asString();
        if (serverKey == null || serverKey.equals("INSERT_KEY_HERE") || serverKey.equals("null") || serverKey.equals(NO_KEY))
            throw new InvalidKeyException("No valid key provided. Register your server at " + GD.OFFICIAL_WEBSITE);

        // FIX: Trim whitespace to prevent invalid key errors
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
                            SslContext sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                            ch.pipeline().addLast(sslCtx.newHandler(ch.alloc(), ip, port));
                        }
                        // FIX: Replaced ReplayingDecoder with robust AuthHandler
                        ch.pipeline().addLast(new AuthHandler(finalKey, conType, authFuture));
                    }
                });

        try {
            channel = b.connect(ip, port).sync().channel();
            this.errorCode = authFuture.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Check for Throttling message passed from AuthHandler
            if (e.getMessage() != null && e.getMessage().contains("Throttled")) {
                int punishment = Integer.parseInt(e.getMessage().split(":")[1].trim());
                AL.debug(this.getClass(), "[CON_TYPE: " + conType + "] Throttled! Retrying in " + punishment / 1000 + "s.");
                Thread.sleep(punishment + 250);
                return _open(); // Retry
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

    /**
     * Handshake Handler:
     * 1. Reads Punishment (Int)
     * 2. Writes Key (UTF) + Type (Byte)
     * 3. Reads ErrorCode (Byte)
     */
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
                // Step 1: Read Punishment Integer (4 bytes)
                if (in.readableBytes() < 4) return;

                int punishment = in.readInt();
                if (punishment > 0) {
                    future.completeExceptionally(new Exception("Throttled:" + punishment));
                    ctx.close();
                    return;
                }

                // Step 2: Send Key + Type
                ByteBuf buf = ctx.alloc().buffer();
                NettyUtils.writeUTF(buf, key);
                buf.writeByte(type);
                ctx.writeAndFlush(buf);

                sentKey = true;
            } else {
                // Step 3: Read Error Code Byte (1 byte)
                if (in.readableBytes() < 1) return;

                byte error = in.readByte();
                ctx.pipeline().remove(this); // Handshake complete
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