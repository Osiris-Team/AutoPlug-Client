/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;
import com.osiris.dyml.exceptions.*;

import java.io.File;
import java.io.IOException;

public class GeneralConfig extends DreamYaml {
    public DYModule server_key;
    public DYModule server_auto_start;
    public DYModule server_java_version;
    public DYModule server_jar;
    public DYModule server_flags_enabled;
    public DYModule server_flags_list;
    public DYModule server_arguments_enabled;
    public DYModule server_arguments_list;

    public GeneralConfig() throws IOException, DuplicateKeyException, DYReaderException, IllegalListException, DYWriterException, NotLoadedException, IllegalKeyException {
        super(System.getProperty("user.dir") + "/autoplug-general-config.yml");
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


        server_key = put(name, "server", "key").setDefValues("INSERT_KEY_HERE").setComments(
                "Enter your Server-Key here. You get the key by registering yourself and your server on https://autoplug.online.\n" +
                        "The Server-Key is essential to perform most of AutoPlugs main operations and it enables remote access from your account.\n" +
                        "No matter what, keep this key private to ensure your servers security!");

        server_auto_start = put(name, "server", "auto-start").setDefValues("true").setComments(
                "Starts your server with the start of AutoPlug.");

        server_java_version = put(name, "server", "java-version").setDefValues("java").setComments(
                "This is the java version your server will be running on.\n" +
                        "If you plan to use a specific version of java or you don't have the java path as a System-PATH variable, enter its path here.\n" +
                        "Otherwise leave it as it is.\n" +
                        "Example for Windows: C:\\Progra~1\\Java\\jdk-14.0.1\\bin\\java.exe");

        server_jar = put(name, "server", "jar").setDefValues("auto-find").setComments(
                "The auto-find feature will scan through your servers root directory and find the first jar with another name than AutoPlug-Client.jar.\n" +
                        "The auto-find feature will fail if...\n" +
                        "... you have more than 2 jars in your servers root directory (AutoPlug-Client.jar included).\n" +
                        "... your server jar is located in another directory.\n" +
                        "You can fix this by entering its file path (Linux and Windows formats are supported)\n" +
                        "or by entering its file name below, without its .jar file extension (only if AutoPlug-Client is also in the servers root directory).");

        server_flags_enabled = put(name, "server", "flags", "enable").setDefValues("true").setComments(
                "If you were using java startup flags, add them to the list below.",
                "Java startup flags are passed before the -jar part. Example: 'java <flags> -jar ...'",
                "The hyphen(-) in the list below is part of the flag. This is how a flag should look like in the list:",
                "Correct:",
                " - XX:+UseG1GC",
                "Wrong:",
                " - \"- XX:+UseG1GC\"",
                "More on this topic:",
                "https://forums.spongepowered.org/t/optimized-startup-flags-for-consistent-garbage-collection/13239",
                "https://aikar.co/2018/07/02/tuning-the-jvm-g1gc-garbage-collector-flags-for-minecraft/");
        server_flags_list = put(name, "server", "flags", "list").setDefValues("Xms2G", "Xmx2G");

        server_arguments_enabled = put(name, "server", "arguments", "enable").setDefValues("true").setComments(
                "If you were using arguments, add them to the list below.\n" +
                        "Arguments are passed after the '-jar <file-name>.jar' part. Example: '... -jar server.jar <arguments>'\n" +
                        "They can be specific to the server software you are using.\n" +
                        "Note that typos/wrong arguments may prevent your server from starting!\n" +
                        "More on this topic:\n" +
                        "https://minecraft.fandom.com/wiki/Tutorials/Setting_up_a_server\n" +
                        "https://bukkit.gamepedia.com/CraftBukkit_Command_Line_Arguments\n" +
                        "https://www.spigotmc.org/wiki/start-up-parameters");
        server_arguments_list = put(name, "server", "arguments", "list").setDefValues("--nogui");

        validateOptions();
        save();

        setGlobalServerPath();
    }

    private void validateOptions() {
    }


    // Set the path in GD so its easier to access
    private void setGlobalServerPath() {
        FileManager fileManager = new FileManager();
        String jar = server_jar.asString();
        if (!jar.equals("auto-find")) {
            if (jar.contains("/") || jar.contains("\\")) GD.SERVER_PATH = new File(jar);
            else GD.SERVER_PATH = fileManager.serverJar(jar);
        } else GD.SERVER_PATH = fileManager.serverJar();
    }

}
