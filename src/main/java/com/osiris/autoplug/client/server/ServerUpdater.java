/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.server;

import com.osiris.autoplug.client.configs.CheckConfig;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.utils.GD;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class ServerUpdater {

    public ServerUpdater()  {
        AutoPlugLogger logger = new AutoPlugLogger();

        File server_jar = GD.SERVER_PATH;

        if (CheckConfig.server_check){
            logger.global_info(" Updating server-jar: "+ server_jar);
            String server = CheckConfig.server_software;
            String version = CheckConfig.server_version;

            try{

                if (server.equals("PAPER")) {

                    switch (version) {
                        case "1.15.x":
                            try {
                                logger.global_info(" Downloading latest paper jar for 1.15.x");
                                if (server_jar.exists()) {

                                    server_jar.delete();
                                }
                                URL url = new URL("https://papermc.io/api/v1/paper/1.15.2/latest/download");
                                logger.global_info(" Link: " + url);
                                FileUtils.copyURLToFile(url, server_jar);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case "1.14.x":
                            try {
                                logger.global_info(" Downloading latest paper jar for 1.14.x");
                                if (server_jar.exists()) {

                                    server_jar.delete();
                                }
                                URL url = new URL("https://papermc.io/api/v1/paper/1.14.4/latest/download");
                                logger.global_info(" Link: " + url);
                                FileUtils.copyURLToFile(url, server_jar);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case "1.13.x":
                            try {
                                logger.global_info(" Downloading latest paper jar for 1.13.x");
                                if (server_jar.exists()) {

                                    server_jar.delete();
                                }
                                URL url = new URL("https://papermc.io/api/v1/paper/1.13.2/latest/download");
                                logger.global_info(" Link: " + url);
                                FileUtils.copyURLToFile(url, server_jar);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case "1.12.x":
                            try {
                                logger.global_info(" Downloading latest paper jar for 1.12.x");
                                if (server_jar.exists()) {

                                    server_jar.delete();
                                }
                                URL url = new URL("https://papermc.io/api/v1/paper/1.12.2/latest/download");
                                logger.global_info(" Link: " + url);
                                FileUtils.copyURLToFile(url, server_jar);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case "1.8.x":
                            try {
                                logger.global_info(" Downloading latest paper jar for 1.8.x");
                                if (server_jar.exists()) {

                                    server_jar.delete();
                                }
                                URL url = new URL("https://papermc.io/api/v1/paper/1.8.8/latest/download");
                                logger.global_info(" Link: " + url);
                                FileUtils.copyURLToFile(url, server_jar);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        default:
                            logger.global_warn("This server-version is NOT supported: " + version);
                            logger.global_warn("Please check the config file for supported server versions!");
                            break;
                    }

                }
                else if (server.equals("SPIGOT")) {
                    logger.global_warn("This server-software is currently not supported("+server+version+"), we are working hard on making it possible! ");
                    switch (version) {
                        case "1.15.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        case "1.14.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        case "1.13.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        case "1.12.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        case "1.8.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        default:
                            logger.global_warn("This server-version is NOT supported: " + version);
                            logger.global_warn("Please check the config file for supported server versions!");
                            break;
                    }

                }
                else if (server.equals("CRAFTBUKKIT")) {

                    logger.global_warn("This server-software is currently not supported("+server+version+"), we are working hard on making it possible! ");
                    switch (version) {
                        case "1.15.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        case "1.14.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        case "1.13.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        case "1.12.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        case "1.8.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        default:
                            logger.global_warn("This server-version is NOT supported: " + version);
                            logger.global_warn("Please check the config file for supported server versions!");
                            break;
                    }

                }

                else if (server.equals("VANILLA")) {

                    logger.global_warn("This server-software is currently not supported("+server+version+"), we are working hard on making it possible! ");
                    switch (version) {
                        case "1.15.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        case "1.14.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        case "1.13.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        case "1.12.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        case "1.8.x":
                            logger.global_warn("This server-software is currently not supported(" + server + version + "), we are working hard on making it possible! ");
                            break;
                        default:
                            logger.global_warn("This server-version is NOT supported: " + version);
                            logger.global_warn("Please check the config file for supported server versions!");
                            break;
                    }

                }
                else{
                    logger.global_warn("This server-software is NOT supported: " + server);
                    logger.global_warn("Please check the config file for supported server software!");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }


        } else {
            logger.global_info(" Skipping server-check! ");
        }






    }

}
