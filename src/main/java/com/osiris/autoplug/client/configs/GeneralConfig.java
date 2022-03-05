/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.*;

import java.io.IOException;

public class GeneralConfig extends Yaml {
    public YamlSection autoplug_auto_stop;

    public YamlSection server_key;
    public YamlSection server_auto_start;
    public YamlSection server_auto_eula;
    public YamlSection server_stop_command;
    public YamlSection server_java_path;
    public YamlSection server_jar;
    public YamlSection server_flags_enabled;
    public YamlSection server_flags_list;
    public YamlSection server_arguments_enabled;
    public YamlSection server_arguments_list;
    public YamlSection server_restart_on_crash;

    public YamlSection directory_cleaner;
    public YamlSection directory_cleaner_max_days;
    public YamlSection directory_cleaner_files;

    public GeneralConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, YamlWriterException, NotLoadedException, IllegalKeyException {
        super(System.getProperty("user.dir") + "/autoplug/general-config.yml");
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
                        "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                        "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################");

        put(name, "autoplug").setCountTopLineBreaks(1);
        autoplug_auto_stop = put(name, "autoplug", "auto-stop").setDefValues("false").setComments(
                "Stops AutoPlug when your server stops. Enabling this feature is not recommended.");

        put(name, "server").setCountTopLineBreaks(1);
        server_key = put(name, "server", "key").setDefValues("INSERT_KEY_HERE").setComments(
                "Enter your Server-Key here. You get it by registering yourself and your server on https://autoplug.one.\n" +
                        "The Server-Key enables remote access from your account.\n" +
                        "No matter what, keep this key private to ensure your servers security!");

        server_auto_start = put(name, "server", "auto-start").setDefValues("true").setComments(
                "Starts your server with the start of AutoPlug.");
        server_auto_eula = put(name, "server", "auto-eula").setDefValues("true").setComments(
                "Creates an eula.txt file if not existing and accepts it.");

        server_stop_command = put(name, "server", "stop-command").setDefValues("stop").setComments(
                "AutoPlug uses this command to stop your server.");

        server_java_path = put(name, "server", "java-path").setDefValues("java").setComments(
                "This is the Java version your server will be running on.",
                "If you plan to use a specific version of Java or you don't have the Java path as a System-PATH variable, enter its path here.",
                "Otherwise leave it as it is.",
                "Example for Windows: C:\\Progra~1\\Java\\jdk-14.0.1\\bin\\java.exe",
                "Note that this value gets ignored if you have the 'java-updater' enabled.");

        server_jar = put(name, "server", "jar-path").setDefValues("auto-find").setComments(
                "The auto-find feature will scan through your servers root directory and select the first jar other than the AutoPlug-Client.jar.\n" +
                        "The auto-find feature could fail or pick the wrong jar if...\n" +
                        "... you have more than 2 jars in total, in your servers root directory.\n" +
                        "... your server jar is located in another directory.\n" +
                        "You can fix these by entering its absolute or relative file path below (Linux and Windows formats are supported).\n" +
                        "'./' represents AutoPlugs current working directory (the server root). \n" +
                        "Relative file paths examples: './paper.jar' or './my-server.jar' (AutoPlug translates them to absolute file paths automatically)\n" +
                        "Absolute file paths examples: Linux: '/user/servers/mc-survival/paper.jar' or Windows: 'D:\\John\\MC-SERVERS\\survival\\my-server.jar' \n");

        server_flags_enabled = put(name, "server", "flags", "enable").setDefValues("true").setComments(
                "If you were using java startup flags, add them to the list below.",
                "Java startup flags are passed before the -jar part. Example: 'java <flags> -jar ...'",
                "The hyphen(-) in the list below is part of the flag. This is how a flag should look like in the list:",
                "Correct:",
                " - XX:+UseG1GC",
                "Wrong:",
                " - \"- XX:+UseG1GC\"",
                "If you want to change the timezone add this flag: -Duser.timezone=\"America/New_York\"",
                "A full list of all timezones: https://garygregory.wordpress.com/2013/06/18/what-are-the-java-timezone-ids/",
                "More on this topic:",
                "https://forums.spongepowered.org/t/optimized-startup-flags-for-consistent-garbage-collection/13239",
                "https://aikar.co/2018/07/02/tuning-the-jvm-g1gc-garbage-collector-flags-for-minecraft/");
        server_flags_list = put(name, "server", "flags", "list").setDefValues("Xms2G", "Xmx2G");

        server_arguments_enabled = put(name, "server", "arguments", "enable").setDefValues("false").setComments(
                "If you were using arguments, add them to the list below.\n" +
                        "Arguments are passed after the '-jar <file-name>.jar' part. Example: '... -jar server.jar <arguments>'\n" +
                        "They can be specific to the server software you are using.\n" +
                        "Note that typos/wrong arguments may prevent your server from starting!\n" +
                        "More on this topic:\n" +
                        "https://minecraft.fandom.com/wiki/Tutorials/Setting_up_a_server\n" +
                        "https://bukkit.gamepedia.com/CraftBukkit_Command_Line_Arguments\n" +
                        "https://www.spigotmc.org/wiki/start-up-parameters");
        server_arguments_list = put(name, "server", "arguments", "list").setDefValues("--nogui");

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

        validateOptions();
        save();
        unlockFile();
    }

    private void validateOptions() {
    }

}
