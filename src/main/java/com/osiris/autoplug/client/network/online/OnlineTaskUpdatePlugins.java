/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.configs.CheckConfig;
import com.osiris.autoplug.client.managers.DownloadManager;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.server.Server;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.utils.GD;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.osiris.autoplug.client.configs.ServerConfig.server_key;
import static java.lang.Thread.sleep;

public class OnlineTaskUpdatePlugins {

    private int UpdateCounter = 0;

    public OnlineTaskUpdatePlugins(Socket online_socket, DataInputStream online_dis, DataOutputStream online_dos,
                                   Socket local_socket, DataInputStream local_dis, DataOutputStream local_dos,
                                   String[] pl_names, String[] pl_authors, String[] pl_versions,
                                   int amount){


        Thread newThread = new Thread(() -> {

            try {

                UpdateCounter = 0;
                List<String> updates_pl_names = new ArrayList<>();
                List<String> updates_current_cache = new ArrayList<>();
                List<String> updates_future_loc = new ArrayList<>();

                //SEND 0: Send Server-Key for Auth
                AutoPlugLogger.info("Authenticating users server_key...");
                online_dos.writeUTF(server_key);

                //Sending request string
                AutoPlugLogger.info("Sending request exec_update...");
                online_dos.writeUTF("exec_update");


                //RECEIVE 3: get response
                if (online_dis.readUTF().equals("true")) {
                    AutoPlugLogger.info("All systems online!");
                    AutoPlugLogger.info("Starting check...");

                    //SEND 4: send the size of for-loop
                    if (amount==0)
                    {
                        AutoPlugLogger.warn("No plugins to update! Closing connections!");
                        online_socket.close();
                        local_socket.close();
                    }
                    online_dos.writeInt(amount);


                    //RECEIVE 7: ready to start for loop?
                    if (online_dis.readUTF().equals("true")) {

                        for (int i = 0; i < amount; i++) {


                            AutoPlugLogger.barrier();

                            //SEND 8: Send Plugin Name
                            online_dos.writeUTF(pl_names[i]);
                            int logical_num = i + 1;
                            AutoPlugLogger.info(" - Checking [" + pl_names[i] +"]["+logical_num+"/"+amount+"] for updates...");

                            //SEND 9: Send Plugin Author
                            online_dos.writeUTF(pl_authors[i]);
                            AutoPlugLogger.info(" - Author: " + pl_authors[i]);

                            //SEND 10: Send Plugin Version
                            online_dos.writeUTF(pl_versions[i]);
                            AutoPlugLogger.info(" - Version: " + pl_versions[i]);

                            //RECEIVE 15: get final download-link/response
                            String response = online_dis.readUTF();

                            //RECEIVE 16: receive latest version
                            String latest_version = online_dis.readUTF();

                            switch (response) {
                                case "query_returns_array_no_update":

                                    //SEND 17: send final reponse
                                    online_dos.writeUTF("true");

                                    AutoPlugLogger.info(" - Result: Already on latest version!");
                                    AutoPlugLogger.info(" ");
                                    break;
                                case "query_returns_array_no_author":

                                    //SEND 17: send final reponse
                                    online_dos.writeUTF("true");

                                    AutoPlugLogger.info(" - Result: Couldn't find this Author on Spigot!");
                                    AutoPlugLogger.info(" - Info: The authors name shouldn't diverge too much from its Spigot name.");
                                    AutoPlugLogger.info(" ");
                                    break;
                                case "query_returns_object":
                                    //SEND 17: send final reponse
                                    online_dos.writeUTF("true");

                                    AutoPlugLogger.warn(" - Result: Returned Json-Object and not array?!");
                                    AutoPlugLogger.warn(" - Info: VERY rare error! Please notify us!");
                                    AutoPlugLogger.info(" ");
                                    break;
                                case "query_no_result":
                                    //SEND 17: send final reponse
                                    online_dos.writeUTF("true");

                                    AutoPlugLogger.warn(" - Result: Couldn't find this Plugin on Spigot!");
                                    AutoPlugLogger.warn(" - Info: This often happens for older Bukkit Plugins.");
                                    AutoPlugLogger.info(" ");
                                    break;
                                case "query_error":
                                    //SEND 17: send final reponse
                                    online_dos.writeUTF("true");

                                    AutoPlugLogger.warn(" - Result: Server-search error! No result!");
                                    AutoPlugLogger.warn(" - Info: Abnormal result pls notify us -> https://discord.gg/GGNmtCC ");
                                    AutoPlugLogger.warn(" ");
                                    break;
                                default:

                                    AutoPlugLogger.info(" - Result: Update available! [" + pl_versions[i] + "] -> [" + latest_version + "]");
                                    AutoPlugLogger.info(" - Info: Downloading from Spigot: " + response);
                                    //Download new version and write to download cache
                                    DownloadManager downloadManager = new DownloadManager();
                                    File download_path = new File(GD.WORKING_DIR + "/autoplug-cache" + "/" + pl_names[i] + "[" + latest_version + "]-AUTOPLUGGED-LATEST.jar");
                                    File future_path = new File(GD.PLUGINS_DIR + "/" + pl_names[i] + "[" + latest_version + "]-AUTOPLUGGED-LATEST.jar");

                                    if (downloadManager.downloadJar(response, download_path)) {

                                        //SEND 17: send final reponse
                                        online_dos.writeUTF("true");

                                        AutoPlugLogger.info(" - Info: Downloaded update to cache successfully!");
                                        AutoPlugLogger.info(" ");

                                        UpdateCounter++;

                                        if (CheckConfig.profile.equals("AUTOMATIC")) {
                                            //Adds the plugins details to the updates list if profile automatic is selected
                                            updates_pl_names.add(pl_names[i]);
                                            updates_current_cache.add(download_path.toPath().toString());
                                            updates_future_loc.add(future_path.toPath().toString());
                                        }

                                    } else {

                                        //SEND 17: send final reponse
                                        online_dos.writeUTF("false");

                                        AutoPlugLogger.warn(" [!] Failed to download jar to cache [!]");
                                        AutoPlugLogger.info(" ");

                                    }

                                    break;
                            }

                        }

                        AutoPlugLogger.barrier();
                        AutoPlugLogger.info("     ___       __       ___  __");
                        AutoPlugLogger.info("    / _ |__ __/ /____  / _ \\/ /_ _____ _");
                        AutoPlugLogger.info("   / __ / // / __/ _ \\/ ___/ / // / _ `/");
                        AutoPlugLogger.info("  /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /");
                        AutoPlugLogger.info("                                 /___/");
                        AutoPlugLogger.info("                _/Result\\_               ");
                        AutoPlugLogger.info("");
                        AutoPlugLogger.info("Plugins checked total -> "+ amount);
                        AutoPlugLogger.info("Plugins to update total -> " + UpdateCounter);
                        AutoPlugLogger.barrier();

                        if (UpdateCounter>0){

                            if (CheckConfig.profile.equals("MANUAL")) {
                                AutoPlugLogger.info("[MANUAL] Plugins downloaded to cache, please update them by yourself or change your profile to AUTOMATIC");
                                local_dos.writeInt(0);
                            }
                            else{
                                AutoPlugLogger.info("[AUTOMATIC] Restarting server and enabling downloaded plugins...");
                                local_dos.writeInt(1);

                                //Waiting for server to get closed
                                while(Server.isRunning()){
                                    sleep(3000);
                                    AutoPlugLogger.info("Waiting for server to shutdown...");
                                }
                                //Server is now stopped, so we can transfer the plugins
                                AutoPlugLogger.info("Server closed! Transferring plugins...");

                                for (int a = 0; a < updates_pl_names.size(); a++) {

                                    AutoPlugLogger.info(" Installing " + updates_pl_names.get(a) + "...");
                                    FileManager fileManager = new FileManager();
                                    fileManager.deleteOldPlugin(updates_pl_names.get(a));

                                    Path old_path = Paths.get(updates_current_cache.get(a));
                                    Path new_path = Paths.get(updates_future_loc.get(a));
                                    Files.copy(old_path, new_path);

                                    AutoPlugLogger.info("Success!");

                                }

                                AutoPlugLogger.info("Finished updates!");
                                sleep(3000);
                                AutoPlugLogger.info("Restarting server...");
                                sleep(3000);
                                Server.start();


                            }


                        } else{
                            local_dos.writeInt(0);
                            AutoPlugLogger.info("|All plugins are up-to-date!");
                            //Close connections
                            if (!online_socket.isClosed()){
                                online_socket.close();
                            }
                            if (!local_socket.isClosed()){
                                local_socket.close();
                            }


                        }
                        AutoPlugLogger.barrier();
                    }

                }
                else {
                    AutoPlugLogger.warn(" [!] Authentication failed! Please check your config and be sure, that your server_key matches the key on our website [!] " + GD.OFFICIAL_WEBSITE);
                    //Close connections
                    if (!online_socket.isClosed()){
                        online_socket.close();
                    }
                    if (!local_socket.isClosed()){
                        local_socket.close();
                    }
                }

                //Close connections
                if (!online_socket.isClosed()){
                    online_socket.close();
                }
                if (!local_socket.isClosed()){
                    local_socket.close();
                }

            }
            catch (Exception ex) {
                ex.printStackTrace();
                AutoPlugLogger.warn(" [!] Exception caught: "+ex.getMessage()+" (Connection closed) [!] ");
                try {
                    //Close connections
                    //Close connections
                    if (!online_socket.isClosed()){
                        online_socket.close();
                    }
                    if (!local_socket.isClosed()){
                        local_socket.close();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        });
        newThread.start();

    }


}
