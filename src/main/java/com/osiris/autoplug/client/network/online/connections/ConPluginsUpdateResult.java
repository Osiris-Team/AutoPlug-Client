/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;

import java.net.Socket;
import java.util.List;

/**
 * This is a temporary connection, which gets closed after
 * finishing its tasks.
 * It starts a {@link BThread} which is attached to the given {@link BThreadManager} (or creates a new Manager if null).
 */
public class ConPluginsUpdateResult extends DefaultConnection {
    private final List<SearchResult> searchResults;
    private final List<MinecraftPlugin> excludedPlugins;

    /**
     * To send the provided information to AutoPlug-Web make sure to call {@link #open()}.
     *
     * @param searchResults   results from checking the included plugins.
     * @param excludedPlugins plugins that were excluded from the check.
     */
    public ConPluginsUpdateResult(List<SearchResult> searchResults,
                                  List<MinecraftPlugin> excludedPlugins) {
        super((byte) 3);
        this.searchResults = searchResults;
        this.excludedPlugins = excludedPlugins;
    }

    @Override
    public boolean open() throws Exception {
        super.open();
        Socket socket = getSocket();
        socket.setSoTimeout(0);
        sendResultsOfPluginCheck();
        return true;
    }


    private void sendResultsOfPluginCheck() throws Exception {

        long msLeft = in.readLong(); // 0 if the last plugins check was over 4 hours ago, else it returns the time left, till a new check is allowed
        if (msLeft != 0)
            throw new Exception("Failed to send update check result to web. Web cool-down is still active (" + (msLeft / 60000) + " minutes remaining).");

        out.writeInt(searchResults.size());
        for (SearchResult result :
                searchResults) {
            if (result.getPlugin().getName() == null)
                out.writeUTF("null");
            else
                out.writeUTF(result.getPlugin().getName());

            if (result.getPlugin().getAuthor() == null)
                out.writeUTF("null");
            else
                out.writeUTF(result.getPlugin().getAuthor());

            if (result.getPlugin().getVersion() == null)
                out.writeUTF("null");
            else
                out.writeUTF(result.getPlugin().getVersion());

            out.writeByte(result.getResultCode());

            if (result.getDownloadType() == null)
                out.writeUTF("null");
            else
                out.writeUTF(result.getDownloadType());

            if (result.getLatestVersion() == null)
                out.writeUTF("null");
            else
                out.writeUTF(result.getLatestVersion());

            if (result.getDownloadUrl() == null)
                out.writeUTF("null");
            else
                out.writeUTF(result.getDownloadUrl());

            if (result.getSpigotId() == null)
                out.writeUTF("0");
            else
                out.writeUTF(result.getSpigotId());

            if (result.getBukkitId() == null)
                out.writeUTF("0");
            else
                out.writeUTF(result.getBukkitId());

            if (result.plugin.getGithubRepoName() == null)
                out.writeUTF("null");
            else
                out.writeUTF(result.plugin.getGithubRepoName());

            if (result.plugin.getGithubAssetName() == null)
                out.writeUTF("null");
            else
                out.writeUTF(result.plugin.getGithubAssetName());

            if (result.plugin.getJenkinsProjectUrl() == null)
                out.writeUTF("null");
            else
                out.writeUTF(result.plugin.getJenkinsProjectUrl());

            if (result.plugin.getJenkinsArtifactName() == null)
                out.writeUTF("null");
            else
                out.writeUTF(result.plugin.getJenkinsArtifactName());
        }

        out.writeInt(excludedPlugins.size());
        for (MinecraftPlugin excludedPl :
                excludedPlugins) {
            String plName = excludedPl.getName();
            if (plName == null)
                out.writeUTF("null");
            else
                out.writeUTF(plName);

            String plAuthor = excludedPl.getAuthor();
            if (plAuthor == null)
                out.writeUTF("null");
            else
                out.writeUTF(plAuthor);

            String plVersion = excludedPl.getVersion();
            if (plAuthor == null)
                out.writeUTF("null");
            else
                out.writeUTF(plVersion);
        }

    }
}
