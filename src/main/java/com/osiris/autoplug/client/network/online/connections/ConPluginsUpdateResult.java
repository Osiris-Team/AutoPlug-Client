package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.autoplug.client.network.online.NettyUtils;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.List;

public class ConPluginsUpdateResult extends DefaultConnection {
    private final List<SearchResult> searchResults;
    private final List<MinecraftPlugin> excludedPlugins;

    public ConPluginsUpdateResult(List<SearchResult> searchResults, List<MinecraftPlugin> excludedPlugins) {
        super((byte) 3);
        this.searchResults = searchResults;
        this.excludedPlugins = excludedPlugins;
    }

    @Override
    public boolean open() throws Exception {
        super.open();

        channel.pipeline().addLast(new ReplayingDecoder<Void>() {
            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> outList) throws Exception {
                long msLeft = in.readLong();
                if (msLeft != 0) {
                    throw new Exception("Web cool-down is still active (" + (msLeft / 60000) + " minutes remaining).");
                }

                ByteBuf out = ctx.alloc().buffer();
                out.writeInt(searchResults.size());
                for (SearchResult r : searchResults) {
                    NettyUtils.writeUTF(out, safeStr(r.getPlugin().getName()));
                    NettyUtils.writeUTF(out, safeStr(r.getPlugin().getAuthor()));
                    NettyUtils.writeUTF(out, safeStr(r.getPlugin().getVersion()));
                    out.writeByte(r.type.id);
                    NettyUtils.writeUTF(out, safeStr(r.getDownloadType()));
                    NettyUtils.writeUTF(out, safeStr(r.getLatestVersion()));
                    NettyUtils.writeUTF(out, safeStr(r.getDownloadUrl()));
                    NettyUtils.writeUTF(out, r.getSpigotId() == null ? "0" : r.getSpigotId());
                    NettyUtils.writeUTF(out, r.getBukkitId() == null ? "0" : r.getBukkitId());
                    NettyUtils.writeUTF(out, safeStr(r.plugin.getGithubRepoName()));
                    NettyUtils.writeUTF(out, safeStr(r.plugin.getGithubAssetName()));
                    NettyUtils.writeUTF(out, safeStr(r.plugin.getJenkinsProjectUrl()));
                    NettyUtils.writeUTF(out, safeStr(r.plugin.getJenkinsArtifactName()));
                }

                out.writeInt(excludedPlugins.size());
                for (MinecraftPlugin ex : excludedPlugins) {
                    NettyUtils.writeUTF(out, safeStr(ex.getName()));
                    NettyUtils.writeUTF(out, safeStr(ex.getAuthor()));
                    NettyUtils.writeUTF(out, safeStr(ex.getVersion()));
                }

                ctx.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);
                ctx.pipeline().remove(this);
            }
        });
        return true;
    }

    private String safeStr(String s) { return s == null ? "null" : s; }
}