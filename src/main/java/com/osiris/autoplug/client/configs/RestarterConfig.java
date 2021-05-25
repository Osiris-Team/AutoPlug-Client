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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RestarterConfig extends DreamYaml {

    public DYModule restarter_enabled;
    public DYModule restarter_times_raw;
    public List<Integer> restarter_times_minutes = new ArrayList<>();
    public List<Integer> restarter_times_hours = new ArrayList<>();
    public DYModule restarter_commands;

    public DYModule c_restarter_enabled;
    public DYModule c_restarter_cron;
    public DYModule c_restarter_commands;

    public RestarterConfig() {
        super(System.getProperty("user.dir") + "/autoplug-restarter-config.yml");

        try {
            load();
            String name = getFileNameWithoutExt();
            add(name).setComment(
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

            restarter_enabled = add(name, "daily-restarter", "enable").setDefValue("false").setComment(
                    "Enable/Disable the scheduler for restarting your minecraft server on a daily basis.\n" +
                            "Make sure to have the other scheduler disabled.");

            restarter_times_raw = add(name, "daily-restarter", "times").setDefValues("23:00", "11:00").setComment(
                    "Restarts your server daily at the times below.\n" +
                            "You can add max 10x times to restart (hours must be within 0-23 and minutes within 0-59).");

            restarter_commands = add(name, "daily-restarter", "commands").setDefValues("say [Server] Server is restarting...", "say [Server] Please allow up to 2min for this process to complete.")
                    .setComment("Executes these server as console, 10 seconds before restarting the server.");
            /*
            TODO WORK IN PROGRESS
            restarter_commands_countdown = add(name, "daily-restarter", "commands", "countdown").setDefValue("10")
                    .setComment("The amount of seconds to wait before restarting the server.");
            restarter_commands = add(name, "daily-restarter", "commands","list").setDefValues(
                    new DYModule("10").addValues("say [Server] Server is restarting in 10 seconds.", "say [Server] Please allow up to 2min for this process to complete."),
                    new DYModule("3").addValue("say [Server] Server is restarting 3..."),
                    new DYModule("2").addValue("say [Server] Server is restarting 2..."),
                    new DYModule("1").addValue("say [Server] Server is restarting 1..."),
                    new DYModule("0").addValue("say [Server] Server is restarting..."))
                    .setComments("Executes these commands as console, before restarting the server.",
                            "You can execute multiple/single commands at any given second of the countdown.");
             */

            c_restarter_enabled = add(name, "custom-restarter", "enable").setDefValue("false").setComment(
                    "Enable/Disable the custom scheduler for restarting your minecraft server.\n" +
                            "Make sure to have the other scheduler disabled.\n" +
                            "This scheduler uses a quartz-cron-expression (https://wikipedia.org/wiki/Cron) to execute the restart.");

            c_restarter_cron = add(name, "custom-restarter", "cron").setDefValue("0 30 9 * * ? *").setComment(
                    "This example will restart your server daily at 9:30 (0 30 9 * * ? *).\n" +
                            "Use this tool to setup your cron expression: https://www.freeformatter.com/cron-expression-generator-quartz.html");

            c_restarter_commands = add(name, "custom-restarter", "commands").setDefValues("say [Server] Server is restarting...", "say [Server] Please allow up to 2min for this process to complete.")
                    .setComment("Executes these server as console, 10 seconds before restarting the server.");
            /*
            TODO WORK IN PROGRESS
            c_restarter_commands_countdown = add(name, "custom-restarter", "commands", "countdown").setDefValue("10");
            c_restarter_commands = add(name, "custom-restarter", "commands", "list").setDefValues(
                    new DYModule("10").addValues("say [Server] Server is restarting in 10 seconds.", "say [Server] Please allow up to 2min for this process to complete."),
                    new DYModule("3").addValue("say [Server] Server is restarting 3..."),
                    new DYModule("2").addValue("say [Server] Server is restarting 2..."),
                    new DYModule("1").addValue("say [Server] Server is restarting 1..."),
                    new DYModule("0").addValue("say [Server] Server is restarting...")
            );
             */

            validateOptions();

            save();

        } catch (Exception e) {
            AL.error(e);
        }

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

    }

}
