/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.online;

import com.google.common.base.Stopwatch;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.server.Server;
import com.osiris.autoplug.client.utils.Config;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.managers.DownloadManager;
import com.osiris.autoplug.client.utils.AutoPlugLogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

/**
 * Waits for incoming connection
 */
public class Communication {

    private AutoPlugLogger logger = new AutoPlugLogger();

    private String server_key;
    private int UpdateCounter = 0;

    private Socket online_connection;
    private DataInputStream online_dis;
    private DataOutputStream online_dos;

    private ServerSocket local_server_socket = null;
    private Socket local_connection = null;
    private DataInputStream local_dis = null;
    private DataOutputStream local_dos = null;

    private String[] pl_names = null;
    private String[] pl_authors = null;
    private String[] pl_versions = null;
    private int amount = 0;

    private List<String> updates_pl_names ;
    private List<String> updates_current_cache;
    private List<String> updates_future_loc;

    public Communication(String server_key){
        this.server_key = server_key;
        listen();
    }

    private void listen(){

        logger.global_debugger("Communication","listen"," Initialising listener on 35556...");
        try {
            local_server_socket = new ServerSocket(35556);
            logger.global_debugger("Communication","listen"," Success!");
        } catch (IOException e) {
            e.printStackTrace();
            logger.global_warn(" [!] Failed to bind listener on port 35556! Already in use? [!]");
        }

        while(true){

            //New connection -> reset old ones
            online_connection = null;
            online_dis = null;
            online_dos = null;

            local_connection = null;
            local_dis = null;
            local_dos = null;

            pl_names = null;
            pl_authors = null;
            pl_versions = null;
            amount = 0;

            try {
                Server.start();
                logger.global_info("Waiting for plugin to start up...");
                local_connection = local_server_socket.accept();
                logger.global_info("AutoPlugPlugin connected!");

                local_dis = new DataInputStream(local_connection.getInputStream());
                local_dos = new DataOutputStream(local_connection.getOutputStream());

                receivePlugins();
                connectOnline();
                checkForUpdates();

            } catch (IOException e) {
                e.printStackTrace();
                logger.global_warn(" [!] AutoPlugPlugin connection error [!]");
            }
        }

    }

    private void receivePlugins(){

        try{
            //Reset data before starting new check
            pl_names = null;
            pl_authors = null;
            pl_versions = null;

            logger.global_info("Waiting for plugins...");
            amount = local_dis.readInt();

            pl_names = new String[amount];
            pl_authors = new String[amount];
            pl_versions = new String[amount];

            for (int i = 0; i < amount; i++) {

                pl_names[i] = local_dis.readUTF();
                pl_authors[i] = local_dis.readUTF();
                pl_versions[i] = local_dis.readUTF();

            }
            logger.global_info("Received "+ amount +" plugins!");

        } catch (IOException e) {
            e.printStackTrace();
            logger.global_warn(" [!] Error receiving plugin information [!]");
        }

    }

    private void connectOnline(){

        try {
            logger.global_info("Connecting to online server...");
            online_connection = new Socket("144.91.78.158",35555);
            online_dis = new DataInputStream(online_connection.getInputStream());
            online_dos = new DataOutputStream(online_connection.getOutputStream());
            logger.global_info("AutoPlug-Server is online!");
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.global_warn(" [!] Error connecting to the Online-Server at "+ GD.OFFICIAL_WEBSITE+" [!]");
            logger.global_warn(" [!] In most cases we are just performing updates and the website is up again after 2min [!]");
            logger.global_warn(" [!] So please wait and try again later. If you still get this error, notify our Team [!]");
        }

    }

    private void checkForUpdates(){

        try {
            Stopwatch timer = Stopwatch.createStarted();

            UpdateCounter = 0;
            updates_pl_names = new ArrayList<>();
            updates_current_cache = new ArrayList<>();
            updates_future_loc = new ArrayList<>();

            //SEND 0: Send Server-Key for Auth
            logger.global_info("Authenticating users server_key...");
            online_dos.writeUTF(server_key);

            //Sending request string
            logger.global_info("Sending request exec_update...");
            online_dos.writeUTF("exec_update");


            //RECEIVE 3: get response
            if (online_dis.readUTF().equals("true")) {
                logger.global_info("Connection successful!");
                logger.global_info("Starting check...");

                //SEND 4: send the size of for-loop
                if (amount==0)
                {
                    logger.global_warn("No plugins to update! Closing connections!");
                    online_connection.close();
                    local_connection.close();
                }
                online_dos.writeInt(amount);


                //RECEIVE 7: ready to start for loop?
                if (online_dis.readUTF().equals("true")) {

                    for (int i = 0; i < amount; i++) {


                        logger.global_info("|----------------------------------------|");
                        logger.global_info(" ");

                        //SEND 8: Send Plugin Name
                        online_dos.writeUTF(pl_names[i]);
                        int logical_num = i + 1;
                        logger.global_info(" - Checking [" + pl_names[i] +"]["+logical_num+"/"+amount+"] for updates...");

                        //SEND 9: Send Plugin Author
                        online_dos.writeUTF(pl_authors[i]);
                        logger.global_info(" - Author: " + pl_authors[i]);

                        //SEND 10: Send Plugin Version
                        online_dos.writeUTF(pl_versions[i]);
                        logger.global_info(" - Version: " + pl_versions[i]);

                        //RECEIVE 15: get final download-link/response
                        String response = online_dis.readUTF();

                        //RECEIVE 16: receive latest version
                        String latest_version = online_dis.readUTF();

                        switch (response) {
                            case "query_returns_array_no_update":
                                noUpdate();
                                break;
                            case "query_returns_array_no_author":
                                noAuthor();
                                break;
                            case "query_returns_object":
                                noObject();
                                break;
                            case "query_no_result":
                                noResult();
                                break;
                            case "query_error":
                                error();
                                break;
                            default:

                                logger.global_info(" - Result: Update available! [" + pl_versions[i] + "] -> [" + latest_version + "]");
                                logger.global_info(" - Info: Downloading from Spigot: " + response);
                                downloadUpdate(response, pl_names[i], latest_version);

                                break;
                        }

                    }


                    timer.stop();
                    String time_result;
                    if (timer.elapsed(TimeUnit.SECONDS)>90){
                        time_result = ""+timer.elapsed(TimeUnit.MINUTES)+" min.";
                    } else{
                        time_result = ""+timer.elapsed(TimeUnit.SECONDS)+" sec.";
                    }

                    logger.global_info("|----------------------------------------|");
                    logger.global_info("     ___       __       ___  __");
                    logger.global_info("    / _ |__ __/ /____  / _ \\/ /_ _____ _");
                    logger.global_info("   / __ / // / __/ _ \\/ ___/ / // / _ `/");
                    logger.global_info("  /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /");
                    logger.global_info("                                 /___/");
                    logger.global_info("                _/Result\\_               ");
                    logger.global_info("|----------------------------------------|");
                    logger.global_info("|Elapsed time -> " + time_result);
                    logger.global_info("|Plugins checked total -> "+ amount);
                    logger.global_info("|Plugins to update total -> " + UpdateCounter);


                    if (UpdateCounter>0){

                        try{
                            if (Config.profile.equals("MANUAL")) {
                                logger.global_info("|[MANUAL] Plugins downloaded to cache, please update them by yourself...");
                                local_dos.writeInt(0);
                            }
                            else{
                                logger.global_info("|[AUTOMATIC] Restarting server and enabling downloaded plugins...");
                                local_dos.writeInt(1);

                                //Waiting for server to get closed
                                while(Server.isRunning()){
                                    sleep(3000);
                                    logger.global_info(" Waiting for server to shutdown...");
                                }
                                //Server is now stopped, so we can transfer the plugins
                                logger.global_info("Server closed! Transferring plugins...");

                                for (int a = 0; a < updates_pl_names.size(); a++) {

                                    logger.global_info(" Installing " + updates_pl_names.get(a) + "...");
                                    FileManager fileManager = new FileManager();
                                    fileManager.deleteOldPlugin(updates_pl_names.get(a));

                                    Path old_path = Paths.get(updates_current_cache.get(a));
                                    Path new_path = Paths.get(updates_future_loc.get(a));
                                    Files.copy(old_path, new_path);

                                    logger.global_info(" Success!");

                                }

                                logger.global_info(" Finished updates!");
                                sleep(3000);
                                logger.global_info(" Restarting server...");
                                sleep(3000);
                                Server.start();


                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }


                    } else{
                        local_dos.writeInt(0);
                        logger.global_info("|All plugins are up-to-date!");
                        //Close connections
                        if (!online_connection.isClosed()){
                            online_connection.close();
                        }
                        if (!local_connection.isClosed()){
                            local_connection.close();
                        }


                    }
                    logger.global_info("|----------------------------------------|");
                    logger.global_info(" ");
                }

            }
            else {
                logger.global_warn(" [!] Authentication failed! Please check your config and be sure, that your server_key matches the key on our website [!] " + GD.OFFICIAL_WEBSITE);
                //Close connections
                if (!online_connection.isClosed()){
                    online_connection.close();
                }
                if (!local_connection.isClosed()){
                    local_connection.close();
                }
            }

            //Close connections
            if (!online_connection.isClosed()){
                online_connection.close();
            }
            if (!local_connection.isClosed()){
                local_connection.close();
            }

        }
        catch (Exception ex) {
            ex.printStackTrace();
            logger.global_warn(" [!] Exception caught: "+ex.getMessage()+" (Connection closed) [!] ");
            try {
                //Close connections
                //Close connections
                if (!online_connection.isClosed()){
                    online_connection.close();
                }
                if (!local_connection.isClosed()){
                    local_connection.close();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
    }

    private void error() {
        try {
            logger.global_warn(" - Result: Server error! No result!");
            logger.global_warn(" - Info: Abnormal result pls notify us -> https://discord.gg/GGNmtCC ");
            logger.global_warn(" ");
            //SEND 17: send final reponse
            online_dos.writeUTF("false");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void downloadUpdate(String url, String pl_name, String latest_version) throws IOException {


            //Download new version and write to download cache
            DownloadManager downloadManager = new DownloadManager();
            File cache_path = new File(GD.WORKING_DIR + "/autoplug-cache" + "/" + pl_name + "[" + latest_version + "]-AUTOPLUGGED-LATEST.jar");
            File future_path = new File(GD.PLUGINS_DIR + "/" + pl_name + "[" + latest_version + "]-AUTOPLUGGED-LATEST.jar");


            if (downloadManager.downloadJar(url, cache_path, latest_version)) {

                logger.global_info(" - Info: Downloaded update to cache successfully!");
                logger.global_info(" ");

                UpdateCounter++;

                try {
                    if (Config.profile.equals("AUTOMATIC")) {
                        //Adds the plugins details to the updates list if profile automatic is selected
                        updates_pl_names.add(pl_name);
                        updates_current_cache.add(cache_path.toPath().toString());
                        updates_future_loc.add(future_path.toPath().toString());

                        //SEND 17: send final reponse
                        online_dos.writeUTF("true");

                    } else{
                        //SEND 17: send final reponse
                        online_dos.writeUTF("true");
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    //SEND 17: send final reponse
                    online_dos.writeUTF("true");
                }


            } else {

                logger.global_warn(" [!] Failed to download jar to cache [!]");
                logger.global_info(" ");
                //SEND 17: send final reponse
                online_dos.writeUTF("false");

            }
    }


    private void noResult() throws IOException {
        logger.global_warn(" - Result: Couldn't find this Plugin on Spigot!");
        logger.global_warn(" - Info: This often happens for older Bukkit Plugins.");
        logger.global_info(" ");

        //SEND 17: send final reponse
        online_dos.writeUTF("true");
    }

    private void noObject() throws IOException {
        logger.global_warn(" - Result: Returned Json-Object and not array?!");
        logger.global_warn(" - Info: VERY rare error! Please notify us!");
        logger.global_info(" ");

        //SEND 17: send final reponse
        online_dos.writeUTF("true");
    }

    private void noUpdate() throws IOException {

        logger.global_info(" - Result: Already on latest version!");
        logger.global_info(" ");

        //SEND 17: send final reponse
        online_dos.writeUTF("true");

    }

    private void noAuthor() throws IOException {

        logger.global_info(" - Result: Couldn't find this Author on Spigot!");
        logger.global_info(" - Info: The authors name shouldn't diverge too much from its Spigot name.");
        logger.global_info(" ");

        //SEND 17: send final reponse
        online_dos.writeUTF("true");

    }
    


}
