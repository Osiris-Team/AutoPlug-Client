package com.osiris.autoplug.client.tasks.updater.self;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.client.tasks.updater.server.TaskServerDownload;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.JsonTools;
import com.osiris.autoplug.client.utils.UtilsJar;
import com.osiris.autoplug.client.utils.UtilsVersion;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;

public class TaskSelfUpdater extends BetterThread {
    private final UpdaterConfig updaterConfig = new UpdaterConfig();

    // These URLS are not allowed to change:
    private final String stableUpdateUrl = "https://raw.githubusercontent.com/Osiris-Team/AutoPlug-Releases/master/stable-builds/update.json";
    private final String betaUpdateUrl = "https://raw.githubusercontent.com/Osiris-Team/AutoPlug-Releases/master/beta-builds/update.json";

    public TaskSelfUpdater(BetterThreadManager manager) {
        super(manager);
    }

    public TaskSelfUpdater(String name, BetterThreadManager manager) {
        super(name, manager);
    }

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();

        if (!updaterConfig.self_updater.asBoolean()) {
            skip();
            return;
        }
        if (Server.isRunning()) throw new Exception("Cannot perform self update while server is running!");

        if (updaterConfig.self_updater_build.asString().equals("stable"))
            doUpdating(stableUpdateUrl);
        else
            doUpdating(betaUpdateUrl);
    }

    private void doUpdating(String url) throws Exception{
        // This url contains a JsonArray with JsonObjects, each representing a java application.
        // In this case we are only interested in the AutoPlug-Client.jar with id 0.
        List<JsonObject> apps = new JsonTools().getJsonArrayAsList(url);
        JsonObject latestJar = null;
        for (JsonObject o :
                apps) {
            if(o.get("id").getAsInt()==0){
                latestJar = o;
                break;
            }
        }

        if (latestJar==null)
            throw new Exception("Failed to find a JsonObject with id=0! Url: "+url);

        // Get latest jars details
        int id                = latestJar.get("id")      .getAsInt();
        File installationFile = convertIntoActualFile(latestJar.get("installation-path").getAsString());
        String version        = latestJar.get("version") .getAsString();
        String downloadUrl    = latestJar.get("download-url")     .getAsString();
        String sha256         = latestJar.get("sha-256").getAsString();
        long size             = latestJar.get("file-size")    .getAsLong();
        String launchClass    = latestJar.get("main-class")   .getAsString();

        // Get current jars details
        Properties currentJar = new UtilsJar().getThisJarsAutoPlugProperties();
        int currentId = Integer.parseInt(currentJar.getProperty("id"));
        String currentInstallationPath = currentJar.getProperty("installation-path");
        String currentVersion = currentJar.getProperty("version");

        // Just to be sure we check if the ids match. Both should be 0.
        if (id!=0 || currentId!=0)
            throw new Exception("The update jars and the current jars ids don't match!");

        // Now we are good to go! Start the download!
        // Check if the latest version is bigger than our current one.
        if (new UtilsVersion().compare(currentVersion, version)){
            String profile = updaterConfig.self_updater_profile.asString();
            if (profile.equals("NOTIFY")){
                setStatus("NOTIFY: Update found ("+currentVersion+" -> "+version+")!");
            }
            else if (profile.equals("MANUAL")){
                setStatus("MANUAL: Update found ("+currentVersion+" -> "+version+"), started download!");

                // Download the file
                File cache_dest = new File(GD.WORKING_DIR+"/autoplug-downloads/"+installationFile.getName());
                if (cache_dest.exists()) cache_dest.delete();
                cache_dest.createNewFile();
                TaskServerDownload download = new TaskServerDownload("Downloader", getManager(), downloadUrl , cache_dest);
                download.start();

                while(true){
                    Thread.sleep(500); // Wait until download is finished
                    if (download.isFinished()){
                        if (download.isSuccess()){
                            setStatus("AutoPlug update downloaded. Checking checksum...");
                            if (download.compareWithSHA256(sha256)){
                                // Create the actual update copy file, by simply copying the newly downloaded file.
                                Files.copy(cache_dest.toPath(), new File(GD.WORKING_DIR+"/autoplug-downloads/AutoPlug-Client-Copy.jar").toPath(), StandardCopyOption.REPLACE_EXISTING);
                                setStatus("AutoPlug update downloaded successfully.");
                                setSuccess(true);
                            }
                            else{
                                setStatus("Downloaded AutoPlug update is broken. Nothing changed!");
                                setSuccess(false);
                            }

                        }
                        else{
                            setStatus("AutoPlug update failed!");
                            setSuccess(false);
                        }
                        break;
                    }
                }
            }
            else {
                setStatus("AUTOMATIC: Update found ("+currentVersion+" -> "+version+"), started download!");
                if (installationFile.exists()) installationFile.delete();
                installationFile.createNewFile();

                // Download the file
                File cache_dest = new File(GD.WORKING_DIR+"/autoplug-downloads/"+installationFile.getName());
                if (cache_dest.exists()) cache_dest.delete();
                cache_dest.createNewFile();
                TaskServerDownload download = new TaskServerDownload("Downloader", getManager(), downloadUrl , cache_dest);
                download.start();

                while(true){
                    Thread.sleep(500);
                    if (download.isFinished()){
                        if (download.isSuccess()){
                            setStatus("AutoPlug update downloaded. Checking hash...");
                            if (download.compareWithSHA256(sha256)){
                                setStatus("Installing AutoPlug update ("+currentVersion+" -> "+version+")...");
                                // Start that updated old jar and close this one
                                Main.startJar(cache_dest.getAbsolutePath());
                                System.exit(0);
                                setSuccess(true);
                            }
                            else{
                                setStatus("Downloaded AutoPlug update is broken. Nothing changed!");
                                setSuccess(false);
                            }

                        }
                        else{
                            setStatus("AutoPlug update failed!");
                            setSuccess(false);
                        }
                        break;
                    }
                }
            }

        }
        else{
            setStatus("AutoPlug is on the latest version!");
            setSuccess(true);
        }


    }

    /**
     * Example input: ./AutoPlug-Client.jar <br>
     * Output: (complete-path)/AutoPlug-Client.jar
     * @param shortPath like example input above.
     * @return
     */
    private File convertIntoActualFile(String shortPath) {
        return new File(shortPath.replace("./", GD.WORKING_DIR+"/"));
    }

}


