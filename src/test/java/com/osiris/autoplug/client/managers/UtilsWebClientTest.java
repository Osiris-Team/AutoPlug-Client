/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.managers;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class UtilsWebClientTest {

    @Test
    void test() {
        WebClient client = UtilsWebClient.getNewCustomClient(30000, 10000,
                UtilsWebClient.buildUniqueUserAgent()); //"AutoPlug Client/"+new Random().nextInt()+" - https://autoplug.online"

        String testUrl1 = "https://www.spigotmc.org/resources/unlimited-enchants.82329/download?version=348662";
        String testUrl2 = "https://www.spigotmc.org/resources/rankmeup-gui-based-rankup-plugin-1-8-1-16.81464/download?version=348659";
        String testUrl3 = "https://www.spigotmc.org/resources/fractal-trees-tree-generator.82146/download?version=348646";

        try{
            HtmlPage page = client.getPage(testUrl1);

            //Print out the pages content for testing purposes
            System.out.println(" ");
            System.out.println("DEBUG 1 - BEFORE ########################################################");
            System.out.println("Status: "+page.getWebResponse().getStatusMessage());
            System.out.println("Status code: "+page.getWebResponse().getStatusCode());
            System.out.println("Content type: "+page.getContentType());
            System.out.println("Page content before waiting: ");
            System.out.println(page.asText());
            System.out.println("Cookies enabled: "+client.getCookieManager().isCookiesEnabled());
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
                page.wait(10000);
            }


            //Get response
            WebResponse response = page.getWebResponse();
            String content_type = response.getContentType();
            int response_status = response.getStatusCode();

            //Print out the pages content for testing purposes
            System.out.println(" ");
            System.out.println("DEBUG 2 - AFTER ########################################################");
            System.out.println("Status: "+response.getStatusMessage());
            System.out.println("Status code: "+response_status);
            System.out.println("Content type: "+content_type);
            System.out.println("Page content after waiting: ");
            System.out.println(page.asText());
            //System.out.println("Page response as String: ");
            //System.out.println(response.getContentAsString());
            System.out.println("Cookies enabled: "+client.getCookieManager().isCookiesEnabled());
            System.out.println("Cookies: " + Arrays.toString(client.getCookieManager().getCookies().toArray()));
            System.out.println(" ");
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}