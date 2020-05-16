/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
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

public class UpdateServerJar {

    public UpdateServerJar (File jar_path)  {
        AutoPlugLogger logger = new AutoPlugLogger();
        logger.global_info(" Updating jar: "+ jar_path);


        Settings settings = new Settings();
        String server = settings.getServer_software();
        String version = settings.getServer_version();


        if (server.equals("PAPER")) {

            if (version.equals("1.15.x")){
                try {
                    logger.global_info(" Downloading latest paper jar for 1.15.x");
                    if (jar_path.exists()){

                        jar_path.delete();
                    }
                    URL url = new URL("https://papermc.io/api/v1/paper/1.15.2/latest/download");
                    FileUtils.copyURLToFile(url, jar_path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if (version.equals("1.14.x")){
                try {
                    logger.global_info(" Downloading latest paper jar for 1.14.x");
                    if (jar_path.exists()){

                        jar_path.delete();
                    }
                    URL url = new URL("https://papermc.io/api/v1/paper/1.14.4/latest/download");
                    FileUtils.copyURLToFile(url, jar_path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if (version.equals("1.13.x")){
                try {
                    logger.global_info(" Downloading latest paper jar for 1.13.x");
                    if (jar_path.exists()){

                        jar_path.delete();
                    }
                    URL url = new URL("https://papermc.io/api/v1/paper/1.13.2/latest/download");
                    FileUtils.copyURLToFile(url, jar_path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if (version.equals("1.12.x")){
                try {
                    logger.global_info(" Downloading latest paper jar for 1.12.x");
                    if (jar_path.exists()){

                        jar_path.delete();
                    }
                    URL url = new URL("https://papermc.io/api/v1/paper/1.12.2/latest/download");
                    FileUtils.copyURLToFile(url, jar_path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if (version.equals("1.8.x")){
                try {
                    logger.global_info(" Downloading latest paper jar for 1.8.x");
                    if (jar_path.exists()){

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
            if (version.equals("1.15.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.14.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.13.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.12.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.8.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else {
                logger.global_warn("This server-version is NOT supported: " + version);
                logger.global_warn("Please check the config file for supported server versions!");
            }

        }
        else if (server.equals("CRAFTBUKKIT")) {

            logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            if (version.equals("1.15.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.14.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.13.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.12.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.8.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else {
                logger.global_warn("This server-version is NOT supported: " + version);
                logger.global_warn("Please check the config file for supported server versions!");
            }

        }

        else if (server.equals("VANILLA")) {

            logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            if (version.equals("1.15.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.14.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.13.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.12.x")){
                logger.global_warn("This server-software is currently not supported("+server+version+"), we are working on making it poosible! ");
            }
            else if (version.equals("1.8.x")){
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
