/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins.search;


import com.osiris.autoplug.client.tasks.updater.plugins.DetailedPlugin;
import com.osiris.autoplug.client.tasks.updater.plugins.search.bukkit.BukkitSearchById;
import com.osiris.autoplug.client.tasks.updater.plugins.search.spigot.SpigotSearchByAuthor;
import com.osiris.autoplug.client.tasks.updater.plugins.search.spigot.SpigotSearchById;
import com.osiris.autoplug.client.tasks.updater.plugins.search.spigot.SpigotSearchByName;

import java.util.HashMap;
import java.util.Map;

public class SearchMaster {

    private final Map<Thread, SearchResult> threadsAndResults = new HashMap<>();

    public SearchResult getSearchResultForThread(Thread thread) {
        return threadsAndResults.get(thread);
    }

    /**
     * Searches for unknown plugins asynchronously. <br>
     * Get the result via {@link #getSearchResultForThread(Thread)}.
     *
     * @return the new Thread that was started to execute this operation.
     */
    public Thread unknownSearch(DetailedPlugin plugin) {
        Thread thread = new Thread(() -> threadsAndResults.put(Thread.currentThread(), unknownSearchSync(plugin)));
        thread.start();
        return thread;
    }

    /**
     * Searches for plugin with provided spigot-id asynchronously. <br>
     * Get the result via {@link #getSearchResultForThread(Thread)}.
     *
     * @return the new Thread that was started to execute this operation.
     */
    public Thread searchBySpigotId(DetailedPlugin plugin) {
        Thread thread = new Thread(() -> threadsAndResults.put(Thread.currentThread(), searchBySpigotIdSync(plugin)));
        thread.start();
        return thread;
    }

    /**
     * Searches for plugin with provided bukkit-id asynchronously. <br>
     * Get the result via {@link #getSearchResultForThread(Thread)}.
     *
     * @return the new Thread that was started to execute this operation.
     */
    public Thread searchByBukkitId(DetailedPlugin plugin) {
        Thread thread = new Thread(() -> threadsAndResults.put(Thread.currentThread(), searchByBukkitIdSync(plugin)));
        thread.start();
        return thread;
    }

    /**
     * If the spigot/bukkit id is not given this type of search
     * based on the plugins name and author will be executed.
     */
    public SearchResult unknownSearchSync(DetailedPlugin plugin) {

        // Before passing over remove everything except numbers and dots
        plugin.setVersion(plugin.getVersion().replaceAll("[^0-9.]", ""));

        // Before passing over remove everything except words and numbers
        plugin.setAuthor(plugin.getAuthor().replaceAll("[^\\w]", ""));

        // Do spigot search by name
        SearchResult result_spigot = new SpigotSearchByName().search(plugin);

        if (result_spigot == null || result_spigot.getResultCode() == 2 || result_spigot.getResultCode() == 3) {
            //Couldn't find author or resource via first search
            //Do alternative search:
            return new SpigotSearchByAuthor().search(plugin);
        }

        return result_spigot;
    }

    public SearchResult searchBySpigotIdSync(DetailedPlugin plugin) {
        return new SpigotSearchById().search(plugin);
    }

    public SearchResult searchByBukkitIdSync(DetailedPlugin plugin) {
        return new BukkitSearchById().search(plugin);
    }

}
