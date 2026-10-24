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
import org.jetbrains.annotations.Nullable;

import java.security.InvalidKeyException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class DefaultConnection implements AutoCloseable {
    public static boolean DEBUG = false;
    public static ExecutorService exec = Executors.newCachedThreadPool();

    public static final String NO_KEY = "NO_KEY";
    public static final EventLoopGroup GROUP = new NioEventLoopGroup(2);

    public final byte conType;
    public byte errorCode = 0;
    public Channel channel;
    public AtomicBoolean isClosing = new AtomicBoolean(false);
    private boolean useSsl = false;

    @Nullable public ConMain conMain = null;
    public boolean isSecondary(){
        return conMain != null;
    }

    public Thread reconnectThread = null;

    public DefaultConnection(byte con_type) {
        this.conType = con_type;
    }

    protected void initConnectionPipeline(SocketChannel ch) throws Exception {
        // Subclasses can install protocol handlers before authentication completes.
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

    public int initalReconnectDelay = 1000;
    public int additionalReconnectDelay = 30000;
    public int currentReconnectDelay = initalReconnectDelay;
    protected int getNewReconnectDelay() {
        int delay = currentReconnectDelay;
        currentReconnectDelay = currentReconnectDelay + additionalReconnectDelay; // For next time
        return delay;
    }

    public volatile boolean isReconnectAllowed = true;
    public volatile boolean isReconnectAllowedWhenUserIsOffline = false;

    protected synchronized void scheduleReconnect() {
        if (reconnectThread != null) {
            try { reconnectThread.interrupt(); } catch (Exception ignored) {}
        }

        if (isClosing.get()) return;
        if (!isReconnectAllowed) return;
        if (!isReconnectAllowedWhenUserIsOffline){
            if(conMain != null && !conMain.isUserActive.get()) return; // Dont try reconnect when the user is offline anyways
        }

        reconnectThread = new Thread(() -> {
            try {
                int delay = getNewReconnectDelay();

                // Wait a least 2 seconds so that the user inactivity ping reaches the main con
                // and re-check before actually trying a reconnect to avoid wrong reconnects in this situation
                AL.debug(this.getClass(), "Connection closed, might be a valid close. Waiting 5 seconds before attempting actual reconnect...");
                Thread.sleep(5000);
                if (!isReconnectAllowedWhenUserIsOffline){
                    if(conMain != null && !conMain.isUserActive.get()) return; // Dont try reconnect when the user is offline anyways
                }

                AL.warn(this.getClass().getSimpleName()+ " Connection problems! Reconnecting in " + delay / 1000 + " seconds...");
                Thread.sleep(delay);

                if(conMain != null // same as isSecondary()
                        && !conMain.isConnected()){
                    AL.debug(this.getClass(), "Reconnect attempt aborted," +
                            " because main con is not connected. Assume reconnect will be handled by main con.");
                    return;
                }

                if (!open()) scheduleReconnect();
            } catch (InterruptedException e){
                // Do nothing on interrupt since its planned
                AL.debug(this.getClass(),"Interrupted reconnect thread.");
            }
            catch (Exception e) {
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

        Object _this = this;

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

                        initConnectionPipeline(ch);

                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg)
                                    throws Exception {
                                if (msg instanceof ByteBuf) {
                                    ByteBuf buf = (ByteBuf) msg;

                                    if(DEBUG) AL.info("["+_this.getClass().getSimpleName()+"] inbound ByteBuf; readableBytes="
                                            + buf.readableBytes()
                                            + "; firstByte="
                                            + (buf.isReadable()
                                            ? (buf.getByte(buf.readerIndex()) & 0xFF)
                                            : -1));
                                } else {
                                    if(DEBUG) AL.info("["+_this.getClass().getSimpleName()+"] inbound message type="
                                            + msg.getClass().getName());
                                }

                                super.channelRead(ctx, msg);
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx)
                                    throws Exception {
                                if(DEBUG) AL.info("["+_this.getClass().getSimpleName()+"] channelInactive");

                                if (!isClosing.get()) {
                                    scheduleReconnect();
                                }

                                super.channelInactive(ctx);
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                if(DEBUG) AL.info("["+_this.getClass().getSimpleName()+"] exceptionCaught: " + cause);

                                if (!isClosing.get()) {
                                    scheduleReconnect();
                                }
                            }
                        });
                    }
                });

        try {
            channel = b.connect(ip, port).sync().channel();
            this.errorCode = authFuture.get(60, TimeUnit.SECONDS);
            currentReconnectDelay = initalReconnectDelay; // Reset reconnect delay on success connect
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
        boolean connected = isConnected();
        boolean secondary = isSecondary();
        boolean closing = isClosing.get();

        boolean chActive = channel != null && channel.isActive();
        boolean chOpen = channel != null && channel.isOpen();
        boolean chWritable = channel != null && channel.isWritable();

        return getClass().getSimpleName() +
                "@" + Integer.toHexString(System.identityHashCode(this)) +

                " " + (useSsl ? "ssl" : "!ssl") +
                " " + (connected ? "connected" : "!connected") +
                " " + (closing ? "closing" : "!closing") +

                " " + (chActive ? "chActive" : "!chActive") +
                " " + (chOpen ? "chOpen" : "!chOpen") +
                " " + (chWritable ? "chWritable" : "!chWritable") +

                " err=" + errorCode +

                " rDelay=" + currentReconnectDelay +
                " rThread=" + (reconnectThread != null ? reconnectThread.getState() : "null");
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