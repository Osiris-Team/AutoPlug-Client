/*
 *  Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client;



import com.osiris.autoplug.client.mcserver.MCServer;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.utils.DeleteOldPluginsJar;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClientListener extends Thread {

    /** -> CLIENT LISTENER
     * Once started up, listens for server stops/starts and crashes
     * After server stop we move
     */
    AutoPlugLogger logger = new AutoPlugLogger();
    MCServer mcServer = new MCServer();



    public ClientListener() {

    }

    @Override
    public void run() {
        super.run();

        try{
            //Startup mc-server
            logger.global_info(" All systems ready! Starting up server...");
            mcServer.startup();
        } catch (Exception e) {
            e.printStackTrace();
        }

        logger.global_debugger("ClientListener","run"," Initialising listener on 35556...");
        //Create one and only serversocket for following port:
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(35556);
            logger.global_debugger("ClientListener","run"," Success!");
        } catch (IOException e) {
            logger.global_warn(" Failed!");
            e.printStackTrace();
        }

        while (true) {
            Socket socket = null;

            try {
                logger.global_debugger("ClientListener","run"," Waiting for plugin connection...");
                socket = serverSocket.accept();
                logger.global_debugger("ClientListener","run"," AutoPlugPlugin Connected!");
                //socket.setSoTimeout(10000); //If we dont get a key in the next 10sek connection will be closed!

                // obtaining input and out streams
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

                //SEND1: Sending connection result
                dos.writeUTF("true");

                //GET For loop size (plugins)
                int size = dis.readInt();
                String[] current_cache_path = new String[size];
                String[] new_plugin_path = new String[size];
                String[] plugin_name = new String[size];
                logger.global_debugger("ClientListener","run"," Total Plugin size: " + size);
                for (int i = 0; i < size; i++) {

                    if (dis.readUTF().equals("true")){

                        logger.global_debugger("ClientListener","run"," Results for Plugin[" + i + "] :");

                        //GET: PATH Plugin current cache location
                        String cachedPathString = dis.readUTF();
                        logger.global_debugger("ClientListener","run"," GOT CURRENT CACHE: " + cachedPathString);
                        current_cache_path[i] = cachedPathString;

                        //GET: PATH future location
                        String new_pathString = dis.readUTF();
                        logger.global_debugger("ClientListener","run"," GOT FUTURE LOC.: " + new_pathString);
                        new_plugin_path[i] = new_pathString;

                        //GET: Plugin name
                        String old_plugin_name = dis.readUTF();
                        logger.global_debugger("ClientListener","run"," GOT RESULT NAME: " + old_plugin_name);
                        plugin_name[i] = old_plugin_name;

                    }
                    else{
                        //If we get false als response it means that plugin doesn't need further changes
                        current_cache_path[i] = "false";
                        new_plugin_path[i] = "false";
                        plugin_name[i] = "false";
                    }
                }

                //Getting final response if we need to take actions
                if (dis.readUTF().equals("true")){
                    sleep(1000);
                    logger.global_info(" There are updates available! Continuing with following profile:");

                    String profile = new Settings().getConfig_profile();
                    if (profile.equals("MANUAL")) {
                        dos.writeUTF("MANUAL");
                        logger.global_info(" MANUAL");
                        logger.global_info(" The plugins latest versions were downloaded to /plugins/autoplug-cache.");
                        logger.global_info(" You have to manually replace your old plugins with the new ones.");
                        logger.global_info(" If you want this process to be automatic, change profile to AUTOMATIC in autoplug-config.yml!");
                    }
                    else if(profile.equals("AUTOMATIC")) {
                        dos.writeUTF("AUTOMATIC");
                        logger.global_info(" AUTOMATIC");
                        logger.global_info(" Waiting for server to shutdown... 20...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 19...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 18...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 17...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 16...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 15...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 14...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 13...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 12...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 11...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 10...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 9...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 8...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 7...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 6...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 5...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 4...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 3...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 2...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 1...");
                        sleep(1000);
                        logger.global_info(" Waiting for server to shutdown... 0...");
                        sleep(1000);

                        logger.global_info(" Transferring plugin jars... ");

                        for (int i = 0; i < size; i++) {

                            if (!current_cache_path[i].equals("false")) {

                                logger.global_debugger("ClientListener","run"," Transfering files for " + plugin_name[i] + "...");
                                String plugins_dir = System.getProperty("user.dir") + "\\plugins";
                                Path pluginPath = Paths.get(plugins_dir);

                                //Delete old Plugin...
                                String crunchifyExtension = "*"+plugin_name[i]+"**.jar"; //
                                DeleteOldPluginsJar deleteOldPluginsJar = new DeleteOldPluginsJar(crunchifyExtension);
                                Files.walkFileTree(pluginPath, deleteOldPluginsJar);

                                //Copy from cache to plugins dir
                                Path old_path = Paths.get(current_cache_path[i]);
                                Path new_path = Paths.get(new_plugin_path[i]);
                                Files.copy(old_path, new_path);
                                logger.global_debugger("ClientListener","run"," Success!");
                            }

                        }

                        logger.global_info(" Done! All plugins were updated! ");
                        logger.global_info(" Restarting server...");

                        mcServer.destroy();
                        mcServer.startup();


                    } else {
                        logger.global_warn(" Error in config.yml! profile: " + profile+" must be MANUAL or AUTOMATIC and nothing else!");
                    }


                }
                else{
                    sleep(1250);
                    logger.global_info(" All plugins up-to-date! No need for further actions!");
                    socket.close();
                }


            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
