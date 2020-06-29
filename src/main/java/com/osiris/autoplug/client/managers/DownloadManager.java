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

    public DownloadManager(){
        AutoPlugLogger.newClassDebug("DownloadManager");
    }


    public boolean downloadJar(String download_url, File download_path) {

        WebClient client = new WebClient(BrowserVersion.CHROME);

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

        try {

            HtmlPage page = client.getPage(download_url);

            client.waitForBackgroundJavaScriptStartingBefore(5000); //10000
            client.waitForBackgroundJavaScript(5000); //10000

            synchronized (page) {
                page.wait(7000);
            }



            //Get response
            WebResponse response = client.getPage(download_url).getWebResponse();
            String content_type = response.getContentType();
            int response_status = response.getStatusCode();

            if (response_status != 200) {
                AutoPlugLogger.warn(" [!] Spigot-Error: " + response_status + " [!]");
                AutoPlugLogger.warn(" [!] Could be a premium resource... Check it for yourself: " + download_url + " [!]");
                AutoPlugLogger.warn(" [!] Skipping plugin [!]");


                client.close();
                return false;
            } else {

                try{

                    //Check if response is application(application/octet-stream) or html(text/html)
                    if (content_type.equals("text/html")){
                        AutoPlugLogger.warn(" [!] The download link forwards to another website [!]");
                        AutoPlugLogger.warn(" [!] Nothing will be downloaded [!]");
                        AutoPlugLogger.warn(" [!] In most cases its an external repository like github [!]");
                        AutoPlugLogger.warn(" [!] Notify the dev, that he should add a direct-download link [!]");
                        AutoPlugLogger.warn(" [!] Check it for yourself: " + download_url + " [!]");

                        client.close();
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
                        }catch (IOException e){e.printStackTrace();}

                        client.close();
                        return true;
                    }
                    else {
                        AutoPlugLogger.warn(" [!] Couldn't determine response type [!]");
                        AutoPlugLogger.warn(" [!] But its not a jar file [!]");
                        AutoPlugLogger.warn(" [!] Notify the dev [!]");
                        AutoPlugLogger.warn(" [!] Check it for yourself: " + download_url + " [!]");

                        client.close();
                        return false;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    AutoPlugLogger.warn(" [!] Download-Error: "+e.getMessage());
                    AutoPlugLogger.warn(" [!] Please go and download the file yourself [!] ");
                    AutoPlugLogger.warn(" [!] Link: " + download_url + " [!]");

                    client.close();
                    return false;
                }

            }

        } catch (FailingHttpStatusCodeException e) {
            AutoPlugLogger.warn(" [!] Download-link error: FailingHttpStatusCodeException");
            AutoPlugLogger.warn(" [!] Please go and download the file yourself [!] ");
            AutoPlugLogger.warn(" [!] Link: " + download_url + " [!]");
            e.printStackTrace();

            client.close();
            return false;
        } catch (MalformedURLException e) {
            AutoPlugLogger.warn(" [!] Download-link error: MalformedURLException [!]");
            AutoPlugLogger.warn(" [!] Currently this is unsupported, please go and download the file yourself [!] ");
            AutoPlugLogger.warn(" [!] Link: " + download_url + " [!]");
            e.printStackTrace();

            client.close();
            return false;
        } catch (IOException e) {
            AutoPlugLogger.warn(" [!] Download-link error: IOException [!]");
            AutoPlugLogger.warn(" [!] Please go and download the file yourself [!] ");
            AutoPlugLogger.warn(" [!] Link: " + download_url + " [!]");
            e.printStackTrace();

            client.close();
            return false;
        } catch (InterruptedException e) {
            AutoPlugLogger.warn(" [!] Download-link error: InterruptedException [!]");
            AutoPlugLogger.warn(" [!] Please go and download the file yourself [!] ");
            AutoPlugLogger.warn(" [!] Link: " + download_url + " [!]");
            e.printStackTrace();

            client.close();
            return false;
        }
    }

}
