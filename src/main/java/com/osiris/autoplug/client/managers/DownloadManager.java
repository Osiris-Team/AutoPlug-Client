/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.managers;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.osiris.autoplug.client.utils.AutoPlugLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;

public class DownloadManager {

    private AutoPlugLogger logger = new AutoPlugLogger();


    public boolean downloadJar(String download_url, File cache_path, String latest_version) {


        WebClient client = new WebClient(BrowserVersion.CHROME);

        client.getOptions().setCssEnabled(false);
        client.getOptions().setJavaScriptEnabled(true);
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        client.getOptions().setRedirectEnabled(true);
        client.getCache().setMaxSize(0);
        client.waitForBackgroundJavaScript(10000);
        client.setJavaScriptTimeout(10000);
        client.waitForBackgroundJavaScriptStartingBefore(10000);

        //Prevents the whole html being printed to the console (We always get code 503 when dealing with cloudflare)
        client.getOptions().setPrintContentOnFailingStatusCode(false);
        //This will always happen if its another website we get as result
        client.getOptions().setThrowExceptionOnScriptError(false);


        try {

            HtmlPage page = client.getPage(download_url);

            synchronized (page) {
                page.wait(7000);
            }

            //Get response
            WebResponse response = client.getPage(download_url).getWebResponse();
            String content_type = response.getContentType();
            int response_status = response.getStatusCode();

            if (response_status != 200) {
                logger.global_warn(" [!] Spigot-Error: " + response_status + " [!]");
                logger.global_warn(" [!] Could be a premium resource... Check it for yourself: " + download_url + " [!]");
                logger.global_warn(" [!] Skipping plugin [!]");
                return false;
            } else {

                try{

                    //Check if response is application(application/octet-stream) or html(text/html)
                    if (content_type.equals("text/html")){
                        logger.global_warn(" [!] The download link forwards to another website [!]");
                        logger.global_warn(" [!] Nothing will be downloaded [!]");
                        logger.global_warn(" [!] In most cases its an external repository like github [!]");
                        logger.global_warn(" [!] Notify the dev, that he should add a direct-download link [!]");
                        logger.global_warn(" [!] Check it for yourself: " + download_url + " [!]");

                        return false;
                    }
                    //This is 100% a jar file
                    else if (content_type.equals("application/octet-stream")) {

                        InputStream in = response.getContentAsStream();
                        try (FileOutputStream fileOutputStream = new FileOutputStream(cache_path)) {
                            byte dataBuffer[] = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                                fileOutputStream.write(dataBuffer, 0, bytesRead);
                            }
                        }catch (IOException e){e.printStackTrace();}

                        return true;
                    }
                    else {
                        logger.global_warn(" [!] Couldn't determine response type [!]");
                        logger.global_warn(" [!] But its not a jar file [!]");
                        logger.global_warn(" [!] Notify the dev [!]");
                        logger.global_warn(" [!] Check it for yourself: " + download_url + " [!]");

                        return false;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    logger.global_warn(" [!] Download-Error: "+e.getMessage());
                    logger.global_warn(" [!] Please go and download the file yourself [!] ");
                    logger.global_warn(" [!] Link: " + download_url + " [!]");
                    return false;
                }

            }

        } catch (FailingHttpStatusCodeException e) {
            logger.global_warn(" [!] Download-link error: FailingHttpStatusCodeException");
            logger.global_warn(" [!] Please go and download the file yourself [!] ");
            logger.global_warn(" [!] Link: " + download_url + " [!]");
            e.printStackTrace();
            return false;
        } catch (MalformedURLException e) {
            logger.global_warn(" [!] Download-link error: MalformedURLException [!]");
            logger.global_warn(" [!] Currently this is unsupported, please go and download the file yourself [!] ");
            logger.global_warn(" [!] Link: " + download_url + " [!]");
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            logger.global_warn(" [!] Download-link error: IOException [!]");
            logger.global_warn(" [!] Please go and download the file yourself [!] ");
            logger.global_warn(" [!] Link: " + download_url + " [!]");
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            logger.global_warn(" [!] Download-link error: InterruptedException [!]");
            logger.global_warn(" [!] Please go and download the file yourself [!] ");
            logger.global_warn(" [!] Link: " + download_url + " [!]");
            e.printStackTrace();
            return false;
        }
    }

}
