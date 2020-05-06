/*
 *  Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.mcserver;

import com.osiris.autoplug.client.Settings;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UpdateServerJar {

    public UpdateServerJar (Path path)  {
        AutoPlugLogger logger = new AutoPlugLogger();
        logger.global_info(" Updating jar: "+path);

        File jar_path = path.toFile();

        Settings settings = new Settings();
        String server = settings.getConfig_server_software();
        String version = settings.getConfig_server_version();

        String working_dir = System.getProperty("user.dir");
        File autoplug_backups = new File(working_dir+"\\autoplug-backups");
        File autoplug_backups_server = new File(working_dir+"\\autoplug-backups\\server");
        File autoplug_backups_plugins = new File(working_dir+"\\autoplug-backups\\plugins");

        if (server.equals("PAPER")) {

            if (version.equals("1.15")){
                try {
                    logger.global_info(" Downloading latest paper jar for 1.15.x");
                    if (jar_path.exists()){
                        LocalDateTime myDateObj = LocalDateTime.now();
                        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
                        String formattedDate = myDateObj.format(myFormatObj);
                        File dest = new File(autoplug_backups_server.getAbsolutePath()+"\\old-paper1.15.x-"+formattedDate+".jar");
                        FileUtils.copyFile(jar_path, dest);
                        jar_path.delete();
                    }
                    URL url = new URL("https://papermc.io/api/v1/paper/1.15.2/latest/download");
                    FileUtils.copyURLToFile(url, jar_path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if (version.equals("1.14")){
                try {
                    logger.global_info(" Downloading latest paper jar for 1.14.x");
                    if (jar_path.exists()){
                        LocalDateTime myDateObj = LocalDateTime.now();
                        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
                        String formattedDate = myDateObj.format(myFormatObj);
                        File dest = new File(autoplug_backups_server.getAbsolutePath()+"\\old-paper1.14.x-"+formattedDate+".jar");
                        FileUtils.copyFile(jar_path, dest);
                        jar_path.delete();
                    }
                    URL url = new URL("https://papermc.io/api/v1/paper/1.14.4/latest/download");
                    FileUtils.copyURLToFile(url, jar_path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if (version.equals("1.13")){
                try {
                    logger.global_info(" Downloading latest paper jar for 1.13.x");
                    if (jar_path.exists()){
                        LocalDateTime myDateObj = LocalDateTime.now();
                        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
                        String formattedDate = myDateObj.format(myFormatObj);
                        File dest = new File(autoplug_backups_server.getAbsolutePath()+"\\old-paper1.13.x-"+formattedDate+".jar");
                        FileUtils.copyFile(jar_path, dest);
                        jar_path.delete();
                    }
                    URL url = new URL("https://papermc.io/api/v1/paper/1.13.2/latest/download");
                    FileUtils.copyURLToFile(url, jar_path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if (version.equals("1.12")){
                try {
                    logger.global_info(" Downloading latest paper jar for 1.12.x");
                    if (jar_path.exists()){
                        LocalDateTime myDateObj = LocalDateTime.now();
                        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
                        String formattedDate = myDateObj.format(myFormatObj);
                        File dest = new File(autoplug_backups_server.getAbsolutePath()+"\\old-paper1.12.x-"+formattedDate+".jar");
                        FileUtils.copyFile(jar_path, dest);
                        jar_path.delete();
                    }
                    URL url = new URL("https://papermc.io/api/v1/paper/1.12.2/latest/download");
                    FileUtils.copyURLToFile(url, jar_path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if (version.equals("1.8")){
                try {
                    logger.global_info(" Downloading latest paper jar for 1.8.x");
                    if (jar_path.exists()){
                        LocalDateTime myDateObj = LocalDateTime.now();
                        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
                        String formattedDate = myDateObj.format(myFormatObj);
                        File dest = new File(autoplug_backups_server.getAbsolutePath()+"\\old-paper1.8.x-"+formattedDate+".jar");
                        FileUtils.copyFile(jar_path, dest);
                        jar_path.delete();
                    }
                    URL url = new URL("https://papermc.io/api/v1/paper/1.8.8/latest/download");
                    FileUtils.copyURLToFile(url, jar_path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else {
                logger.global_warn("This server-version is NOT supported: " + version);
                logger.global_warn("Please check the config file for supported server versions!");
            }

        }
        else if (server.equals("SPIGOT")) {
            logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            if (version.equals("1.15")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.14")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.13")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.12")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.8")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else {
                logger.global_warn("This server-version is NOT supported: " + version);
                logger.global_warn("Please check the config file for supported server versions!");
            }

        }
        else if (server.equals("CRAFTBUKKIT")) {

            if (version.equals("1.15")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.14")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.13")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.12")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.8")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else {
                logger.global_warn("This server-version is NOT supported: " + version);
                logger.global_warn("Please check the config file for supported server versions!");
            }

        }

        else if (server.equals("VANILLA")) {

            if (version.equals("1.15")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.14")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.13")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.12")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.8")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else {
                logger.global_warn("This server-version is NOT supported: " + version);
                logger.global_warn("Please check the config file for supported server versions!");
            }



        }
        else{
            logger.global_warn("This server-software is NOT supported: " + server);
            logger.global_warn("Please check the config file for supported server software!");
        }


    }

}
