/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.network.online.SecondaryConnection;
import com.osiris.autoplug.client.tasks.updater.plugins.DetailedPlugin;
import com.osiris.autoplug.client.tasks.updater.plugins.search.SearchResult;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.List;

/**
 * This is a temporary connection, which gets closed after
 * finishing its tasks.
 * It starts a {@link BetterThread} which is attached to the given {@link BetterThreadManager} (or creates a new Manager if null).
 */
public class PluginsUpdateResultConnection extends SecondaryConnection {
    private final List<SearchResult> searchResults;
    private final List<DetailedPlugin> excludedPlugins;

    /**
     * To send the provided information to AutoPlug-Web make sure to call {@link #open()}.
     *
     * @param searchResults   results from checking the included plugins.
     * @param excludedPlugins plugins that were excluded from the check.
     */
    public PluginsUpdateResultConnection(List<SearchResult> searchResults,
                                         List<DetailedPlugin> excludedPlugins) {
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
        DataOutputStream dos = this.getDataOut();
        DataInputStream dis = this.getDataIn();

        long msLeft = dis.readLong(); // 0 if the last plugins check was over 4 hours ago, else it returns the time left, till a new check is allowed
        if (msLeft != 0)
            throw new Exception("Failed to send update check result to web. Web cool-down is still active (" + (msLeft / 60000) + " minutes remaining).");

        dos.writeInt(searchResults.size());
        for (SearchResult result :
                searchResults) {
            dos.writeUTF(result.getPlugin().getName());
            dos.writeUTF(result.getPlugin().getAuthor());
            dos.writeUTF(result.getPlugin().getVersion());
            dos.writeByte(result.getResultCode());

            if (result.getDownloadType() == null)
                dos.writeUTF("null");
            else
                dos.writeUTF(result.getDownloadType());

            if (result.getLatestVersion() == null)
                dos.writeUTF("null");
            else
                dos.writeUTF(result.getLatestVersion());

            if (result.getDownloadUrl() == null)
                dos.writeUTF("null");
            else
                dos.writeUTF(result.getDownloadUrl());

            if (result.getSpigotId() == null)
                dos.writeUTF("0");
            else
                dos.writeUTF(result.getSpigotId());

            if (result.getBukkitId() == null)
                dos.writeUTF("0");
            else
                dos.writeUTF(result.getBukkitId());
        }

        dos.writeInt(excludedPlugins.size());
        for (DetailedPlugin excludedPl :
                excludedPlugins) {
            String plName = excludedPl.getName();
            if (plName == null)
                dos.writeUTF("null");
            else
                dos.writeUTF(plName);

            String plAuthor = excludedPl.getAuthor();
            if (plAuthor == null)
                dos.writeUTF("null");
            else
                dos.writeUTF(plAuthor);

            String plVersion = excludedPl.getVersion();
            if (plAuthor == null)
                dos.writeUTF("null");
            else
                dos.writeUTF(plVersion);
        }

    }
}
