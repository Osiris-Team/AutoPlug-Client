/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.scheduler.TaskScheduler;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import org.simpleyaml.configuration.file.YamlFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RestarterConfig {

    public RestarterConfig(){
        AutoPlugLogger.newClassDebug("RestarterConfig");
    }

    private final YamlFile config = new YamlFile("autoplug-restarter-config.yml");

    public void load(){

        // Load the YAML file if is already created or create new one otherwise
        try {
            if (!config.exists()) {
                AutoPlugLogger.info(" - autoplug-restarter-config.yml not found! Creating new one...");
                config.createNewFile(true);
                AutoPlugLogger.debug("create", "Created file at: " + config.getFilePath());
            }
            else {
                AutoPlugLogger.info(" - Loading autoplug-restarter-config.yml...");
            }
            config.load(); // Loads the entire file
        } catch (Exception e) {
            AutoPlugLogger.warn("Failed to load autoplug-restarter-config.yml...");
            e.printStackTrace();
        }

        // Insert defaults
        insertDefaults();

        // Makes settings globally accessible
        setUserOptions();

        // Validates options
        validateOptions();

        //Finally save the file
        save();

    }

    private void insertDefaults(){

        config.addDefault("autoplug-restarter-config.daily-restarter.enable", false);
        List<String> times = Arrays.asList("23:00 11:00".split("[\\s]+"));
        config.addDefault("autoplug-restarter-config.daily-restarter.times", times);
        List<String> commands = Arrays.asList("say [Server] Server is restarting...","say [Server] Please allow up to 2min for this process to complete.");
        config.addDefault("autoplug-restarter-config.daily-restarter.commands", commands);

    }

    //User configuration
    public static boolean restarter_enabled;
    public static List<String> restarter_times_raw;
    public static List<Integer> restarter_times_minutes = new ArrayList<>();
    public static List<Integer> restarter_times_hours = new ArrayList<>();
    public static List<String> restarter_commands;

    private void setUserOptions(){

        AutoPlugLogger.debug("setUserOptions", "Applying values for autoplug-restarter-config.yml");

        //DAILY RESTARTER
        restarter_enabled = config.getBoolean("autoplug-restarter-config.daily-restarter.enable");
        debugConfig("restarter_enabled", String.valueOf(restarter_enabled));
        restarter_times_raw = config.getStringList("autoplug-restarter-config.daily-restarter.times");
        debugConfig("restarter_times_raw", String.valueOf(restarter_times_raw));
        restarter_commands = config.getStringList("autoplug-restarter-config.daily-restarter.commands");
        debugConfig("restarter_commands", String.valueOf(restarter_commands));

    }

    private void validateOptions() {

        //Get the config string list
        //Split each time up into hours and min to validate them
        //Pass the int list over to the scheduler
        if (restarter_enabled){

            for (int i = 0; i < restarter_times_raw.size(); i++) {

                //Splits up time. ex.: 22:10 into 22 and 10
                String[] raw_times = restarter_times_raw.get(i).split(":");

                AutoPlugLogger.debug("validateTimes", Arrays.toString(raw_times));

                //Validate:
                //Hours must be between: 0-23
                int hour = Integer.parseInt(raw_times[0]);
                if (hour>23 || hour<0){
                    AutoPlugLogger.warn("Config error at daily-restarter.times -> " + hour +"h is not between 0h and 23h! Applying default: 0");
                    raw_times[0] = "0";
                }

                //Minutes must be between: 0-59
                int minute = Integer.parseInt(raw_times[1]);
                if (minute>59 || minute<0){
                    AutoPlugLogger.warn("Config error at daily-restarter.times -> " + minute +"min is not between 0min and 59min! Applying default: 0");
                    raw_times[1] = "0";
                }

                //Separate the validated values into two lists
                restarter_times_hours.add(Integer.parseInt(raw_times[0]));
                restarter_times_minutes.add(Integer.parseInt(raw_times[1]));

            }

            //After we got all information we create the scheduler and pass it over
            TaskScheduler scheduler = new TaskScheduler();
            scheduler.createScheduler();


        }

    }

    private void debugConfig(String config_name, String config_value){
        AutoPlugLogger.debug("debugConfig", "Setting value "+config_name+": "+config_value+"");
    }

    private void save() {

        // Finally, save changes!
        try {
            config.saveWithComments();
        } catch (IOException e) {
            e.printStackTrace();
            AutoPlugLogger.warn("Issues while saving config.yml");
        }

        AutoPlugLogger.info(" - Configuration file loaded!");

    }

}
