/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.managers;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Deprecated
public class DownloadManager {

    private WebClient client;
    private HtmlPage page;
    private WebResponse response;

    public static void main(String[] args) {

        //TODO MAKE THIS WORK
        //See https://github.com/HtmlUnit/htmlunit/issues/201

        String testUrl1 = "https://www.spigotmc.org/resources/unlimited-enchants.82329/download?version=348662";
        String testUrl2 = "https://www.spigotmc.org/resources/rankmeup-gui-based-rankup-plugin-1-8-1-16.81464/download?version=348659";
        String testUrl3 = "https://www.spigotmc.org/resources/fractal-trees-tree-generator.82146/download?version=348646";
        final String finalDownloadUrl = testUrl3;

        File downloadTestFile = new File(System.getProperty("user.dir") + "/TEST.jar");
        try {
            if (!downloadTestFile.exists()) {
                downloadTestFile.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Starting jar download from: " + finalDownloadUrl);
        System.out.println("File download path: " + downloadTestFile.getAbsolutePath());

        DownloadManager dm = new DownloadManager();
        dm.downloadJar(finalDownloadUrl, downloadTestFile);

    }

    public boolean downloadJar(String download_url, File download_path) {

        client = new WebClient(BrowserVersion.CHROME);

        client.getOptions().setCssEnabled(false); //false
        client.getOptions().setJavaScriptEnabled(true);
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        client.getOptions().setRedirectEnabled(true);
        client.getCache().setMaxSize(1); //1
        client.setJavaScriptTimeout(0); //10000

        // Testing options 12.10.2020
        client.getOptions().setGeolocationEnabled(false);
        client.getOptions().setDoNotTrackEnabled(true);
        client.getOptions().setDownloadImages(false);


        //Prevents the whole html being printed to the console (We always get code 503 when dealing with cloudflare)
        client.getOptions().setPrintContentOnFailingStatusCode(false);

        //This will always happen if its another website we get as result
        client.getOptions().setThrowExceptionOnScriptError(false);
        client.getOptions().setDoNotTrackEnabled(true);

        try {


            page = client.getPage(download_url);

            client.waitForBackgroundJavaScriptStartingBefore(7000); //10000
            client.waitForBackgroundJavaScript(7000); //10000

            //Print out the pages content for testing purposes
            System.out.println(" ");
            System.out.println("DEBUG 1 - BEFORE ########################################################");
            System.out.println("Status: " + page.getWebResponse().getStatusMessage());
            System.out.println("Status code: " + page.getWebResponse().getStatusCode());
            System.out.println("Content type: " + page.getContentType());
            System.out.println("Page content before waiting: ");
            System.out.println(page.asText());
            System.out.println("Cookies enabled: " + client.getCookieManager().isCookiesEnabled());
            System.out.println("Cookies: " + Arrays.toString(client.getCookieManager().getCookies().toArray()));
            System.out.println(" ");

            /*
            int secsToWait = 10;
            System.out.println("Sleeping for "+secsToWait+" seconds!");
            for (int i = 0; i < secsToWait; i++) {
                Thread.sleep(1000);
                System.out.println("Time passed: "+i+"secs...");
            }
             */

            synchronized (page) {
                page.wait(7000);
            }


            //Get response
            HtmlPage page2 = client.getPage(download_url);
            response = page2.getWebResponse();
            String content_type = response.getContentType();
            int response_status = response.getStatusCode();

            //Print out the pages content for testing purposes
            System.out.println(" ");
            System.out.println("DEBUG 2 - AFTER ########################################################");
            System.out.println("Status: " + response.getStatusMessage());
            System.out.println("Status code: " + response_status);
            System.out.println("Content type: " + content_type);
            System.out.println("Page content after waiting: ");
            System.out.println(page2.asText());
            //System.out.println("Page response as String: ");
            //System.out.println(response.getContentAsString());
            System.out.println("Cookies enabled: " + client.getCookieManager().isCookiesEnabled());
            System.out.println("Cookies: " + Arrays.toString(client.getCookieManager().getCookies().toArray()));
            System.out.println(" ");


            if (response_status != 200) {
                System.out.println(" [!] Spigot-Error: " + response_status + " [!]");
                System.out.println(" [!] Could be a premium resource... Check it for yourself: " + download_url + " [!]");
                System.out.println(" [!] Skipping plugin [!]");

                closeAll();
                return false;
            } else {

                try {

                    //Check if response is application(application/octet-stream) or html(text/html)
                    if (content_type.equals("text/html")) {
                        System.out.println(" [!] The download link forwards to another website [!]");
                        System.out.println(" [!] Nothing will be downloaded [!]");
                        System.out.println(" [!] In most cases its an external repository like github [!]");
                        System.out.println(" [!] Notify the dev, that he should add a direct-download link [!]");
                        System.out.println(" [!] Check it for yourself: " + download_url + " [!]");

                        closeAll();
                        return false;
                    }
                    //This is 100% a jar file
                    else if (content_type.equals("application/octet-stream")) {

                        InputStream in = response.getContentAsStream();
                        try (FileOutputStream fileOutputStream = new FileOutputStream(download_path)) {
                            byte[] dataBuffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                                fileOutputStream.write(dataBuffer, 0, bytesRead);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            System.out.println(" [!] Failed to download file [!]");
                            System.out.println(" [!] Error: " + e.getMessage() + "[!]");

                            closeAll();
                            return false;
                        }

                        closeAll();
                        return true;
                    } else {
                        System.out.println(" [!] Couldn't determine response type [!]");
                        System.out.println(" [!] But its not a jar file [!]");
                        System.out.println(" [!] Notify the dev [!]");
                        System.out.println(" [!] Check it for yourself: " + download_url + " [!]");

                        closeAll();
                        return false;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println(" [!] Download-Error: " + e.getMessage());
                    System.out.println(" [!] Please go and download the file yourself [!] ");
                    System.out.println(" [!] Link: " + download_url + " [!]");

                    closeAll();
                    return false;
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(" [!] Download error: " + e.getMessage());
            System.out.println(" [!] Please go and download the file yourself [!] ");
            System.out.println(" [!] Link: " + download_url + " [!]");

            closeAll();
            return false;
        }
    }

    private void closeAll() {

        if (response != null) {
            response.cleanUp();
        }
        if (page != null) {
            page.cleanUp();
        }
        if (client != null) {
            client.close();
        }

    }

}
