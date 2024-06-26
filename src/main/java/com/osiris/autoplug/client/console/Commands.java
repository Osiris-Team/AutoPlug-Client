/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.console;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;

import org.jetbrains.annotations.NotNull;

import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.SSHConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.network.online.connections.ConSendPrivateDetails;
import com.osiris.autoplug.client.network.online.connections.ConSendPublicDetails;
import com.osiris.autoplug.client.tasks.BeforeServerStartupTasks;
import com.osiris.autoplug.client.tasks.SSHManager;
import com.osiris.autoplug.client.tasks.backup.TaskBackup;
import com.osiris.autoplug.client.tasks.updater.java.TaskJavaUpdater;
import com.osiris.autoplug.client.tasks.updater.mods.InstalledModLoader;
import com.osiris.autoplug.client.tasks.updater.mods.MinecraftMod;
import com.osiris.autoplug.client.tasks.updater.mods.TaskModDownload;
import com.osiris.autoplug.client.tasks.updater.mods.TaskModsUpdater;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.plugins.ResourceFinder;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginDownload;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginsUpdater;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.tasks.updater.self.TaskSelfUpdater;
import com.osiris.autoplug.client.tasks.updater.server.TaskServerUpdater;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsFile;
import com.osiris.autoplug.client.utils.UtilsMinecraft;
import com.osiris.autoplug.client.utils.tasks.MyBThreadManager;
import com.osiris.autoplug.client.utils.tasks.UtilsTasks;
import com.osiris.jlib.logger.AL;

/**
 * Listens for input started with .
 * List the server with .help
 */
public final class Commands {
    
    
    public static SSHManager sshManager;


    /**
     * Returns true if the provided String is a AutoPlug command.
     *
     * @param command An AutoPlug command like .help for example.
     */
    public static boolean execute(@NotNull String command) {



        String first = "";
        try {
            Objects.requireNonNull(command);
            command = command.trim();
            first = Character.toString(command.charAt(0));
        } catch (StringIndexOutOfBoundsException e) {
            AL.info("Enter .help for all available commands!");
            return false;
        } catch (Exception e) {
            AL.warn("Failed to read command '" + command + "'! Enter .help for all available commands!", e);
            return false;
        }

        if (first.equals(".")) {
            try {
                SSHConfig sshConfig = new SSHConfig();
                if (command.equals(".help") || command.equals(".h")) {
                    AL.info("");
                    AL.info(".help | Prints out this (Shortcut: .h)");
                    AL.info(".run tasks | Runs the 'before server startup tasks' without starting the server (.rt)");
                    AL.info(".con info | Shows details about AutoPlugs network connections (.ci)");
                    AL.info(".con reload | Closes and reconnects all connections (.cr)");
                    AL.info(".backup | Ignores cool-down and does an backup (.b)");
                    AL.info(".env info | Shows environment details (.ei)");
                    AL.info(".find java | Finds all Java installations and lists current Javas binaries (.fj)");
                    AL.info("");
                    AL.info("Server related commands:");
                    AL.info(".start | Starts the server (.s)");
                    AL.info(".restart | Restarts the server (.r)");
                    AL.info(".stop | Stops and saves the server (.st)");
                    AL.info(".stop both | Stops, saves your server and closes AutoPlug safely (.stb)");
                    AL.info(".kill | Kills the server without saving (.k)");
                    AL.info(".kill both | Kills the server without saving and closes AutoPlug (.kb)");
                    AL.info(".server info | Shows details about this server (.si)");
                    AL.info("");
                    AL.info("Direct install commands:");
                    AL.info(".install plugin <name> | Installs a new plugin by its name over spigot (.ip)");
                    AL.info(".install plugin spigot|bukkit|modrinth <id> | Installs a new plugin via id (.ip)");
                    AL.info(".install plugin github <author>/<name> (optional <asset-name>) | Installs a new plugin via github (.ip)");
                    AL.info(".install mod <name> | Installs a new mod by its name (.im)");
                    AL.info(".install mod modrinth|curseforge <id> | Installs a new mod via id (.im)");
                    AL.info(".install mod github <author>/<name> (optional | Installs a new mod via github (.im)");
                    AL.info("");
                    AL.info("Update checking commands: (note that all the checks below");
                    AL.info("ignore the cool-down and behave according to the selected profile)");
                    AL.info(".check | Checks for AutoPlug updates (.c)");
                    AL.info(".check java | Checks for Java updates (.cj)");
                    AL.info(".check server | Checks for server updates (.cs)");
                    AL.info(".check plugins | Checks for plugins updates (.cp)");
                    AL.info(".check mods | Checks for mods updates (.cm)");
                    AL.info("");
                    AL.info(".ssh stop | Stops the SSH-Server");
                    AL.info(".ssh start | Starts the SSH-Server");
                    AL.info(".ssh restart | Restarts the SSH-Server");

                    AL.info("");
                    return true;
                } else if (command.equals(".start") || command.equals(".s")) {
                    Server.start();
                    return true;
                } else if (command.equals(".restart") || command.equals(".r")) {
                    Server.restart();
                    return true;
                } else if (command.equals(".stop") || command.equals(".st")) {
                    Server.stop();
                    return true;
                } else if (command.equals(".stop both") || command.equals(".stb")) {
                    // All the stuff that needs to be done before shutdown is done by the ShutdownHook.
                    // See SystemChecker.addShutdownHook() for details.
                    System.exit(0);
                    return true;
                } else if (command.equals(".kill") || command.equals(".k")) {
                    Server.kill();
                    return true;
                } else if (command.equals(".kill both") || command.equals(".kb")) {
                    Server.kill();
                    AL.info("Killing AutoPlug-Client and Server! Ahhhh!");
                    AL.info("Achievement unlocked: Double kill!");
                    Thread.sleep(3000);
                    System.exit(0);
                    return true;
                } else if (command.equals(".run tasks") || command.equals(".rt")) {
                    new BeforeServerStartupTasks();
                    return true;
                } else if (command.equals(".con info") || command.equals(".ci")) {
                    AL.info("Main connection: connected=" + Main.CON.isAlive() + " interrupted=" + Main.CON.isInterrupted() + " user/staff active=" + Main.CON.isUserActive.get());
                    AL.info(Main.CON.CON_PUBLIC_DETAILS.toString());
                    AL.info(Main.CON.CON_PRIVATE_DETAILS.toString());
                    AL.info(Main.CON.CON_CONSOLE_SEND.toString());
                    AL.info(Main.CON.CON_CONSOLE_RECEIVE.toString());
                    AL.info(Main.CON.CON_FILE_MANAGER.toString());
                    return true;
                } else if (command.equals(".con reload") || command.equals(".cr")) {
                    Main.CON.close();
                    AL.info("Closed connections, reconnecting in 10 seconds...");
                    Thread.sleep(10000);
                    Main.CON.open();
                    return true;
                } else if (command.equals(".server info") || command.equals(".si")) {
                    AL.info("AutoPlug-Version: " + GD.VERSION);
                    ConSendPublicDetails conPublic = Main.CON.CON_PUBLIC_DETAILS;
                    ConSendPrivateDetails conPrivate = Main.CON.CON_PRIVATE_DETAILS;
                    AL.info("Running: " + Server.isRunning());
                    String ip;
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL("http://checkip.amazonaws.com").openStream()))) {
                        ip = in.readLine();
                    } catch (Exception e) {
                        ip = "- (failed to reach http://checkip.amazonaws.com)";
                    }
                    AL.info("Public-IP: " + ip);
                    AL.info("Device-/Local-IP: " + InetAddress.getLocalHost().getHostAddress());
                    if (!conPublic.isAlive()) {
                        AL.info(conPublic.getClass().getSimpleName() + " is not active, thus more information cannot be retrieved!");
                    } else {
                        AL.info("Details from " + conPublic.getClass().getSimpleName() + ":");
                        AL.info("Host: " + conPublic.host + ":" + conPublic.port);
                        AL.info("Running: " + conPublic.isRunning);
                        AL.info("Version: " + conPublic.version);
                        AL.info("Players: " + conPublic.currentPlayers);
                        if (conPublic.mineStat != null) {
                            AL.info("Ping result: " + conPublic.mineStat.pingResult.name());
                        } else
                            AL.info("Ping result: -");
                        AL.info("Details from " + conPrivate.getClass().getSimpleName() + ":");
                        AL.info("CPU usage: " + conPrivate.cpuUsage + "%");
                        AL.info("CPU current: " + conPrivate.cpuSpeed + " GHz");
                        AL.info("CPU max: " + conPrivate.cpuMaxSpeed + " GHz");
                        AL.info("MEM free: " + conPrivate.memAvailable + " Gb");
                        AL.info("MEM used: " + conPrivate.memUsed + " Gb");
                        AL.info("MEM total: " + conPrivate.memTotal + " Gb");
                    }
                    return true;
                } else if (command.equals(".env info") || command.equals(".ei")) {

                    AL.info("###################################################");
                    AL.info("ALL PROPERTIES:");
                    AL.info("###################################################");
                    Properties props = System.getProperties();
                    props.forEach((key, val) -> {
                        AL.info(key + ": " + val);
                    });

                    AL.info("###################################################");
                    AL.info("ALL ENVIRONMENT VARIABLES:");
                    AL.info("###################################################");
                    // Get all environment variables
                    Map<String, String> env = System.getenv();
                    // Loop through and print each environment variable
                    for (Map.Entry<String, String> entry : env.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        AL.info(key + ": " + value);
                    }

                    return true;
                } else if (command.equals(".find java") || command.equals(".fj")) {
                    Path directoryPath = new File(System.getProperty("java.home")).toPath();

                    AL.info("###################################################");
                    AL.info("ALL BINARY FILES IN " + directoryPath + ": ");
                    AL.info("###################################################");
                    try {
                        Files.walkFileTree(directoryPath, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                // Check if the file name contains "java"
                                if (file.toFile().isFile() && (!file.getFileName().toString().contains(".") || file.getFileName().toString().contains(".exe"))) {
                                    AL.info(file.toString());
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } catch (IOException e) {
                        AL.warn(e);
                    }

                    AL.info("###################################################");
                    AL.info("JAVA INSTALLATIONS: ");
                    AL.info("###################################################");
                    List<String> javaInstallations = findJavaInstallations();

                    if (javaInstallations.isEmpty()) {
                        AL.info("No Java installations found.");
                    } else {
                        AL.info("Possible Java installations found:");
                        for (String installation : javaInstallations) {
                            AL.info(installation);
                        }
                    }
                    return true;
                } else if (command.equals(".check") || command.equals(".c")) {
                    MyBThreadManager myManager = new UtilsTasks().createManagerAndPrinter();
                    new TaskSelfUpdater("SelfUpdater", myManager.manager).start();
                    new UtilsTasks().printResultsWhenDone(myManager.manager);
                    return true;
                } else if (command.equals(".check java") || command.equals(".cj")) {
                    MyBThreadManager myManager = new UtilsTasks().createManagerAndPrinter();
                    new TaskJavaUpdater("JavaUpdater", myManager.manager).start();
                    new UtilsTasks().printResultsWhenDone(myManager.manager);
                    return true;
                } else if (command.equals(".check server") || command.equals(".cs")) {
                    MyBThreadManager myManager = new UtilsTasks().createManagerAndPrinter();
                    new TaskServerUpdater("ServerUpdater", myManager.manager).start();
                    new UtilsTasks().printResultsWhenDone(myManager.manager);
                    return true;
                } else if (command.equals(".check plugins") || command.equals(".cp")) {
                    MyBThreadManager myManager = new UtilsTasks().createManagerAndPrinter();
                    new TaskPluginsUpdater("PluginsUpdater", myManager.manager).start();
                    new UtilsTasks().printResultsWhenDone(myManager.manager);
                    return true;
                } else if (command.equals(".check mods") || command.equals(".cm")) {
                    MyBThreadManager myManager = new UtilsTasks().createManagerAndPrinter();
                    new TaskModsUpdater("ModsUpdater", myManager.manager).start();
                    new UtilsTasks().printResultsWhenDone(myManager.manager);
                    return true;
                } else if (command.startsWith(".install plugin") || command.startsWith(".ip")) {
                    installPlugin(command);
                    return true;
                } else if (command.startsWith(".install mod") || command.startsWith(".im")) {
                    installMod(command);
                    return true;
                } else if (command.equals(".backup") || command.equals(".b")) {
                    MyBThreadManager myManager = new UtilsTasks().createManagerAndPrinter();
                    TaskBackup backupTask = new TaskBackup("BackupTask", myManager.manager);
                    backupTask.ignoreCooldown = true;
                    backupTask.start();
                    new UtilsTasks().printResultsWhenDone(myManager.manager);
                    return true;
                } else if (command.equals(".ssh stop")) {
                    SSHManager.stop(false);
                    return true;
                } else if (command.equals(".ssh start")) {
                    SSHManager.start();
                    return true;
                } else if (command.equals(".ssh restart")) {
                    SSHManager.stop(false);
                    SSHManager.start();
                    return true;
                } else if (command.equals(".testssh")) { // This may look like it does nothing, but it's actually so the SSHServerTest can send something and have an expected output.
                    AL.info("OK");
                    return true;
                } else {
                    AL.info("Command '" + command + "' not found! Enter .help or .h for all available commands!");
                    return true;
                }
            } catch (Exception e) {
                AL.warn("Error at execution of '" + command + "' command!", e);
                return true;
            }
        } else { // Is not an AP command - should be sent to the server
            return false;
        }
    }

    public static List<String> findJavaInstallations() {
        List<String> installations = new ArrayList<>();

        // Get all drives on the system
        File[] drives;
        try {
            drives = File.listRoots();
        } catch (Exception e) {
            AL.warn("Failed to fetch drives, using fallback hardcoded drives.", e);
            drives = new File[]{Paths.get("/").toFile(), Paths.get("C:\\").toFile(), Paths.get("D:\\").toFile()};
        }

        for (File drive : drives) {
            try {
                findJavaInstallationsInDrive(drive, installations);
            } catch (Exception e) {
                AL.warn("Failed for drive: " + drive, e);
            }
        }

        return installations;
    }

    private static void findJavaInstallationsInDrive(File directory, List<String> installations) {
        // Check if the directory is named "bin"
        File binDirectory = new File(directory, "bin");

        if (binDirectory.exists() && binDirectory.isDirectory()) {
            // Check if the "java" file exists in the "bin" directory
            for (File f : binDirectory.listFiles()) {
                if (f.getName().startsWith("java")) {
                    installations.add(binDirectory.getAbsolutePath());
                    break;
                }
            }
        }

        // Recursively search subdirectories
        File[] subDirectories = directory.listFiles(File::isDirectory);
        if (subDirectories != null) {
            for (File subDirectory : subDirectories) {
                findJavaInstallationsInDrive(subDirectory, installations);
            }
        }
    }

    public static boolean installPlugin(String command) throws Exception {
        String input = command.replaceFirst("\\.install plugin", "").replaceFirst("\\.ip", "").trim();
        SearchResult result = null;
        String tempName = "NEW_PLUGIN";
        UpdaterConfig updaterConfig = new UpdaterConfig();
        File pluginsDir = FileManager.convertRelativeToAbsolutePath(updaterConfig.plugins_updater_path.asString());

        MinecraftPlugin plugin = new MinecraftPlugin(new File(pluginsDir + "/" + tempName).getAbsolutePath(),
                tempName, "0", "", 0, 0, "");

        String mcVersion = updaterConfig.plugins_updater_version.asString();
        if (mcVersion == null) mcVersion = Server.getMCVersion();

        String repo = "spigot";
        if(input.startsWith(repo)){
            int spigotId = Integer.parseInt(input.replace(repo, "").trim());
            result = new ResourceFinder().findPluginBySpigotId(new MinecraftPlugin(new File(pluginsDir + "/" + tempName).getAbsolutePath(), tempName, "0", "", spigotId, 0, ""));
        }
        repo = "bukkit";
        if(input.startsWith(repo)){
            int bukkitId = Integer.parseInt(input.replace(repo, "").trim());
            result = new ResourceFinder().findPluginByBukkitId(new MinecraftPlugin(new File(pluginsDir + "/" + tempName).getAbsolutePath(),
                    tempName, "0", "", 0, bukkitId, ""));
        }
        repo = "github";
        if(input.startsWith(repo)){
            String[] split = input.replace(repo, "").trim().split(" ");
            if (!input.contains("/")) {
                AL.warn("The github format must be <author>/<name> (and optional <asset-name>).");
                return false;
            }
            plugin.setGithubRepoName(split[0]);
            if(split.length >= 2) plugin.setGithubAssetName(split[1]);
            else plugin.setGithubAssetName(split[0].split("/")[1]);
            result = new ResourceFinder().findByGithubUrl(plugin);
        }
        repo = "modrinth";
        if(input.startsWith(repo)){
            MinecraftPlugin plugin2 = new MinecraftPlugin(new File(pluginsDir + "/" + tempName).getAbsolutePath(),
                    tempName, "0", "", 0, 0, "");
            plugin2.setModrinthId(input.replace(repo, "").trim());
            result = new ResourceFinder().findPluginByModrinthId(plugin2, mcVersion);
        }
        /*
        Nothing of the above worked thus do search by name
         */
        if(!SearchResult.isMatchFound(result)){
            plugin.setName(input.trim());

            try { // SPIGOT
                result = new ResourceFinder().findUnknownSpigotPlugin(plugin);
                if (!result.similarPlugins.isEmpty()) {
                    List<MinecraftPlugin> similarPlugins = result.similarPlugins;
                    AL.info("Multiple results (" + similarPlugins.size() + "):");
                    AL.info("Sorted by most downloads first.");
                    for (int i = 0; i < similarPlugins.size(); i++) {
                        MinecraftPlugin similarPl = similarPlugins.get(i);
                        AL.info(i+". "+similarPl.getName()+" by "+similarPl.getAuthor());
                    }
                    AL.info("Enter the number to install it or press enter to abort:");
                    String input2 = "";
                    Scanner scanner = new Scanner(System.in);
                    while(true){
                        input2 = scanner.nextLine();
                        if(input2.trim().isEmpty()) {
                            AL.info("Aborted.");
                            break;
                        }
                        try{
                            int i = Integer.parseInt(input2.trim());
                            result.plugin = similarPlugins.get(i);
                            result.type = SearchResult.Type.UPDATE_AVAILABLE;
                            break;
                        } catch (Exception e) {
                            AL.warn(e);
                            AL.info("Not a valid option. Try again.");
                        }
                    }
                }
            } catch (Exception e) {
                AL.warn("Failed to find plugin named '"+plugin.getName()+"' at spigotmc.org.", e);
            }
            // TODO also search other repos
        }

        if (!SearchResult.isMatchFound(result)) {
            AL.warn("Failed to find plugin, check the provided name for typos.");
            return false;
        }
        MyBThreadManager myManager = new UtilsTasks().createManagerAndPrinter();
        File finalDest = new File(pluginsDir + "/" + result.plugin.getName() + "-LATEST-[" + result.latestVersion + "].jar");
        TaskPluginDownload task = new TaskPluginDownload("PluginDownloader", myManager.manager, tempName, result.latestVersion,
                result.downloadUrl, result.plugin.getIgnoreContentType(), "AUTOMATIC", finalDest);
        task.start();
        new UtilsTasks().printResultsWhenDone(myManager.manager);
        List<MinecraftPlugin> plugins = new UtilsMinecraft().getPlugins(pluginsDir);
        for (MinecraftPlugin pl : plugins) {
            if (pl.getInstallationPath().equals(finalDest.getAbsolutePath())) {
                // Replace tempName with actual plugin name
                finalDest = new UtilsFile().renameFile(task.getFinalDest(),
                        new File(pl.getInstallationPath()).getName().replace(tempName, pl.getName()));
            }
        }
        AL.info("Installed to: " + finalDest);
        return true;
    }

    public static boolean installMod(String command) throws Exception {
        String input = command.replaceFirst("\\.install mod", "").replaceFirst("\\.im", "").trim();

        SearchResult result = null;
        String tempName = "NEW_MOD";
        UpdaterConfig updaterConfig = new UpdaterConfig();
        File modsDir = FileManager.convertRelativeToAbsolutePath(updaterConfig.mods_updater_path.asString());

        String mcVersion = updaterConfig.mods_updater_version.asString();
        if (mcVersion == null) mcVersion = Server.getMCVersion();

        MinecraftMod mod = new MinecraftMod(new File(modsDir + "/" + tempName).getAbsolutePath(), tempName, "0",
                "", "0", "0", "");

        String repo = "modrinth";
        if(input.startsWith(repo)) {
            mod.modrinthId = input.replace(repo, "").trim();
            result = new ResourceFinder().findModByModrinthId(new InstalledModLoader(),
                    mod, mcVersion);
        }
        repo = "curseforge";
        if(input.startsWith(repo)) {
            mod.curseforgeId = input.replace(repo, "").trim();
            result = new ResourceFinder().findModByCurseforgeId(new InstalledModLoader(), mod,
                    mcVersion, updaterConfig.mods_update_check_name_for_mod_loader.asBoolean());
        }
        repo = "github";
        if(input.startsWith(repo)) {
            String[] split = input.replace(repo, "").trim().split(" ");
            if (!input.contains("/")) {
                AL.warn("The github format must be <author>/<name> (and optional <asset-name>).");
                return false;
            }
            mod.githubRepoName = (split[0]);
            if(split.length >= 2) mod.githubAssetName = (split[1]);
            else mod.githubAssetName = (split[0].split("/")[1]);
            result = new ResourceFinder().findByGithubUrl(mod);
        }
        /*
        Nothing of the above worked thus do search by name
         */
        if(!SearchResult.isMatchFound(result)){
            mod.setName(input.trim());

            try { // MODRINTH
                // https://docs.modrinth.com/#tag/projects/operation/searchProjects
                AL.warn("Modrinth search is not implemented yet, this is in todo. As alternative use .im modrinth <id> for example.");
                // TODO something similar for mods: result = new ResourceFinder().findUnknownSpigotPlugin(plugin);
            } catch (Exception e) {
                //AL.warn("Failed to find plugin named '"+plugin.getName()+"' at spigotmc.org.", e);
            }
            // TODO also search other repos
        }

        if (!SearchResult.isMatchFound(result)) {
            AL.warn("Failed to find mod, check the provided name for typos.");
            return false;
        }
        MyBThreadManager myManager = new UtilsTasks().createManagerAndPrinter();
        File finalDest = new File(modsDir + "/" + result.mod.getName() + "-LATEST-[" + result.latestVersion + "].jar");
        TaskModDownload task = new TaskModDownload("ModDownloader", myManager.manager, tempName, result.latestVersion,
                result.downloadUrl, result.mod.ignoreContentType, "AUTOMATIC", finalDest);
        task.start();
        new UtilsTasks().printResultsWhenDone(myManager.manager);
        List<MinecraftMod> plugins = new UtilsMinecraft().getMods(modsDir);
        for (MinecraftMod mod2 : plugins) {
            if (mod2.installationPath.equals(finalDest.getAbsolutePath())) {
                // Replace tempName with actual plugin name
                finalDest = new UtilsFile().renameFile(task.getFinalDest(),
                        new File(mod2.installationPath).getName().replace(tempName, mod2.getName()));
            }
        }
        AL.info("Installed to: " + finalDest);
        return true;
    }


}
