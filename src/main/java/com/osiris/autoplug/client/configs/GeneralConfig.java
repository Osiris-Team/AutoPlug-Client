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
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;

import java.io.File;

public class GeneralConfig extends DreamYaml {
    public DYModule server_key;
    public DYModule server_java_version;
    public DYModule server_jar;
    public DYModule server_flags_enabled;
    public DYModule server_flags_list;
    public DYModule server_arguments_enabled;
    public DYModule server_arguments_list;
    public DYModule cool_down;

    public GeneralConfig(){
        super(System.getProperty("user.dir")+"/autoplug-general-config.yml");
        try{
            load();
            String name = getFileNameWithoutExt();
            add(name).setComment(
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


            server_key = add(name,"server","key").setDefValue("INSERT_KEY_HERE").setComment(
                    "Enter your server-key here. You get the key by registering on https://autoplug.online.\n" +
                            "The key is essential to perform most of AutoPlugs main operations and it enables remote access from your account.\n" +
                            "No matter what, keep this key private to ensure your servers security!");

            server_java_version = add(name,"server","java-version").setDefValue("java").setComment(
                    "This is the java version your server will be running on.\n" +
                            "If you plan to use a specific version of java or you don't have the java path as a System-PATH variable, enter its path here.\n" +
                            "Otherwise leave it as it is.\n" +
                            "Example for windows: C:\\Progra~1\\Java\\jdk-14.0.1\\bin\\java.exe");

            server_jar = add(name,"server","jar").setDefValue("auto-find").setComment(
                    "The auto-find feature will scan through your root directory and find the first jar with another name than AutoPlugLauncher.jar.\n" +
                            "The auto-find feature will fail if...\n" +
                            "... you have more than 2 jars in your root directory.\n" +
                            "... your server jar is located in another directory.\n" +
                            "You can fix this by entering its file path (linux and windows formats are supported)\n" +
                            "or by entering its file name below, without its .jar file extension (only if AutoPlug is also in the root directory).");

            server_flags_enabled = add(name,"server","flags","enable").setDefValue("true").setComment(
                    "If you were using java startup flags, add them to the list below.\n" +
                            "Java startup flags are passed before the -jar part. Example: 'java <flags> -jar ...'\n" +
                    "For flags like - -XX:+UseG1GC (with 2 bars) remove one bar, so it looks like this in the list: - XX:+UseG1GC\n" +
                    "More on this topic:\n" +
                    "https://forums.spongepowered.org/t/optimized-startup-flags-for-consistent-garbage-collection/13239\n" +
                            "https://aikar.co/2018/07/02/tuning-the-jvm-g1gc-garbage-collector-flags-for-minecraft/");
            server_flags_list = add(name,"server","flags","list").setDefValues("Xms2G","Xmx2G");

            server_arguments_enabled = add(name,"server","arguments","enable").setDefValue("true").setComment(
                    "If you were using arguments, add them to the list below.\n" +
                            "Arguments are passed after the '-jar <file-name>.jar' part. Example: '... -jar server.jar <arguments>'\n" +
                    "They can be specific to the server software you are using.\n" +
                    "More on this topic:\n" +
                            "https://minecraft.fandom.com/wiki/Tutorials/Setting_up_a_server\n" +
                            "https://bukkit.gamepedia.com/CraftBukkit_Command_Line_Arguments\n" +
                    "https://www.spigotmc.org/wiki/start-up-parameters");
            server_arguments_list = add(name,"server","arguments","list").setDefValues("--help","--nogui");


            cool_down = add(name,"cool-down").setDefValue("5").setComment("Determines the 'before startup tasks' cool-down in minutes. Minimum is 5.");

            validateOptions();
            save();

        } catch (Exception e) {
            AL.error(e);
        }

        setGlobalServerPath();
    }

    private void validateOptions() {
        int cool = 0;
        try{
            cool = cool_down.asInt();
        } catch (Exception ignored) {
        }

        if (cool<5){
            cool_down.setValue("5");
        }
    }


    // Set the path in GD so its easier to access
    private void setGlobalServerPath() {
        FileManager fileManager = new FileManager();
        String jar = server_jar.asString();
        if (!jar.equals("auto-find")){
            if (jar.contains("/") || jar.contains("\\")) GD.SERVER_PATH = new File(jar);
            else GD.SERVER_PATH = fileManager.serverJar(jar);
        }
        else GD.SERVER_PATH = fileManager.serverJar();
    }

}
