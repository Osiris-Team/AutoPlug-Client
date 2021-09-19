/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.managers;

/*
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.logging.Level;

 */

@Deprecated
public class UtilsWebClient {
    /*
    @NotNull
    public static String buildUniqueUserAgent() {
        StringBuilder b = new StringBuilder();
        b.append("Mozilla/5.0 " +
                "(Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537." + new Random().nextInt(250) + " " +
                "(KHTML, like Gecko) " +
                "Chrome/87.0.4280." + new Random().nextInt(250) + " " +
                "Safari/537." + new Random().nextInt(250));
        return b.toString();
    }

     */

    /**
     * Provided by: https://github.com/HtmlUnit/htmlunit/issues/249
     */
    /*
    @NotNull
    public static WebClient getNewCustomClient(int pageTimeout, int jsTimeout, String UserAgent) {
        java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);

        BrowserVersion ua = new BrowserVersion.BrowserVersionBuilder(BrowserVersion.CHROME).setUserAgent(UserAgent).build();
        WebClient client = new WebClient(ua);

        client.getOptions().setUseInsecureSSL(true);

        client.getOptions().setJavaScriptEnabled(true);
        client.getOptions().setCssEnabled(true);
        client.getOptions().setDownloadImages(false);
        client.getOptions().setRedirectEnabled(true);
        client.getOptions().setPopupBlockerEnabled(true);
        client.getOptions().setDoNotTrackEnabled(true);
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        client.getOptions().setThrowExceptionOnScriptError(false);
        client.getOptions().setGeolocationEnabled(false);
        client.getOptions().setTimeout(pageTimeout);
        client.getOptions().setActiveXNative(true);
        client.getOptions().setAppletEnabled(true);
        client.getOptions().setScreenHeight(768);
        client.getOptions().setScreenWidth(1366);

        client.setAjaxController(new NicelyResynchronizingAjaxController());
        client.setAlertHandler((page, message) -> System.err.println("[alert] " + message)); // to be changed
        client.setJavaScriptTimeout(jsTimeout);
        client.getCookieManager().setCookiesEnabled(true);
        client.getCache().setMaxSize(1);

        return client;
    }


     */
}
