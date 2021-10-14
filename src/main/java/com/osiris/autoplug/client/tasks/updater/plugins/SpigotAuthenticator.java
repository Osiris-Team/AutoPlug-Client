/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;

import com.google.gson.*;
import com.osiris.autoplug.client.configs.SystemConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.headlessbrowser.windows.PlaywrightWindow;
import org.jsoup.nodes.Document;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.HttpCookie;
import java.util.Random;

public class SpigotAuthenticator {

    /**
     * Throws an exceptions, with the details, if the login attempt fails.
     */
    public void attemptLoginForWindow(PlaywrightWindow window) throws Exception {
        boolean isLoggedIn = false;
        File spigotCookiesJson = new File(new SystemConfig().getFile().getParentFile() + "/spigot-cookies.json");
        if (!spigotCookiesJson.exists()) spigotCookiesJson.createNewFile();
        if (spigotCookiesJson.length() != 0) {
            boolean setSpigotXfUserCookie = false;
            boolean setSpigotXfSessionCookie = false;
            for (JsonElement jsonElement :
                    new Gson().fromJson(new FileReader(spigotCookiesJson), JsonArray.class)) {
                HttpCookie httpCookie = parseJsonCookieToHttpCookie(jsonElement.getAsJsonObject());
                if (httpCookie.getName().equals("xf_user")) {
                    window.setCookie(httpCookie);
                    setSpigotXfUserCookie = true;
                } else if (httpCookie.getName().equals("xf_session")) {
                    window.setCookie(httpCookie);
                    setSpigotXfSessionCookie = true;
                }
            }
            if (!setSpigotXfUserCookie) throw new Exception("Failed to set 'xf_user' cookie!");
            if (!setSpigotXfSessionCookie) throw new Exception("Failed to set 'xf_session' cookie!");
            // Since the cookies responsible for authentication have been set, we should have been successfully logged in
        }

        AL.debug(this.getClass(), "Logging in with cookies...");
        window.load("https://www.spigotmc.org/login");
        Thread.sleep(new Random().nextInt(10000 - 7000) + 7000); // Wait for Cloudflare checks to finish // Random number between 7s and 10s
        boolean cloudflarePassed = false;
        for (int i = 0; i < 60; i++) {
            Thread.sleep(1000);
            if (isCloudflarePassed(window)) {
                cloudflarePassed = true;
                break;
            }
        }
        if (!cloudflarePassed) throw new Exception("Failed to pass the cloudflare check!");
        AL.debug(this.getClass(), "Cloudflare passed!");

        isLoggedIn = isLoginSuccess(window);
        if (!isLoggedIn) {
            if (spigotCookiesJson.length() != 0)
                AL.debug(this.getClass(), "Failed to login with cookies from " + spigotCookiesJson.getName() + " file. Attempting manual login with actual credentials.");

            UpdaterConfig updaterConfig = new UpdaterConfig();
            window.executeJS("" +
                    "document.getElementById('ctrl_pageLogin_login').value=\"" + updaterConfig.plugin_updater_spigot_username.asString() + "\";\n" +
                    "document.getElementById('ctrl_pageLogin_password').value=\"" + updaterConfig.plugin_updater_spigot_password.asString() + "\";\n" +
                    "document.forms[0].submit();\n");
            Thread.sleep(1000);
            isLoggedIn = isLoginSuccess(window);
        }

        if (!isLoggedIn) {
            throw new Exception("Spigot-Login failed with provided credentials! Update your credentials and run the updating task again.");
        } else { // Update cookies file
            AL.debug(this.getClass(), "Successfully logged in into Spigot! Cookies should been updated.");
            JsonObject xfUserJsonCookie = null;
            JsonObject xfSessionJsonCookie = null;
            for (JsonElement element :
                    window.getCookiesAsJsonArray()) {
                JsonObject jsonCookie = element.getAsJsonObject();
                String name = jsonCookie.get("name").getAsString();
                if (name.equals("xf_user")) {
                    xfUserJsonCookie = jsonCookie;
                } else if (name.equals("xf_session")) {
                    xfSessionJsonCookie = jsonCookie;
                }
            }

            if (xfUserJsonCookie == null || xfSessionJsonCookie == null)
                throw new Exception("Failed to find xf_user and xf_session cookies after login!");

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(spigotCookiesJson))) {
                JsonArray array = new JsonArray();
                array.add(xfUserJsonCookie);
                array.add(xfSessionJsonCookie);
                bw.write(new GsonBuilder().setPrettyPrinting().create().toJson(array));
            }
            AL.debug(this.getClass(), "Updated Spigot-Login-Cookies successfully!");
        }
    }

    private boolean isCloudflarePassed(PlaywrightWindow window) {
        Document doc = window.getDocument();
        return doc.getElementsByClass("cf-browser-verification").isEmpty() && doc.getElementsByClass("cf-im-under-attack").isEmpty();
    }

    public boolean isLoginSuccess(PlaywrightWindow window) {
        return !window.getDocument().getElementsByClass("accountUsername").isEmpty();
    }

    public HttpCookie parseJsonCookieToHttpCookie(JsonObject jsonCookie) {
        HttpCookie httpCookie = new HttpCookie(jsonCookie.get("name").getAsString(), jsonCookie.get("value").getAsString());
        httpCookie.setDomain(jsonCookie.get("domain").getAsString());
        httpCookie.setPath(jsonCookie.get("path").getAsString());
        httpCookie.setMaxAge(jsonCookie.get("max_age").getAsLong());
        httpCookie.setHttpOnly(jsonCookie.get("http_only").getAsBoolean());
        httpCookie.setSecure(jsonCookie.get("secure").getAsBoolean());
        return httpCookie;
    }
}
