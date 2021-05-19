/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggerConfig extends DreamYaml {

    public DYModule debug;

    public LoggerConfig() {
        super(System.getProperty("user.dir") + "/autoplug-logger-config.yml");
        try {
            load();
            String name = getFileNameWithoutExt();
            add(name).setComment(
                    "#######################################################################################################################\n" +
                            "    ___       __       ___  __\n" +
                            "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                            "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                            " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                            "                                /___/ Logger-Config\n" +
                            "Thank you for using AutoPlug!\n" +
                            "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                            "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                            "\n" +
                            "#######################################################################################################################");

            debug = add(name, "debug").setDefValue("false").setComment(
                    "Writes the debug output to console.\n" +
                            "The log file contains the debug output by default and this option wont affect that.\n" +
                            "This is the only setting that needs a restart to work.");

            extraDebugOptions();
            save();

        } catch (Exception e) {
            AL.error(e);
        }
    }

    private void extraDebugOptions() {
        //Enable debug mode for libs
        Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
        if (debug.asBoolean()) {
            AL.debug(this.getClass(), "Enabled HtmlUnit logger!");
            Logger.getLogger("com.gargoylesoftware").setLevel(Level.ALL);
        }
    }

}
