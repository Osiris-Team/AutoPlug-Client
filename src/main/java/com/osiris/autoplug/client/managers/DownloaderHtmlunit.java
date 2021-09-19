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
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.osiris.autoplug.core.logger.AL;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

 */

@Deprecated
public class DownloaderHtmlunit { //implements IDownloader
    /*
    private WebClient client;
    private HtmlPage page;
    private WebResponse response;

    @Nullable
    @Override
    public InputStream getInputStreamFromDownload(String download_url) throws Exception {

        client = new WebClient(BrowserVersion.CHROME);

        client.getOptions().setCssEnabled(false); //false
        client.getOptions().setJavaScriptEnabled(true);
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        client.getOptions().setRedirectEnabled(true);
        client.getCache().setMaxSize(1);
        client.setJavaScriptTimeout(0); //10000

        //Prevents the whole html being printed to the console (We always get code 503 when dealing with cloudflare)
        client.getOptions().setPrintContentOnFailingStatusCode(false);

        //This will always happen if its another website we get as result
        client.getOptions().setThrowExceptionOnScriptError(false);
        client.getOptions().setDoNotTrackEnabled(true);



        //Start the actual download process


        page = client.getPage(download_url);

        client.waitForBackgroundJavaScriptStartingBefore(7000); //10000
        client.waitForBackgroundJavaScript(7000); //10000

        synchronized (page) {
            page.wait(7000);
        }

        //Get response
        response = client.getPage(download_url).getWebResponse();
        String content_type = response.getContentType();
        int response_status = response.getStatusCode();

        if (response_status != 200) {
            AL.warn("Spigot-Error: " + response_status);
            AL.warn("Could be a premium resource... Check it for yourself: " + download_url);
            AL.warn("Skipping plugin");

            closeAll();
            return null;
        } else {

            try {

                //Check if response is application(application/octet-stream) or html(text/html)
                if (content_type.equals("application/octet-stream")) {
                    return response.getContentAsStream();
                } else if (content_type.equals("text/html")) {
                    AL.warn("The download link forwards to another website");
                    AL.warn("Nothing will be downloaded");
                    AL.warn("In most cases its an external repository like github");
                    AL.warn("Notify the dev, that he should add a direct-download link");
                    AL.warn("Check it for yourself: " + download_url);

                    closeAll();
                    return null;
                } else {
                    AL.warn("Couldn't determine response type");
                    AL.warn("But its not a jar file");
                    AL.warn("Notify the dev");
                    AL.warn("Check it for yourself: " + download_url);

                    closeAll();
                    return null;
                }


            } catch (IOException e) {
                e.printStackTrace();
                AL.warn("Download-Error: " + e.getMessage());
                AL.warn("Please go and download the file yourself");
                AL.warn("Link: " + download_url);

                closeAll();
                return null;
            }

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
*/
}

