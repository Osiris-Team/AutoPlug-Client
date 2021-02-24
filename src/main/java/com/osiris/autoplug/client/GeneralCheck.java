package com.osiris.autoplug.client;

import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.client.scheduler.TaskScheduler;
import com.osiris.autoplug.core.logger.AL;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

public class GeneralCheck {

    public void checkFilePermission() throws Exception{
        try{
            File test = new File(System.getProperty("user.dir")+"/read-write-test.txt");
            if (!test.exists()){
                test.createNewFile();
            }
            test.delete();
        } catch (Exception e) {
            System.err.println("Make sure that this jar has read/write permissions!");
            throw e;
        }
    }

    public void checkInternetAccess() throws Exception{
        try{
            HttpURLConnection connection = (HttpURLConnection) new URL("https://www.google.com").openConnection();
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("Failed to get code 200 from "+connection.getURL().toString());
            }
        } catch (Exception e) {
            System.err.println("Make sure that you have an internet connection!");
            throw e;
        }
    }

    /**
     * This enables AutoPlug to securely
     * shutdown and closes all open things.
     */
    public void addShutDownHook(){
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (Server.isRunning()) Server.stop();
            while(Server.isRunning()){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                }
            }
            TaskScheduler.safeShutdown();
            AL.info("See you soon!");
            new AL().stop();
        }, "Shutdown-Thread"));
    }
}
