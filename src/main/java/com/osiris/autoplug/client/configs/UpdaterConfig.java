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
import com.osiris.dyml.exceptions.*;

import java.io.IOException;

public class UpdaterConfig extends DreamYaml {

    public DYModule self_updater;
    public DYModule self_updater_profile;
    public DYModule self_updater_build;

    public DYModule java_updater;
    public DYModule java_updater_profile;
    //public DYModule java_updater_vendor; // Currently not supported, because only adopt-api is implemented at the moment
    public DYModule java_updater_version;
    public DYModule java_updater_build_id;
    public DYModule java_updater_large_heap;

    public DYModule server_updater;
    public DYModule server_updater_profile;
    public DYModule server_software;
    public DYModule server_version;
    public DYModule server_build_id;

    public DYModule plugin_updater;
    public DYModule plugin_updater_profile;
    public DYModule plugin_updater_async;

    public UpdaterConfig() throws IOException, DuplicateKeyException, DYReaderException, IllegalListException, NotLoadedException, IllegalKeyException, DYWriterException {
        super(System.getProperty("user.dir") + "/autoplug-updater-config.yml");
        lockAndLoad();
        String name = getFileNameWithoutExt();
        put(name).setComments("#######################################################################################################################\n" +
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
                "All the updaters below search for updates before your server gets started.\n" +
                "Available profiles for all updaters are: NOTIFY, MANUAL and AUTOMATIC.\n" +
                "NOTIFY: Only notifies when updates are available.\n" +
                "MANUAL: Only downloads the updates to /autoplug-downloads.\n" +
                "AUTOMATIC: Downloads and installs updates automatically.");

        self_updater = put(name, "self-updater", "enable").setDefValues("true").setComments(
                "AutoPlug is able to update itself automatically.",
                "Its strongly recommended to have this feature enabled,",
                "to benefit from new features, bug fixes and security enhancements.");
        self_updater_profile = put(name, "self-updater", "profile").setDefValues("AUTOMATIC");
        self_updater_build = put(name, "self-updater", "build").setDefValues("stable").setComments(
                "Choose between 'stable' and 'beta' builds.",
                "Stable builds are recommended.");

        java_updater = put(name, "java-updater", "enable").setDefValues("false");
        java_updater_profile = put(name, "java-updater", "profile").setDefValues("AUTOMATIC").setComments(
                "If you selected the MANUAL or AUTOMATIC profile the 'java-path' value inside 'autoplug-general-config.yml' gets ignored.",
                "Instead the new Java installation inside of /autoplug-system/jre is used to run your server."
        );
        java_updater_version = put(name, "java-updater", "version").setDefValues("15").setComments(
                "The major Java version. List of versions available: https://api.adoptopenjdk.net/v3/info/available_releases",
                "Note: If you change this, also remove the \"build-id\" value to guarantee correct update-detection."
        );
        java_updater_build_id = put(name, "java-updater", "build-id").setComments(
                "If you change the Java version, remember to remove this value, to ensure proper update-detection.",
                "Otherwise don't touch this. It gets replaced after every successful update automatically.");
        java_updater_large_heap = put(name, "java-updater", "large-heap").setDefValues("false").setComments(
                "Only enable if you plan to give your server more than 57gb of ram, otherwise not recommended.");

        server_updater = put(name, "server-updater", "enable").setDefValues("false");

        server_updater_profile = put(name, "server-updater", "profile").setDefValues("MANUAL");
        server_software = put(name, "server-updater", "software").setDefValues("paper").setComments(
                "Select your favorite server software. Enter the name below.\n" +
                        "Currently supported:\n" +
                        "- paper (https://papermc.io/)\n" +
                        "- waterfall (https://github.com/PaperMC/Waterfall)\n" +
                        "- travertine (https://github.com/PaperMC/Travertine)\n" +
                        "Note: If you change this, also reset the \"build-id\" to 0 to guarantee correct update-detection.");
        server_version = put(name, "server-updater", "version").setDefValues("1.16.4").setComments(
                "Currently supported minecraft versions:\n" +
                        "- paper versions: https://papermc.io/api/v2/projects/paper\n" +
                        "- waterfall versions: https://papermc.io/api/v2/projects/waterfall\n" +
                        "- travertine versions: https://papermc.io/api/v2/projects/travertine\n" +
                        "Note: Only update to a newer version if you are sure that all your essential plugins support that version.\n" +
                        "Note: Remember that worlds may not be converted to older versions.\n" +
                        "Note: If you change this, also reset the \"build-id\" to 0 to guarantee correct update-detection.");
        server_build_id = put(name, "server-updater", "build-id").setDefValues("0").setComments(
                "Each release/update has its unique build-id. First release was 1, the second 2 and so on...\n" +
                        "If you change your server software or mc-version, remember to change this to 0, to ensure proper update-detection.\n" +
                        "Otherwise don't touch this. It will get incremented after every successful update automatically.");


        plugin_updater = put(name, "plugins-updater", "enable").setDefValues("true").setComments(
                "Updates your plugins in to /plugins directory.",
                "The results are sent to AutoPlug-Web. You can configure this in the web-config.",
                "Note that there is a web-cool-down (that cannot be changed) of a few hours, to prevent spamming of results to AutoPlug-Web.");
        plugin_updater_profile = put(name, "plugins-updater", "profile").setDefValues("MANUAL");
        plugin_updater_async = put(name, "plugins-updater", "async").setDefValues("true").setComments(
                "Asynchronously checks for updates.",
                "Normally this should be faster than checking for updates synchronously, thus it should be enabled.",
                "The only downside of this is that your log file gets a bit messy.");

        validateOptions();
        saveAndUnlock();
    }

    private void validateOptions() {
        String selfP = self_updater_profile.asString();
        String jP = java_updater_profile.asString();
        String sP = server_updater_profile.asString();
        String uP = plugin_updater_profile.asString();

        if (!selfP.equals("NOTIFY") && !selfP.equals("MANUAL") && !selfP.equals("AUTOMATIC")) {
            String correction = "NOTIFY";
            AL.warn("Config error -> " + self_updater_profile.getKeys() + " must be: NOTIFY or MANUAL or AUTOMATIC. Applied default!");
            self_updater_profile.setValues(correction);
        }

        if (!jP.equals("NOTIFY") && !jP.equals("MANUAL") && !jP.equals("AUTOMATIC")) {
            String correction = "NOTIFY";
            AL.warn("Config error -> " + java_updater_profile.getKeys() + " must be: NOTIFY or MANUAL or AUTOMATIC. Applied default!");
            java_updater_profile.setValues(correction);
        }

        if (!sP.equals("NOTIFY") && !sP.equals("MANUAL") && !sP.equals("AUTOMATIC")) {
            String correction = "NOTIFY";
            AL.warn("Config error -> " + server_updater_profile.getKeys() + " must be: NOTIFY or MANUAL or AUTOMATIC. Applied default!");
            server_updater_profile.setValues(correction);
        }

        if (!uP.equals("NOTIFY") && !uP.equals("MANUAL") && !uP.equals("AUTOMATIC")) {
            String correction = "NOTIFY";
            AL.warn("Config error -> " + plugin_updater_profile.getKeys() + " must be: NOTIFY or MANUAL or AUTOMATIC. Applied default!");
            plugin_updater_profile.setValues(correction);
        }

    }

}
