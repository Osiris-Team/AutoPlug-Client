/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.*;

import java.io.IOException;

public class UpdaterConfig extends Yaml {

    public YamlSection global_cool_down;

    public YamlSection self_updater;
    public YamlSection self_updater_profile;
    public YamlSection self_updater_build;

    public YamlSection java_updater;
    public YamlSection java_updater_profile;
    //public YamlSection java_updater_vendor; // Currently not supported, because only adopt-api is implemented at the moment
    public YamlSection java_updater_version;
    public YamlSection java_updater_build_id;
    public YamlSection java_updater_large_heap;

    public YamlSection server_updater;
    public YamlSection server_updater_profile;
    public YamlSection server_software;
    public YamlSection server_version;
    public YamlSection server_build_id;
    public YamlSection server_skip_hash_check;
    public YamlSection server_github_repo_name;
    public YamlSection server_github_asset_name;
    public YamlSection server_github_version;
    public YamlSection server_jenkins_project_url;
    public YamlSection server_jenkins_artifact_name;
    public YamlSection server_jenkins_build_id;

    public YamlSection plugin_updater;
    public YamlSection plugin_updater_profile;
    public YamlSection plugin_updater_async;

    public UpdaterConfig() throws NotLoadedException, YamlWriterException, IOException, IllegalKeyException, DuplicateKeyException, YamlReaderException, IllegalListException {
        this(ConfigPreset.DEFAULT);
    }

    public UpdaterConfig(ConfigPreset preset) throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, NotLoadedException, IllegalKeyException, YamlWriterException {
        super(System.getProperty("user.dir") + "/autoplug/updater-config.yml");
        lockFile();
        load();
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
                "Available profiles for all updaters are: NOTIFY, MANUAL and AUTOMATIC.\n\n" +

                "NOTIFY:        \tOnly notifies when updates are available.\n" +
                "MANUAL:        \tOnly downloads the updates to /autoplug/downloads.\n" +
                "AUTOMATIC:     \tDownloads and installs updates automatically.");

        put(name, "global-cool-down").setCountTopLineBreaks(1);
        global_cool_down = put(name, "global-cool-down").setDefValues("60").setComments(
                "Cool-down time in minutes to the next updater tasks execution.",
                "Prevents unnecessary spam of updating/update checking tasks and thus shortens the server startup time.",
                "Useful when testing plugins/configs and having to restart the server often in a short amount of time.",
                "Set to 0 to disable.");

        put(name, "self-updater").setCountTopLineBreaks(1);
        self_updater = put(name, "self-updater", "enable").setDefValues("true").setComments(
                "AutoPlug is able to update itself automatically.",
                "Its strongly recommended to have this feature enabled,",
                "to benefit from new features, bug fixes and security enhancements.",
                "Linux users, using screen read this: https://github.com/Osiris-Team/AutoPlug-Client/issues/75");
        self_updater_profile = put(name, "self-updater", "profile").setDefValues("AUTOMATIC");
        self_updater_build = put(name, "self-updater", "build").setDefValues("stable").setComments(
                "Choose between 'stable' and 'beta' builds.",
                "Stable builds are recommended.");

        put(name, "java-updater").setCountTopLineBreaks(1);
        java_updater = put(name, "java-updater", "enable").setDefValues("false");
        java_updater_profile = put(name, "java-updater", "profile").setDefValues("AUTOMATIC").setComments(
                "If you selected the AUTOMATIC profile the 'java-path' value inside 'autoplug-general-config.yml' gets ignored.",
                "Note that this won't update your already existing Java installation, but instead create a new one inside of /autoplug/system/jre, which then will be used to run your server."
        );
        java_updater_version = put(name, "java-updater", "version").setDefValues("17").setComments(
                "The major Java version. List of versions available: https://api.adoptium.net/v3/info/available_releases",
                "Note: If you change this, also set the \"build-id\" value to 0, to guarantee correct update-detection."
        );
        java_updater_build_id = put(name, "java-updater", "build-id").setDefValues("0").setComments(
                "If you change the Java version, remember to set this value to 0, to ensure proper update-detection.",
                "Otherwise don't touch this. It gets replaced after every successful update automatically.");
        java_updater_large_heap = put(name, "java-updater", "large-heap").setDefValues("false").setComments(
                "Only enable if you plan to give your server more than 57gb of ram, otherwise not recommended.");


        put(name, "server-updater").setCountTopLineBreaks(1);
        server_updater = put(name, "server-updater", "enable").setDefValues("false");

        server_updater_profile = put(name, "server-updater", "profile").setDefValues("MANUAL");
        server_software = put(name, "server-updater", "software").setDefValues("paper").setComments(
                "Select your favorite server software. Enter the name below. Supported software:\n" +
                        "- Minecraft (paper, waterfall, travertine, velocity, purpur, fabric)\n" +
                        "-> Learn more about them here: https://papermc.io/ | https://github.com/PaperMC/Waterfall | https://github.com/PaperMC/Travertine | https://github.com/PaperMC/Velocity | https://purpur.pl3x.net/ | https://fabricmc.net/\n" +
                        "Note: If you change this, also reset the \"build-id\" to 0 to guarantee correct update-detection.");
        server_version = put(name, "server-updater", "version").setDefValues("1.18.2").setComments(
                "Select the servers' version. A list of supported version can be found in the links below:\n" +
                        "- Minecraft versions: https://papermc.io/api/v2/projects/paper | https://papermc.io/api/v2/projects/waterfall | https://papermc.io/api/v2/projects/travertine | https://papermc.io/api/v2/projects/velocity | https://purpur.pl3x.net/downloads | https://fabricmc.net/use/installer\n" +
                        "Note: Only update to a newer version if you are sure that all your essential plugins support that version.\n" +
                        "Note: Remember that worlds cannot be converted to older versions.\n" +
                        "Note: If you change this, also reset the \"build-id\" to 0 to guarantee correct update-detection.");
        server_build_id = put(name, "server-updater", "build-id").setDefValues("0").setComments(
                "Each release/update has its unique build-id. First release was 1, the second 2 and so on...\n" +
                        "If you change your server software or mc-version, remember to change this to 0, to ensure proper update-detection.\n" +
                        "Otherwise don't touch this. It will gets updated after every successful update automatically.");
        server_skip_hash_check = put(name, "server-updater", "skip-hash-check").setDefValues("false").setComments(
                "Its not recommended to enable this.");

        server_github_repo_name = put(name, "server-updater", "alternatives", "github", "repo-name").setComments(
                "The github repository name can be found in its url or on its page. Example: EssentialsX/Essentials (full url: https://github.com/EssentialsX/Essentials)"
        );
        server_github_asset_name = put(name, "server-updater", "alternatives", "github", "asset-name").setComments(
                "The name of the release asset to download, without version info. For example 'EssentialsX'."
        );
        server_github_version = put(name, "server-updater", "alternatives", "github", "version").setDefValues("0").setComments(
                "Remember to set this to 0, if you changed the repo-name. Otherwise don't touch it."
        );


        server_jenkins_project_url = put(name, "server-updater", "alternatives", "jenkins", "project-url").setComments(
                "The url of the jenkins project. For example: https://ci.ender.zone/job/EssentialsX/");
        server_jenkins_artifact_name = put(name, "server-updater", "alternatives", "jenkins", "artifact-name").setComments(
                "The name of the artifact to download, without version info. For example 'EssentialsX'.");
        server_jenkins_build_id = put(name, "server-updater", "alternatives", "jenkins", "build-id").setDefValues("0").setComments(
                "Remember to set this to 0, if you changed the project-url or artifact-name. Otherwise don't touch it.");


        put(name, "plugins-updater").setCountTopLineBreaks(1);
        plugin_updater = put(name, "plugins-updater", "enable").setDefValues("true").setComments(
                "Updates your plugins in to /plugins directory.",
                "The results are sent to AutoPlug-Web. You can configure this in the web-config.",
                "Note that there is a web-cool-down (that cannot be changed) of a few hours, to prevent spamming of results to AutoPlug-Web.");
        plugin_updater_profile = put(name, "plugins-updater", "profile").setDefValues("MANUAL");
        plugin_updater_async = put(name, "plugins-updater", "async").setDefValues("true").setComments(
                "Asynchronously checks for updates.",
                "Normally this should be faster than checking for updates synchronously, thus it should be enabled.",
                "The only downside of this is that your log file gets a bit messy.");

        if (preset.equals(ConfigPreset.FAST)) {
            java_updater.setDefValues("true");
            java_updater_profile.setDefValues("AUTOMATIC");
            server_updater.setDefValues("true");
            server_updater_profile.setDefValues("AUTOMATIC");
            plugin_updater_profile.setDefValues("AUTOMATIC");
        }

        validateOptions();
        save();
        unlockFile();
    }

    private void validateOptions() {
        String selfP = self_updater_profile.asString();
        String jP = java_updater_profile.asString();
        String sP = server_updater_profile.asString();
        String uP = plugin_updater_profile.asString();

        if (!selfP.equals("NOTIFY") && !selfP.equals("MANUAL") && !selfP.equals("AUTOMATIC")) {
            String correction = self_updater_profile.getDefValue().asString();
            AL.warn("Config error -> " + self_updater_profile.getKeys() + " must be: NOTIFY or MANUAL or AUTOMATIC. Applied default!");
            self_updater_profile.setValues(correction);
        }

        if (!jP.equals("NOTIFY") && !jP.equals("MANUAL") && !jP.equals("AUTOMATIC")) {
            String correction = java_updater_profile.getDefValue().asString();
            AL.warn("Config error -> " + java_updater_profile.getKeys() + " must be: NOTIFY or MANUAL or AUTOMATIC. Applied default!");
            java_updater_profile.setValues(correction);
        }

        if (!sP.equals("NOTIFY") && !sP.equals("MANUAL") && !sP.equals("AUTOMATIC")) {
            String correction = server_updater_profile.getDefValue().asString();
            AL.warn("Config error -> " + server_updater_profile.getKeys() + " must be: NOTIFY or MANUAL or AUTOMATIC. Applied default!");
            server_updater_profile.setValues(correction);
        }

        if (!uP.equals("NOTIFY") && !uP.equals("MANUAL") && !uP.equals("AUTOMATIC")) {
            String correction = plugin_updater_profile.getDefValue().asString();
            AL.warn("Config error -> " + plugin_updater_profile.getKeys() + " must be: NOTIFY or MANUAL or AUTOMATIC. Applied default!");
            plugin_updater_profile.setValues(correction);
        }

    }

}
