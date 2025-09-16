/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.ui.MainWindow;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsJar;
import com.osiris.autoplug.client.utils.UtilsNative;
import com.osiris.dyml.SmartString;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.*;
import com.osiris.jlib.logger.AL;

import java.io.IOException;

public class GeneralConfig extends MyYaml {
    public YamlSection autoplug_auto_stop;
    public YamlSection autoplug_target_software;
    public YamlSection autoplug_start_on_boot;
    public YamlSection autoplug_system_tray;
    public YamlSection autoplug_system_tray_theme;

    public YamlSection server_key;
    public YamlSection server_version;
    public YamlSection server_auto_start;
    public YamlSection server_auto_eula;
    public YamlSection server_start_command;
    public YamlSection server_stop_command;
    public YamlSection server_restart_on_crash;

    public YamlSection directory_cleaner;
    public YamlSection directory_cleaner_max_days;
    public YamlSection directory_cleaner_files;

    public GeneralConfig() throws NotLoadedException, YamlReaderException, YamlWriterException, IOException, IllegalKeyException, DuplicateKeyException, IllegalListException {
        this(true);
    }

    public GeneralConfig(boolean save) throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, YamlWriterException, NotLoadedException, IllegalKeyException {
        super(System.getProperty("user.dir") + "/autoplug/general.yml");

        addSingletonConfigFileEventListener(e -> {

            try {
                // Saving this file in this event causes an infinite loop, thus don't
                //GD.determineTarget(this);
            } catch (Exception ex) {
                AL.warn(ex);
            }

            try {
                if (this.autoplug_start_on_boot.asBoolean())
                    new UtilsNative().enableStartOnBootIfNeeded(new UtilsJar().getThisJar());
                else new UtilsNative().disableStartOnBootIfNeeded();
            } catch (Exception ex) {
                AL.warn(ex);
            }

            try {
                if (this.autoplug_system_tray.asBoolean()) {
                    boolean firstStart = MainWindow.GET == null;
                    new MainWindow(this);
                    if (firstStart) AL.info("Started system-tray GUI.");
                } else {
                    boolean isRunning = MainWindow.GET != null;
                    if (isRunning) {
                        MainWindow.GET.close();
                        AL.info("Stopped system-tray GUI.");
                    }
                }
            } catch (Exception ex) {
                AL.warn(ex);
            }

            if (MainWindow.GET != null) MainWindow.GET.initTheme(this);

        });

        lockFile();
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ General-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions here: https://autoplug.one/installer\n" +
                        "If there are any questions or you just want chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################");

        put(name, "autoplug").setCountTopLineBreaks(1);
        autoplug_auto_stop = put(name, "autoplug", "auto-stop").setDefValues("false").setComments(
                "Stops AutoPlug when your server stops. Enabling this feature is not recommended.");
        autoplug_start_on_boot = put(name, "autoplug", "start-on-boot").setDefValues("false").setComments(
                "Starts AutoPlug automatically right after the system booted up.",
                "The current user is used to register the script that is started on system boot.",
                "The script is located at /autoplug/system and can be modified, but should not be renamed or have its path changed.",
                "By default the script launches AutoPlug in the background without a terminal, thus its recommended to also enable the system-tray GUI to have access to the output.",
                "If you have no GUI its recommended to install software like \"screen\" for virtual terminals and edit the script accordingly.");
        autoplug_target_software = put(name, "autoplug", "target-software").setComments(
                "Select the target software AutoPlug was installed on.",
                "Available options: MINECRAFT_CLIENT, MINECRAFT_SERVER, MINDUSTRY_SERVER, OTHER.",
                "If value changed -> restart AutoPlug.");
        autoplug_system_tray = put(name, "autoplug", "system-tray", "enable").setDefValues("false");
        autoplug_system_tray_theme = put(name, "autoplug", "system-tray", "theme").setDefValues("light")
                .setComments("Select between: light, dark and darcula.");

        put(name, "server").setCountTopLineBreaks(1);
        server_key = put(name, "server", "key").setDefValues("INSERT_KEY_HERE").setComments(
                "Enter your Server-Key here. You get it by registering yourself and your server on https://autoplug.one.\n" +
                        "The Server-Key enables remote access from your account.\n" +
                        "No matter what, keep this key private to ensure your servers security!");

        server_version = put(name, "server", "version").setComments(
                "Leave empty to determine it automatically via the server jar.",
                "Set this if the above fails, or you are running as CLI, or you just want to force a specific version.");
        server_auto_start = put(name, "server", "auto-start").setDefValues("true").setComments(
                "Starts your server with the start of AutoPlug.");
        server_auto_eula = put(name, "server", "auto-eula").setDefValues("true").setComments(
                "Creates an eula.txt file if not existing and accepts it.");

        /**
         * Must be given in {@link com.osiris.autoplug.client.Main} at first start.
         */
        server_start_command = put(name, "server", "start-command").setComments("The full command used to start your server.",
                "Like 'java -jar server.jar' for example, or './server.exe'.",
                "./ represents the working directory (where the AutoPlug was started from).");
        server_stop_command = put(name, "server", "stop-command").setDefValues("stop", "shutdown", "end", "close", "finish", "terminate", "abort").setComments(
                "AutoPlug uses this command to stop your server.",
                "By default there are multiple provided to ensure the right one is also there for your server type.");

        // Convert old config stuff to new one:
        YamlSection oldJavaPath = get(name, "server", "java-path");
        if (oldJavaPath != null) {
            oldJavaPath.setComments("DEPRECATED in favor of 'start-command'.");
            String startCommand = "";
            if (oldJavaPath.asString() != null) startCommand += oldJavaPath.asString();
            else startCommand += "java";
            startCommand += " ";

            YamlSection oldServerFlagsEnabled = get(name, "server", "flags", "enable");
            oldServerFlagsEnabled.setComments("DEPRECATED in favor of 'start-command'.");
            YamlSection oldServerFlags = get(name, "server", "flags", "list");
            oldServerFlags.setComments("DEPRECATED in favor of 'start-command'.");
            if (oldServerFlagsEnabled.asBoolean()) {
                for (SmartString flag :
                        oldServerFlags.getValues()) {
                    startCommand += "-" + flag.asString() + " ";
                }
            }

            YamlSection oldServerJar = get(name, "server", "jar-path");
            oldServerJar.setComments("DEPRECATED in favor of 'start-command'.");
            if (oldServerJar.asString().equals("auto-find"))
                startCommand += "-jar " + new FileManager().serverExecutable(GD.WORKING_DIR);
            else startCommand += "-jar \"" + oldServerJar.asString() + "\"";
            startCommand += " ";


            YamlSection oldServerArgsEnabled = get(name, "server", "arguments", "enable");
            oldServerArgsEnabled.setComments("DEPRECATED in favor of 'start-command'.");
            YamlSection oldServerArgs = get(name, "server", "arguments", "list");
            oldServerArgs.setComments("DEPRECATED in favor of 'start-command'.");
            if (oldServerArgsEnabled.asBoolean()) {
                for (SmartString flag :
                        oldServerArgs.getValues()) {
                    startCommand += flag.asString() + " ";
                }
            }

            server_start_command.setValues(startCommand);
            remove(oldJavaPath);
            remove(oldServerFlagsEnabled);
            remove(oldServerFlags);
            remove(oldServerJar);
            remove(oldServerArgsEnabled);
            remove(oldServerArgs);
        }

        server_restart_on_crash = put(name, "server", "restart-on-crash").setDefValues("true");

        put(name, "directory-cleaner").setCountTopLineBreaks(1);
        directory_cleaner = put(name, "directory-cleaner", "enabled").setDefValues("true")
                .setComments("Deletes files older than 'max-days' in the selected directories.");
        directory_cleaner_max_days = put(name, "directory-cleaner", "max-days")
                .setComments("If the file is older than the provided time in days, it gets deleted.").setDefValues("7");
        directory_cleaner_files = put(name, "directory-cleaner", "list")
                .setComments("The list of directories to clean.",
                        "By default sub-directories will not get cleaned, unless you add 'true' before its path, like shown below.",
                        "Supported paths are relative (starting with './' which is the servers root directory) and absolute paths.")
                .setDefValues("true ./autoplug/logs", "./autoplug/downloads");

        if (save) save();
        unlockFile();
    }

    @Override
    public Yaml validateValues() {
        return this;
    }
}
