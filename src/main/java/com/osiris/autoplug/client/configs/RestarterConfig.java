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
import com.osiris.dyml.utils.UtilsYamlSection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RestarterConfig extends Yaml {

    public YamlSection restarter_enabled;
    public YamlSection restarter_times_raw;
    @NotNull
    public List<Integer> restarter_times_minutes = new ArrayList<>();
    @NotNull
    public List<Integer> restarter_times_hours = new ArrayList<>();
    public YamlSection restarter_commands;

    public YamlSection c_restarter_enabled;
    public YamlSection c_restarter_cron;
    public YamlSection c_restarter_commands;


    public RestarterConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, YamlWriterException, NotLoadedException, IllegalKeyException {
        super(System.getProperty("user.dir") + "/autoplug/restarter.yml");
        lockFile();
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ Restarter-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                        "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################");

        put(name, "daily-restarter").setCountTopLineBreaks(1);
        restarter_enabled = put(name, "daily-restarter", "enable").setDefValues("true").setComments(
                "Enable/Disable the scheduler for restarting your minecraft server on a daily basis.\n" +
                        "Make sure to have the other scheduler disabled.");

        restarter_times_raw = put(name, "daily-restarter", "times").setDefValues("05:00").setComments(
                "Restarts your server daily at the times below.\n" +
                        "You can add max 10x times to restart (hours must be within 0-23 and minutes within 0-59).",
                "Multiple times can be added like this:",
                "times:",
                "  - 04:00",
                "  - 12:00",
                "  - and so on...");

        restarter_commands = put(name, "daily-restarter", "commands", "list").setComments("Executes these commands as console, before restarting the server.",
                "You can execute multiple/single commands at any given second of the countdown.",
                "The countdown starts at the highest given number.");
        if (restarter_commands.getChildModules().isEmpty()) {
            put(name, "daily-restarter", "commands", "list", "10").setDefValues("say Restarting in 10 seconds.", "say Please allow up to 2min for this process to complete.");
            put(name, "daily-restarter", "commands", "list", "3").setDefValues("say Restarting in 3.");
            put(name, "daily-restarter", "commands", "list", "2").setDefValues("say Restarting in 2.");
            put(name, "daily-restarter", "commands", "list", "1").setDefValues("say Restarting in 1.");
            put(name, "daily-restarter", "commands", "list", "0").setDefValues("say Restarting...");
        }
        UtilsYamlSection utilsYamlSection = new UtilsYamlSection();
        for (YamlSection m :
                restarter_commands.getChildModules()) {
            if (utilsYamlSection.getExisting(m, getAllInEdit()) == null)
                getAllInEdit().add(m); // So that these don't get marked as deprecated
        }

        put(name, "custom-restarter").setCountTopLineBreaks(1);
        c_restarter_enabled = put(name, "custom-restarter", "enable").setDefValues("false").setComments(
                "Enable/Disable the custom scheduler for restarting your minecraft server.\n" +
                        "Make sure to have the other scheduler disabled.\n" +
                        "This scheduler uses a quartz-cron-expression (https://wikipedia.org/wiki/Cron) to execute the restart.");

        c_restarter_cron = put(name, "custom-restarter", "cron").setDefValues("0 30 9 * * ? *").setComments(
                "This example will restart your server daily at 9:30 (0 30 9 * * ? *).\n" +
                        "Use this tool to setup your cron expression: https://www.freeformatter.com/cron-expression-generator-quartz.html");

        c_restarter_commands = put(name, "custom-restarter", "commands", "list");
        if (c_restarter_commands.getChildModules().isEmpty()) {
            put(name, "custom-restarter", "commands", "list", "10").setDefValues("say Restarting in 10 seconds.", "say Please allow up to 2min for this process to complete.");
            put(name, "custom-restarter", "commands", "list", "3").setDefValues("say Restarting in 3.");
            put(name, "custom-restarter", "commands", "list", "2").setDefValues("say Restarting in 2.");
            put(name, "custom-restarter", "commands", "list", "1").setDefValues("say Restarting in 1.");
            put(name, "custom-restarter", "commands", "list", "0").setDefValues("say Restarting...");
        }
        for (YamlSection m :
                c_restarter_commands.getChildModules()) {
            if (utilsYamlSection.getExisting(m, getAllInEdit()) == null)
                getAllInEdit().add(m); // So that these don't get marked as deprecated
        }

        validateOptions();
        save();
        unlockFile();
    }

    private void validateOptions() {

        //Get the config string list
        //Split each time up into hours and min to validate them
        //Pass the int list over to the scheduler
        if (restarter_enabled.asBoolean()) {

            List<String> list = restarter_times_raw.asStringList();
            for (int i = 0; i < list.size(); i++) {

                //Splits up time. ex.: 22:10 into 22 and 10
                String[] raw_times = list.get(i).split(":");

                AL.debug(this.getClass(), Arrays.toString(raw_times));

                //Validate:
                //Hours must be between: 0-23
                int hour = Integer.parseInt(raw_times[0]);
                if (hour > 23 || hour < 0) {
                    AL.warn("Config error at daily-restarter.times -> " + hour + "h is not between 0h and 23h! Applying default: 0");
                    raw_times[0] = "0";
                }

                //Minutes must be between: 0-59
                int minute = Integer.parseInt(raw_times[1]);
                if (minute > 59 || minute < 0) {
                    AL.warn("Config error at daily-restarter.times -> " + minute + "min is not between 0min and 59min! Applying default: 0");
                    raw_times[1] = "0";
                }

                //Separate the validated values into two lists
                restarter_times_hours.add(Integer.parseInt(raw_times[0]));
                restarter_times_minutes.add(Integer.parseInt(raw_times[1]));

            }

        }


        for (YamlSection m :
                restarter_commands.getChildModules()) {
            try {
                Integer.parseInt(m.getLastKey());
            } catch (Exception e) {
                this.remove(m);
                AL.warn("Removed module: " + m.getKeys() + " because of invalid countdown time!", e);
            }
        }

        for (YamlSection m :
                c_restarter_commands.getChildModules()) {
            try {
                Integer.parseInt(m.getLastKey());
            } catch (Exception e) {
                this.remove(m);
                AL.warn("Removed module: " + m.getKeys() + " because of invalid countdown time!", e);
            }
        }

    }

}
