/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.managers.DownloadManager;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;

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

import static java.lang.Thread.sleep;

@Deprecated
public class OnlineTaskUpdatePlugins {

    private int updateCounter = 0;

    public OnlineTaskUpdatePlugins(Socket online_socket, DataInputStream online_dis, DataOutputStream online_dos,
                                   Socket local_socket, DataInputStream local_dis, DataOutputStream local_dos,
                                   String[] pl_names, String[] pl_authors, String[] pl_versions,
                                   int amount){


        Thread newThread = new Thread(() -> {

            try {

                updateCounter = 0;
                List<String> updates_pl_names = new ArrayList<>();
                List<String> updates_current_cache = new ArrayList<>();
                List<String> updates_future_loc = new ArrayList<>();

                //Sending request string
                AL.info("Sending request exec_update...");
                online_dos.writeUTF("exec_update");


                //RECEIVE 3: get response
                if (online_dis.readUTF().equals("true")) {
                    AL.info("All systems online!");
                    AL.info("Starting check...");

                    //SEND 4: send the size of for-loop
                    if (amount==0)
                    {
                        AL.warn("No plugins to update! Closing connections!");
                        online_socket.close();
                        local_socket.close();
                    }
                    online_dos.writeInt(amount);


                    //RECEIVE 7: ready to start for loop?
                    if (online_dis.readUTF().equals("true")) {

                        for (int i = 0; i < amount; i++) {

                            //SEND 8: Send Plugin Name
                            online_dos.writeUTF(pl_names[i]);
                            int logical_num = i + 1;
                            AL.info(" - Checking [" + pl_names[i] +"]["+logical_num+"/"+amount+"] for updates...");

                            //SEND 9: Send Plugin Author
                            online_dos.writeUTF(pl_authors[i]);
                            AL.info(" - Author: " + pl_authors[i]);

                            //SEND 10: Send Plugin Version
                            online_dos.writeUTF(pl_versions[i]);
                            AL.info(" - Version: " + pl_versions[i]);

                            //RECEIVE 15: get final download-link/response
                            String response = online_dis.readUTF();

                            //RECEIVE 16: receive latest version
                            String latest_version = online_dis.readUTF();

                            switch (response) {
                                case "query_returns_array_no_update":

                                    //SEND 17: send final reponse
                                    online_dos.writeUTF("true");

                                    AL.info(" - Result: Already on latest version!");
                                    AL.info(" ");
                                    break;
                                case "query_returns_array_no_author":

                                    //SEND 17: send final reponse
                                    online_dos.writeUTF("true");

                                    AL.info(" - Result: Couldn't find this Author on Spigot!");
                                    AL.info(" - Info: The authors name shouldn't diverge too much from its Spigot name.");
                                    AL.info(" ");
                                    break;
                                case "query_returns_object":
                                    //SEND 17: send final reponse
                                    online_dos.writeUTF("true");

                                    AL.warn(" - Result: Returned Json-Object and not array?!");
                                    AL.warn(" - Info: VERY rare error! Please notify us!");
                                    AL.info(" ");
                                    break;
                                case "query_no_result":
                                    //SEND 17: send final reponse
                                    online_dos.writeUTF("true");

                                    AL.warn(" - Result: Couldn't find this Plugin on Spigot!");
                                    AL.warn(" - Info: This often happens for older Bukkit Plugins.");
                                    AL.info(" ");
                                    break;
                                case "query_error":
                                    //SEND 17: send final reponse
                                    online_dos.writeUTF("true");

                                    AL.warn(" - Result: Server-search error! No result!");
                                    AL.warn(" - Info: Abnormal result pls notify us -> https://discord.gg/GGNmtCC ");
                                    AL.warn(" ");
                                    break;
                                default:

                                    AL.info(" - Result: Update available! [" + pl_versions[i] + "] -> [" + latest_version + "]");
                                    AL.info(" - Info: Downloading from Spigot: " + response);
                                    //Download new version and write to download cache
                                    DownloadManager downloadMan = new DownloadManager();
                                    File download_path = new File(GD.WORKING_DIR + "/autoplug-downloads" + "/" + pl_names[i] + "[" + latest_version + "]-AUTOPLUGGED-LATEST.jar");
                                    File future_path = new File(GD.PLUGINS_DIR + "/" + pl_names[i] + "[" + latest_version + "]-AUTOPLUGGED-LATEST.jar");

                                    if (downloadMan.downloadJar(response, download_path)) {

                                        //SEND 17: send final reponse
                                        online_dos.writeUTF("true");

                                        AL.info(" - Info: Downloaded update to cache successfully!");
                                        AL.info(" ");

                                        updateCounter++;

                                        if (new UpdaterConfig().plugin_updater_profile.asString().equals("AUTOMATIC")) {
                                            //Adds the plugins details to the updates list if profile automatic is selected
                                            updates_pl_names.add(pl_names[i]);
                                            updates_current_cache.add(download_path.toPath().toString());
                                            updates_future_loc.add(future_path.toPath().toString());
                                        }

                                    } else {

                                        //SEND 17: send final reponse
                                        online_dos.writeUTF("false");

                                        AL.warn(" [!] Failed to download jar to cache [!]");
                                        AL.info(" ");

                                    }

                                    break;
                            }

                        }

                        AL.info("     ___       __       ___  __");
                        AL.info("    / _ |__ __/ /____  / _ \\/ /_ _____ _");
                        AL.info("   / __ / // / __/ _ \\/ ___/ / // / _ `/");
                        AL.info("  /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /");
                        AL.info("                                 /___/");
                        AL.info("                _/Result\\_               ");
                        AL.info("");
                        AL.info("Plugins checked total -> "+ amount);
                        AL.info("Plugins to update total -> " + updateCounter);

                        if (updateCounter >0){

                            if (new UpdaterConfig().plugin_updater_profile.asString().equals("MANUAL")) {
                                AL.info("[MANUAL] Plugins downloaded to cache, please update them by yourself or change your profile to AUTOMATIC");
                                local_dos.writeInt(0);
                            }
                            else{
                                AL.info("[AUTOMATIC] Restarting server and enabling downloaded plugins...");
                                local_dos.writeInt(1);

                                //Waiting for server to get closed
                                while(Server.isRunning()){
                                    sleep(3000);
                                    AL.info("Waiting for server to shutdown...");
                                }
                                //Server is now stopped, so we can transfer the plugins
                                AL.info("Server closed! Transferring plugins...");

                                for (int a = 0; a < updates_pl_names.size(); a++) {

                                    AL.info(" Installing " + updates_pl_names.get(a) + "...");
                                    FileManager fileManager = new FileManager();
                                    fileManager.deleteOldPlugin(updates_pl_names.get(a));

                                    Path old_path = Paths.get(updates_current_cache.get(a));
                                    Path new_path = Paths.get(updates_future_loc.get(a));
                                    Files.copy(old_path, new_path);

                                    AL.info("Success!");

                                }

                                AL.info("Finished updates!");
                                sleep(3000);
                                AL.info("Restarting server...");
                                sleep(3000);
                                Server.start();


                            }


                        } else{
                            local_dos.writeInt(0);
                            AL.info("|All plugins are up-to-date!");
                            //Close connections
                            if (!online_socket.isClosed()){
                                online_socket.close();
                            }
                            if (!local_socket.isClosed()){
                                local_socket.close();
                            }


                        }


                    }

                }
                else {
                    AL.warn(" [!] Authentication failed! Please check your config and be sure, that your server_key matches the key on our website [!] " + GD.OFFICIAL_WEBSITE);
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
                AL.warn(" [!] Exception caught: "+ex.getMessage()+" (Connection closed) [!] ");
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
