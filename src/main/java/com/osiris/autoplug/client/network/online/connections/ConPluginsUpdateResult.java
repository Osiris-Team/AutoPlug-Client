package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.autoplug.client.network.online.NettyUtils;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.jlib.logger.AL;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.List;

public class ConPluginsUpdateResult extends DefaultConnection {
    public static boolean DEBUG = false;

    private final List<SearchResult> searchResults;
    private final List<MinecraftPlugin> excludedPlugins;

    public ConPluginsUpdateResult(
            List<SearchResult> searchResults,
            List<MinecraftPlugin> excludedPlugins
    ) {
        super((byte) 3);
        this.searchResults = searchResults;
        this.excludedPlugins = excludedPlugins;
        this.isReconnectAllowed = false; // No reconnect attempts
    }

    @Override
    public boolean open() throws Exception {
        if(DEBUG) AL.info("ConPluginsUpdateResult: Opening one-shot connection. Plugins="
                + searchResults.size()
                + ", excludedPlugins=" + excludedPlugins.size());

        boolean opened = super.open();

        if(DEBUG) AL.info("ConPluginsUpdateResult: super.open() returned " + opened);
        return true;
    }

    @Override
    protected void initConnectionPipeline(SocketChannel ch) {
        if(DEBUG) AL.info("ConPluginsUpdateResult: Installing response decoder. Channel=" + ch);

        ch.pipeline().addLast(new ReplayingDecoder<Void>() {
            @Override
            protected void decode(
                    ChannelHandlerContext ctx,
                    ByteBuf in,
                    List<Object> outList
            ) throws Exception {
                if(DEBUG) AL.info("ConPluginsUpdateResult: Received server response. Readable bytes="
                        + in.readableBytes());

                long msLeft = in.readLong();

                if(DEBUG) AL.info("ConPluginsUpdateResult: Server cooldown remaining="
                        + msLeft + " ms");

                if (msLeft != 0) {
                    if(DEBUG) AL.info("ConPluginsUpdateResult: Server cooldown active; closing connection.");
                    throw new Exception(
                            "Web cool-down is still active ("
                                    + (msLeft / 60000)
                                    + " minutes remaining)."
                    );
                }

                ByteBuf out = ctx.alloc().buffer();

                out.writeInt(searchResults.size());
                if(DEBUG) AL.info("ConPluginsUpdateResult: Serializing "
                        + searchResults.size() + " plugin search results.");

                for (SearchResult r : searchResults) {
                    NettyUtils.writeUTF(out, safeStr(r.getPlugin().getName()));
                    NettyUtils.writeUTF(out, safeStr(r.getPlugin().getAuthor()));
                    NettyUtils.writeUTF(out, safeStr(r.getPlugin().getVersion()));
                    out.writeByte(r.type.id);
                    NettyUtils.writeUTF(out, safeStr(r.getDownloadType()));
                    NettyUtils.writeUTF(out, safeStr(r.getLatestVersion()));
                    NettyUtils.writeUTF(out, safeStr(r.getDownloadUrl()));
                    NettyUtils.writeUTF(out,
                            r.getSpigotId() == null ? "0" : r.getSpigotId());
                    NettyUtils.writeUTF(out,
                            r.getBukkitId() == null ? "0" : r.getBukkitId());
                    NettyUtils.writeUTF(out,
                            safeStr(r.plugin.getGithubRepoName()));
                    NettyUtils.writeUTF(out,
                            safeStr(r.plugin.getGithubAssetName()));
                    NettyUtils.writeUTF(out,
                            safeStr(r.plugin.getJenkinsProjectUrl()));
                    NettyUtils.writeUTF(out,
                            safeStr(r.plugin.getJenkinsArtifactName()));
                }

                out.writeInt(excludedPlugins.size());
                if(DEBUG) AL.info("ConPluginsUpdateResult: Serializing "
                        + excludedPlugins.size() + " excluded plugins.");

                for (MinecraftPlugin ex : excludedPlugins) {
                    NettyUtils.writeUTF(out, safeStr(ex.getName()));
                    NettyUtils.writeUTF(out, safeStr(ex.getAuthor()));
                    NettyUtils.writeUTF(out, safeStr(ex.getVersion()));
                }

                if(DEBUG) AL.info("ConPluginsUpdateResult: Response serialized. Bytes="
                        + out.readableBytes()
                        + ". Writing response to server.");

                ctx.writeAndFlush(out).addListener(future -> {
                    if (future.isSuccess()) {
                        if(DEBUG) AL.info("ConPluginsUpdateResult: Response write succeeded. Closing one-shot connection.");
                    } else {
                        if(DEBUG) AL.info("ConPluginsUpdateResult: Response write failed: "
                                + future.cause());
                    }

                    close();
                });

                ctx.pipeline().remove(this);
                if(DEBUG) AL.info("ConPluginsUpdateResult: Response decoder removed from pipeline.");
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                if(DEBUG) AL.info("ConPluginsUpdateResult: Decoder error; closing connection. Cause="
                        + cause);

                close();
            }
        });
    }

    private String safeStr(String s) {
        return s == null ? "null" : s;
    }
}