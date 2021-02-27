package com.osiris.autoplug.client.tasks.updater;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.client.utils.JsonTools;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
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

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();

        if (!updaterConfig.self_updater.asBoolean()) {
            skip();
            return;
        }
        if (Server.isRunning()) throw new Exception("Cannot perform plugins update while server is running!");

        if (updaterConfig.self_updater_build.asString().equals("stable"))
            doUpdating(stableUpdateUrl);
        else
            doUpdating(betaUpdateUrl);
    }

    private void doUpdating(String url) throws Exception{
        // This url contains a JsonArray with JsonObjects, each representing a java application.
        // In this case we are only interested in AutoPlug with its id=0;
        List<JsonObject> apps = new JsonTools().getJsonArrayAsList(url);
        JsonObject autoplug = null;
        for (JsonObject o :
                apps) {
            if(o.get("id").getAsInt()==0){
                autoplug = o;
                break;
            }
        }

        if (autoplug==null)
            throw new Exception("Failed to find a JsonObject with id=0! Url: "+url);

        // Get details about the latest jar
        int id             = autoplug.get("id")      .getAsInt();
        String path        = autoplug.get("path")    .getAsString();
        double version     = autoplug.get("version") .getAsDouble();
        String downloadUrl = autoplug.get("url")     .getAsString();
        String checksum    = autoplug.get("checksum").getAsString();
        long size          = autoplug.get("size")    .getAsLong();
        String launchClass = autoplug.get("class")   .getAsString();



    }

    private Properties getAutoPlugPropertiesFromJar(String path) throws Exception {
        return getPropertiesFromJar(path, "autoplug");
    }

    /**
     * This creates an URLClassLoader so we can access the autoplug.properties file inside the jar and then returns the properties file.
     * @param path The jars path
     * @param propertiesFileName Properties file name without its .properties extension.
     * @return autoplug.properties
     * @throws Exception
     */
    private Properties getPropertiesFromJar(String path, String propertiesFileName) throws Exception{
        File file = new File(path); // The properties file
        if (file.exists()){
            Collection<URL> urls = new ArrayList<URL>();
            urls.add(file.toURI().toURL());
            URLClassLoader fileClassLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]));

            java.io.InputStream is = fileClassLoader.getResourceAsStream(propertiesFileName+".properties");
            java.util.Properties p = new java.util.Properties();
            p.load(is);
            return p;
        }
        else
            throw new Exception("Couldn't find the properties file at: " + path);
    }

}


