/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.dyml.exceptions.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

public class UtilsJar {

    public File determineServerJar() throws YamlWriterException, NotLoadedException, IOException, IllegalKeyException, DuplicateKeyException, YamlReaderException, IllegalListException {
        GeneralConfig generalConfig = new GeneralConfig();
        String path = generalConfig.server_start_command.asString();
        if (path == null) return null;
        if (path.contains("-jar ")) { // jar file
            path = path.substring(path.indexOf("-jar "));
            if (path.codePointAt(5) == '"') {
                for (int i = 6; i < path.length(); i++) {
                    char c = (char) path.codePointAt(i);
                    if (c == '\"') return new File(path.substring(6, i));
                }
                throw new RuntimeException("Server jar path started with \" but didn't finish with another \"!" + path);
            } else {
                return new File(path.split(" ")[1]);
            }
        } else { // probably exe file
            if (path.startsWith("\"")) {
                for (int i = 1; i < path.length(); i++) {
                    char c = (char) path.codePointAt(i);
                    if (c == '\"') return new File(path.substring(1, i));
                }
                throw new RuntimeException("Server jar path started with \" but didn't finish with another \"! " + path);
            } else {
                return new File(path.split(" ")[0]);
            }
        }
    }

    /**
     * Returns the currently running jar file.
     */
    public File getThisJar() throws URISyntaxException {
        String path = Main.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath();
        return new File(path);
    }

    @NotNull
    public Properties getThisJarsAutoPlugProperties() throws Exception {
        return getAutoPlugPropertiesFromJar(Main.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath());
    }

    @NotNull
    public Properties getAutoPlugPropertiesFromJar(@NotNull String path) throws Exception {
        return getPropertiesFromJar(path, "autoplug");
    }

    /**
     * This creates an URLClassLoader so we can access the autoplug.properties file inside the jar and then returns the properties file.
     *
     * @param path               The jars path
     * @param propertiesFileName Properties file name without its .properties extension.
     * @return autoplug.properties
     * @throws Exception
     */
    @NotNull
    public Properties getPropertiesFromJar(@NotNull String path, String propertiesFileName) throws Exception {
        File file = new File(path); // The properties file
        if (file.exists()) {
            Collection<URL> urls = new ArrayList<URL>();
            urls.add(file.toURI().toURL());
            URLClassLoader fileClassLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]));

            java.io.InputStream is = fileClassLoader.getResourceAsStream(propertiesFileName + ".properties");
            java.util.Properties p = new java.util.Properties();
            p.load(is);
            return p;
        } else
            throw new Exception("Couldn't find the properties file at: " + path);
    }

}
