/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.connections.ConPluginsUpdateResult;
import com.osiris.autoplug.client.tasks.updater.plugins.search.SearchMaster;
import com.osiris.autoplug.client.tasks.updater.plugins.search.SearchResult;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.BetterWarning;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TaskPluginsUpdater extends BetterThread {
    //private final PluginsUpdateResultConnection con;
    private final String notifyProfile = "NOTIFY";
    private final String manualProfile = "MANUAL";
    private final String automaticProfile = "AUTOMATIC";
    private final int updatesDownloaded = 0;
    private final List<TaskPluginDownload> downloadTasksList = new ArrayList<>();
    @NotNull
    private final List<DetailedPlugin> includedPlugins = new ArrayList<>();
    @NotNull
    private final List<DetailedPlugin> allPlugins = new ArrayList<>();
    @NotNull
    private final List<DetailedPlugin> excludedPlugins = new ArrayList<>();
    DreamYaml pluginsConfig;
    private UpdaterConfig updaterConfig;
    private String userProfile;
    private String pluginsConfigName;
    private Socket online_socket;
    private DataInputStream online_dis;
    private DataOutputStream online_dos;
    private int updatesAvailable = 0;

    public TaskPluginsUpdater(String name, BetterThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        pluginsConfig = new DreamYaml(System.getProperty("user.dir") + "/autoplug/plugins-config.yml");
        pluginsConfig.load(); // No lock needed, since there are no other threads that access this file
        String name = pluginsConfig.getFileNameWithoutExt();
        pluginsConfig.put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ Plugins-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                        "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################\n" +
                        "This file contains detailed information about your installed plugins. It is fetched from each plugins 'plugin.yml' file (located inside their jars).\n" +
                        "The data gets refreshed before performing an update-check. To exclude a plugin from the check set exclude=true.\n" +
                        "If a name/author/version is missing, the plugin gets excluded automatically.\n" +
                        "If there are plugins that weren't found by the search-algorithm, you can add an id (spigot or bukkit) and a custom link (optional & must be a static link to the latest plugin jar).\n" +
                        "spigot-id: Can be found directly in the url. Example URLs id is 78414. Example URL: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                        "bukkit-id: Is the 'Project-ID' and can be found on the plugins bukkit site inside of the 'About' box at the right.\n" +
                        "custom-check-url (FEATURE NOT WORKING YET): must link to a yaml or json file that contains at least these fields: name, author, version (of the plugin)\n" +
                        "custom-download-url: must be a static url to the plugins latest jar file" +
                        "If a spigot-id is not given, AutoPlug will try and find the matching id by using its unique search-algorithm (if it succeeds the spigot-id gets set, else it stays 0).\n" +
                        "If both (bukkit and spigot) ids are provided, the spigot-id will be used.\n" +
                        "The configuration for uninstalled plugins wont be removed from this file, but they are automatically excluded from future checks (the exclude value is ignored).\n" +
                        "If multiple authors are provided, only the first author will be used by the search-algorithm.\n" +
                        "Note: Remember, that the values for exclude, version and author get overwritten if new data is available.\n" +
                        "Note for plugin devs: You can add your spigot/bukkit-id to your plugin.yml file. For more information visit " + GD.OFFICIAL_WEBSITE + "faq/2\n");

        DYModule keep_removed = pluginsConfig.put(name, "general", "keep-removed").setDefValues("true")
                .setComments("Keep the plugins entry in this file even after its removal/uninstallation?");

        PluginManager man = new PluginManager();
        this.allPlugins.addAll(man.getPlugins());
        if (!allPlugins.isEmpty())
            for (DetailedPlugin pl :
                    allPlugins) {
                try {
                    final String plName = pl.getName();
                    if (pl.getName() == null || pl.getName().isEmpty())
                        throw new Exception("The plugins name couldn't be determined for '" + pl.getInstallationPath() + "'!");

                    DYModule exclude = pluginsConfig.put(name, plName, "exclude").setDefValues("false"); // Check this plugin?
                    DYModule version = pluginsConfig.put(name, plName, "version").setDefValues(pl.getVersion());
                    DYModule latestVersion = pluginsConfig.put(name, plName, "latest-version");
                    DYModule author = pluginsConfig.put(name, plName, "author").setDefValues(pl.getAuthor());
                    DYModule spigotId = pluginsConfig.put(name, plName, "spigot-id").setDefValues("0");
                    //DYModule songodaId = new DYModule(config, getModules(), name, plName,+".songoda-id", 0); // TODO WORK_IN_PROGRESS
                    DYModule bukkitId = pluginsConfig.put(name, plName, "bukkit-id").setDefValues("0");
                    DYModule customCheckURL = pluginsConfig.put(name, plName, "custom-check-url");
                    DYModule customDownloadURL = pluginsConfig.put(name, plName, "custom-download-url");

                    // The plugin devs can add their spigot/bukkit ids to their plugin.yml files
                    if (pl.getSpigotId() != 0 && spigotId.asString() != null && spigotId.asInt() == 0) // Don't update the value, if the user has already set it
                        spigotId.setValues("" + pl.getSpigotId());
                    if (pl.getBukkitId() != 0 && bukkitId.asString() != null && bukkitId.asInt() == 0)
                        bukkitId.setValues("" + pl.getBukkitId());

                    // Update the detailed plugins in-memory values
                    pl.setSpigotId(spigotId.asInt());
                    pl.setBukkitId(bukkitId.asInt());
                    pl.setCustomLink(customDownloadURL.asString());

                    // Check for missing author in plugin.yml
                    if ((pl.getVersion() == null || pl.getVersion().trim().isEmpty())
                            && (spigotId.asString() == null || spigotId.asInt() == 0)
                            && (bukkitId.asString() == null || bukkitId.asInt() == 0)) {
                        exclude.setValues("true");
                        this.addWarning("Plugin " + pl.getName() + " is missing 'version' in its plugin.yml file and was excluded.");
                    }

                    // Check for missing version in plugin.yml
                    if ((pl.getAuthor() == null || pl.getAuthor().trim().isEmpty())
                            && (spigotId.asString() == null || spigotId.asInt() == 0)
                            && (bukkitId.asString() == null || bukkitId.asInt() == 0)) {
                        exclude.setValues("true");
                        this.addWarning("Plugin " + pl.getName() + " is missing 'author' or 'authors' in its plugin.yml file and was excluded.");
                    }

                    if (!exclude.asBoolean())
                        includedPlugins.add(pl);
                    else
                        excludedPlugins.add(pl);
                } catch (DuplicateKeyException e) {
                    addWarning(new BetterWarning(this, e, "Duplicate plugin '" + pl.getName() + "' (or plugin name from its plugin.yml) found in your plugins directory. " +
                            "Its recommended to remove it."));
                } catch (Exception e) {
                    addWarning(new BetterWarning(this, e));
                }
            }

        if (keep_removed.asBoolean())
            pluginsConfig.save();
        else {
            pluginsConfig.save(true); // This overwrites the file and removes everything else that wasn't added via the add method before.
        }
        pluginsConfigName = pluginsConfig.getFileNameWithoutExt();
        updaterConfig = new UpdaterConfig();
        userProfile = updaterConfig.plugin_updater_profile.asString();

        if (!updaterConfig.plugin_updater.asBoolean()) {
            skip();
            return;
        }
        if (Server.isRunning()) throw new Exception("Cannot perform plugins update while server is running!");

        // TODO DO COOL-DOWN CHECK STUFF LOCALLY
        /*
        long msLeft = online_dis.readLong(); // 0 if the last plugins check was over 4 hours ago, else it returns the time left, till a new check is allowed
        if (msLeft != 0) {
            skip("Skipped. Cool-down still active (" + (msLeft / 60000) + " minutes remaining).");
            return;
        }

         */

        // First we get the latest plugin details from the yml config.
        // The minimum required information is:
        // name, version, and author. Otherwise they won't get update-checked by AutoPlug (and are not inside the list below).
        setStatus("Fetching latest plugin data...");
        int size = includedPlugins.size();
        if (size == 0) throw new Exception("Plugins size is 0! Nothing to check...");
        setMax(size);

        // TODO USE THIS FOR RESULT REPORT
        int sizeSpigotPlugins = 0;
        int sizeBukkitPlugins = 0;
        int sizeCustomLinkPlugins = 0;
        int sizeUnknownPlugins = 0;

        ExecutorService executorService;
        if (updaterConfig.plugin_updater_async.asBoolean())
            executorService = Executors.newFixedThreadPool(size);
        else
            executorService = Executors.newSingleThreadExecutor();
        List<Future<SearchResult>> activeFutures = new ArrayList<>();
        for (DetailedPlugin pl :
                includedPlugins) {
            try {
                setStatus("Initialising update check for  " + pl.getName() + "...");
                if (pl.getSpigotId() != 0) {
                    sizeSpigotPlugins++; // SPIGOT PLUGIN
                    activeFutures.add(executorService.submit(() -> new SearchMaster().searchBySpigotId(pl)));
                } else if (pl.getBukkitId() != 0) {
                    sizeBukkitPlugins++; // BUKKIT PLUGIN
                    activeFutures.add(executorService.submit(() -> new SearchMaster().searchByBukkitId(pl)));
                } else if (pl.getCustomLink() != null && !pl.getCustomLink().isEmpty()) {
                    sizeCustomLinkPlugins++; // CUSTOM LINK PLUGIN
                    if (pl.getSpigotId() != 0)
                        activeFutures.add(executorService.submit(() -> new SearchMaster().searchBySpigotId(pl)));
                    else if (pl.getBukkitId() != 0)
                        activeFutures.add(executorService.submit(() -> new SearchMaster().searchByBukkitId(pl)));
                    else
                        activeFutures.add(executorService.submit(() -> new SearchMaster().unknownSearch(pl)));
                } else {
                    sizeUnknownPlugins++; // UNKNOWN PLUGIN
                    activeFutures.add(executorService.submit(() -> new SearchMaster().unknownSearch(pl)));
                }
            } catch (Exception e) {
                this.getWarnings().add(new BetterWarning(this, e, "Critical error while searching for update for '" + pl.getName() + "' plugin!"));
            }
        }


        List<SearchResult> results = new ArrayList<>();
        while (!activeFutures.isEmpty()) {
            Thread.sleep(250);
            Future<SearchResult> finishedFuture = null;
            for (Future<SearchResult> future :
                    activeFutures) {
                if (future.isDone()) {
                    finishedFuture = future;
                    break;
                }
            }

            if (finishedFuture != null) {
                activeFutures.remove(finishedFuture);
                SearchResult result = finishedFuture.get();
                results.add(result);
                DetailedPlugin pl = result.getPlugin();
                byte code = result.getResultCode();
                String type = result.getDownloadType(); // The file type to download (Note: When 'external' is returned nothing will be downloaded. Working on a fix for this!)
                String latest = result.getLatestVersion(); // The latest version as String
                String downloadUrl = result.getDownloadUrl(); // The download url for the latest version
                String resultSpigotId = result.getSpigotId();
                String resultBukkitId = result.getBukkitId();
                this.setStatus("Checked '" + pl.getName() + "' plugin (" + results.size() + "/" + size + ")");
                if (code == 0 || code == 1) {
                    doDownloadLogic(pl, code, type, latest, downloadUrl, resultSpigotId, resultBukkitId);
                } else if (code == 2)
                    if (result.getException() != null)
                        getWarnings().add(new BetterWarning(this, result.getException(), "There was an api-error for " + pl.getName() + "!"));
                    else
                        getWarnings().add(new BetterWarning(this, new Exception("There was an api-error for " + pl.getName() + "!")));
                else if (code == 3)
                    getWarnings().add(new BetterWarning(this, new Exception("Plugin " + pl.getName() + " was not found by the search-algorithm! Specify an id in the plugins config file.")));
                else
                    getWarnings().add(new BetterWarning(this, new Exception("Unknown error occurred! Code: " + code + "."), "Notify the developers. Fastest way is through discord (https://discord.gg/GGNmtCC)."));

                try {
                    DYModule mSpigotId = pluginsConfig.get(pluginsConfigName, pl.getName(), "spigot-id");
                    if (resultSpigotId != null
                            && (mSpigotId.asString() == null || mSpigotId.asInt() == 0)) // Because we can get a "null" string from the server
                        mSpigotId.setValues(resultSpigotId);

                    DYModule mBukkitId = pluginsConfig.get(pluginsConfigName, pl.getName(), "bukkit-id");
                    if (resultBukkitId != null
                            && (mSpigotId.asString() == null || mSpigotId.asInt() == 0)) // Because we can get a "null" string from the server
                        mBukkitId.setValues(resultBukkitId);

                    // The config gets saved at the end of the runAtStart method.
                } catch (Exception e) {
                    getWarnings().add(new BetterWarning(this, e));
                }
            }
        }

        pluginsConfig.save();


        // Wait until all download tasks have finished.
        while (!downloadTasksList.isEmpty()) {
            Thread.sleep(1000);
            TaskPluginDownload finishedDownloadTask = null;
            for (TaskPluginDownload task :
                    downloadTasksList) {
                if (!task.isAlive()) {
                    finishedDownloadTask = task;
                    break;
                }
            }

            if (finishedDownloadTask != null) {
                downloadTasksList.remove(finishedDownloadTask);
                SearchResult matchingResult = null;
                for (SearchResult result :
                        results) {
                    if (result.getPlugin().getName().equals(finishedDownloadTask.getPlName())) {
                        matchingResult = result;
                        break;
                    }
                }
                if (matchingResult == null)
                    throw new Exception("This should not happen! Please report to the devs!");

                if (finishedDownloadTask.isDownloadSuccessful())
                    matchingResult.setResultCode((byte) 5);

                if (finishedDownloadTask.isInstallSuccessful())
                    matchingResult.setResultCode((byte) 6);
            }
        }

        if (new WebConfig().send_plugins_updater_results.asBoolean()) {
            setStatus("Sending update check results to AutoPlug-Web...");
            try {
                new ConPluginsUpdateResult(results, excludedPlugins)
                        .open();
            } catch (Exception e) {
                addWarning(new BetterWarning(this, e));
            }
        }

        finish("Finished checking all plugins (" + results.size() + "/" + size + ")");
    }

    private void doDownloadLogic(@NotNull DetailedPlugin pl, byte code, @NotNull String type, String latest, String url, @NotNull String resultSpigotId, @NotNull String resultBukkitId) {
        if (code == 0) {
            //getSummary().add("Plugin " +pl.getName()+ " is already on the latest version (" + pl.getVersion() + ")"); // Only for testing right now
        } else {
            updatesAvailable++;

            try {
                // Update the in-memory config
                DYModule mLatest = pluginsConfig.get(pluginsConfigName, pl.getName(), "latest-version");
                mLatest.setValues(latest);
            } catch (Exception e) {
                getWarnings().add(new BetterWarning(this, e));
            }

            if (userProfile.equals(notifyProfile)) {
                addInfo("NOTIFY: Plugin '" + pl.getName() + "' has an update available (" + pl.getVersion() + " -> " + latest + ")");
            } else {
                if (type.equals(".jar") || type.equals("external")) { // Note that "external" support is kind off random and strongly dependent on what spigot devs are doing

                    if (pl.isPremium()) {
                        getWarnings().add(new BetterWarning(this,
                                pl.getName() + " (" + latest + ") is a premium plugin and currently not supported."));
                        /* // TODO
                        try{
                            AL.info("Logging in with provided credentials for spigotmc.org...");
                            // You can optionally pass a Settings object here,
                            // constructed using Settings.Builder
                            JBrowserDriver driver = new JBrowserDriver(Settings.builder().
                                    timezone(Timezone.AMERICA_NEWYORK).build());
                            driver.manage().addCookie(new Cookie("xf_user", GD.SPIGOT_XF_USER));
                            driver.manage().addCookie(new Cookie("xf_session", GD.SPIGOT_XF_SESSION));

                            // This will block for the page load and any
                            // associated AJAX requests
                            driver.get("https://www.spigotmc.org/resources/"+resultSpigotId);
                            Thread.sleep(7000); // Cloudflare is only 5 seconds, just to be sure we do 7 though...
                            driver.get("https://www.spigotmc.org/resources/"+resultSpigotId);

                            String actualDownloadUrl = "https://www.spigotmc.org/"+ driver.findElementByClassName("inner OverlayTrigger")
                                    .getAttribute("href"); // The download or purchase buttons <a> tag with the download link

                            if (actualDownloadUrl.contains("/purchase"))
                                throw new Exception("Resource update failed, because you do not own this premium resource.");

                            if (driver.getStatusCode() != 200)
                                throw new Exception("Status code should be 200, but is '"+driver.getStatusCode()+"'. Html ("+driver.getCurrentUrl()+"): "+driver.getPageSource());

                            // Close the browser. Allows its thread to terminate.
                            driver.quit();

                            if (userProfile.equals(manualProfile)) {
                                File cache_dest = new File(GD.WORKING_DIR + "/autoplug/downloads/" + pl.getName() + "[" + latest + "].jar");
                                TaskPluginDownload task = new TaskPluginDownload(
                                        "PluginDownloader", getManager(), pl.getName(), latest, actualDownloadUrl, userProfile, cache_dest, null, true);
                                downloadTasksList.add(task);
                                task.start();
                            } else {
                                File oldPl = new File(pl.getInstallationPath());
                                File dest = new File(GD.WORKING_DIR + "/plugins/" + pl.getName() + "-LATEST-" + "[" + latest + "]" + ".jar");
                                TaskPluginDownload task = new TaskPluginDownload(
                                        "PluginDownloader", getManager(), pl.getName(), latest, actualDownloadUrl, userProfile, dest, oldPl, true);
                                downloadTasksList.add(task);
                                task.start();
                            }
                        } catch (Exception e) {
                            getWarnings().add(new BetterWarning(this, e,
                                    "Failed to download premium plugin update(" + latest + ") for " + pl.getName() + "."));
                        }

                         */
                    } else { // Not a premium plugin
                        if (userProfile.equals(manualProfile)) {
                            File cache_dest = new File(GD.WORKING_DIR + "/autoplug/downloads/" + pl.getName() + "[" + latest + "].jar");
                            TaskPluginDownload task = new TaskPluginDownload("PluginDownloader", getManager(), pl.getName(), latest, url, userProfile, cache_dest);
                            downloadTasksList.add(task);
                            task.start();
                        } else {
                            File oldPl = new File(pl.getInstallationPath());
                            File dest = new File(GD.WORKING_DIR + "/plugins/" + pl.getName() + "-LATEST-" + "[" + latest + "]" + ".jar");
                            TaskPluginDownload task = new TaskPluginDownload("PluginDownloader", getManager(), pl.getName(), latest, url, userProfile, dest, oldPl);
                            downloadTasksList.add(task);
                            task.start();
                        }
                    }
                } else
                    getWarnings().add(new BetterWarning(this, new Exception("Failed to download plugin update(" + latest + ") for " + pl.getName() + " because of unsupported type: " + type)));
            }
        }

    }

    /**
     * Returns a list containing only plugins, that contain all the information needed to perform a search. <br>
     * That means, that a plugin must have its name, its authors name and its version in its plugin.yml file.
     */
    @NotNull
    public List<DetailedPlugin> getIncludedPlugins() {
        return includedPlugins;
    }

    @NotNull
    public List<DetailedPlugin> getExcludedPlugins() {
        return excludedPlugins;
    }

    /**
     * Returns a list containing all plugins found in the /plugins directory. <br>
     */
    @NotNull
    public List<DetailedPlugin> getAllPlugins() {
        return allPlugins;
    }

}
