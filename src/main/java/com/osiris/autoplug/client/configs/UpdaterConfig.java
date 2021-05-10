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

public class UpdaterConfig extends DreamYaml {

    public DYModule self_updater;
    public DYModule self_updater_profile;
    public DYModule self_updater_build;

    public DYModule server_updater;
    public DYModule server_updater_profile;
    public DYModule server_software;
    public DYModule server_version;
    public DYModule build_id;

    public DYModule plugin_updater;
    public DYModule plugin_updater_profile;

    public UpdaterConfig(){
        super(System.getProperty("user.dir")+"/autoplug-updater-config.yml");
        try{
            load();
            String name = getFileNameWithoutExt();
            add(name).setComment("#######################################################################################################################\n" +
                            "    ___       __       ___  __\n" +
                            "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                            "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                            " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                            "                                /___/ Updater-Config\n" +
                            "Thank you for using AutoPlug!\n" +
                            "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                            "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                            "\n" +
                            "#######################################################################################################################\n" +
                    "Available profiles for all updaters are: NOTIFY, MANUAL and AUTOMATIC.\n" +
                    "NOTIFY: Only notifies when updates are available.\n" +
                    "MANUAL: Only downloads the updates to /autoplug-downloads.\n" +
                    "AUTOMATIC: Downloads and installs updates automatically.");

            self_updater = add(name,"self-updater","enable").setDefValue("true").setComments("Executed before mc server startup.",
                    "Responsible for updating AutoPlug.");
            self_updater_profile = add(name,"self-updater","profile").setDefValue("AUTOMATIC");
            self_updater_build = add(name,"self-updater","build").setDefValue("stable").setComments("Choose between 'stable' and 'beta' builds.",
                    "Stable builds are recommended.");

            server_updater = add(name,"server-updater","enable").setDefValue("false").setComment("Executed before mc server startup.");

            server_updater_profile = add(name,"server-updater","profile").setDefValue("MANUAL");
            server_software = add(name,"server-updater","software").setDefValue("paper").setComment(
                    "Select your favorite server software. Enter the name below.\n" +
                            "Currently supported:\n" +
                            "- paper (https://papermc.io/)\n" +
                            "- waterfall (https://github.com/PaperMC/Waterfall)\n" +
                            "- travertine (https://github.com/PaperMC/Travertine)\n" +
                            "Note: If you change this, also reset the \"build-id\" to 0 to guarantee correct update-detection.");
            server_version = add(name,"server-updater","version").setDefValue("1.16.4").setComment(
                    "Currently supported minecraft versions:\n" +
                            "- paper versions: https://papermc.io/api/v2/projects/paper\n" +
                            "- waterfall versions: https://papermc.io/api/v2/projects/waterfall\n" +
                            "- travertine versions: https://papermc.io/api/v2/projects/travertine\n" +
                            "Note: Only update to a newer version if you are sure that all your essential plugins support that version.\n" +
                            "Note: Remember that worlds may not be converted to older versions.\n" +
                            "Note: If you change this, also reset the \"build-id\" to 0 to guarantee correct update-detection.");
            build_id = add(name,"server-updater","build-id").setDefValue("0").setComment(
                    "Each release/update has its unique build-id. First release was 1, the second 2 and so on...\n" +
                            "If you change your server software or mc-version, remember to change this to 0, to ensure proper update-detection.\n" +
                            "Otherwise don't touch this. It will get incremented after every successful update automatically.");


            plugin_updater = add(name,"plugins-updater","enable").setDefValue("true").setComment(
                    "Executed before mc server startup.");
            plugin_updater_profile = add(name,"plugins-updater","profile").setDefValue("MANUAL");

            validateOptions();
            save();

        } catch (Exception e) {
            AL.warn(e);
        }
    }

    private void validateOptions() {
        String selfP = self_updater_profile.asString();
        String sP = server_updater_profile.asString();
        String uP = plugin_updater_profile.asString();

        if (selfP.equals("NOTIFY") || selfP.equals("MANUAL") || selfP.equals("AUTOMATIC") ){
        } else{
            String correction = "NOTIFY";
            AL.warn("Config error -> " + self_updater_profile.getKeys() +" must be: NOTIFY or MANUAL or AUTOMATIC. Applied default!");
            self_updater_profile.setValue(correction);
        }

        if (sP.equals("NOTIFY") || sP.equals("MANUAL") || sP.equals("AUTOMATIC") ){
        } else{
            String correction = "NOTIFY";
            AL.warn("Config error -> " + server_updater_profile.getKeys() +" must be: NOTIFY or MANUAL or AUTOMATIC. Applied default!");
            server_updater_profile.setValue(correction);
        }

        if (uP.equals("NOTIFY") || uP.equals("MANUAL") || uP.equals("AUTOMATIC") ){
        } else{
            String correction = "NOTIFY";
            AL.warn("Config error -> " + plugin_updater_profile.getKeys() +" must be: NOTIFY or MANUAL or AUTOMATIC. Applied default!");
            plugin_updater_profile.setValue(correction);
        }

    }

}
