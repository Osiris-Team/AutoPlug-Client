package com.osiris.autoplug.client;

import org.cef.callback.CefCookieVisitor;
import org.cef.misc.BoolRef;
import org.cef.network.CefCookie;
import org.cef.network.CefCookieManager;
import org.junit.jupiter.api.Test;
import org.panda_lang.pandomium.Pandomium;
import org.panda_lang.pandomium.settings.PandomiumSettings;
import org.panda_lang.pandomium.wrapper.PandomiumBrowser;
import org.panda_lang.pandomium.wrapper.PandomiumClient;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ChromiumTests {

    @Test
    void test() throws InterruptedException {
        // TODO findout how to disable the logger

        String username = "email";
        String password = "pass";
        String loginUrl = "https://www.spigotmc.org/login";

        Logger.getLogger("org.panda_lang.pandomium.Pandomium").setLevel(Level.OFF);
        PandomiumSettings settings = PandomiumSettings.getDefaultSettingsBuilder()
                /*
                .logger(new DefaultLogger(Channel.ALL) {
                    @Override
                    protected void internalLog(Channel channel, String message) {
                        if(channel.equals(Channel.INFO))
                            AL.info(message);
                        else if(channel.equals(Channel.DEBUG))
                            AL.debug(this.getClass(), message);
                        else if(channel.equals(Channel.ERROR))
                            AL.warn(message);
                        else
                            AL.info(message);
                    }
                })

                 */
                .nativeDirectory(System.getProperty("user.dir") + "/autoplug-system/natives")
                //.proxy("localhost", 20) // blank page
                .build();

        Pandomium pandomium = new Pandomium(settings);
        pandomium.initialize();

        PandomiumClient client = pandomium.createClient();

        System.out.println("Loading '" + loginUrl + "'. Please stand by...");
        PandomiumBrowser browser = client.loadURL(loginUrl);
        System.out.println("Loaded '" + loginUrl + "'!");


        System.out.println("Logging in...");
        browser.getCefBrowser().executeJavaScript("" +
                "document.getElementById('ctrl_pageLogin_login').value=\"" + username + "\";" +
                "document.getElementById('ctrl_pageLogin_password').value=\"" + password + "\";" +
                "document.forms[0].submit();", "", 1);
        Thread.sleep(1000);

        System.out.println("Cookies: ");
        boolean hasAccessToCookies = CefCookieManager.getGlobalManager().visitAllCookies(new CefCookieVisitor() {
            @Override
            public boolean visit(CefCookie cefCookie, int i, int i1, BoolRef boolRef) {
                System.out.println("Cookie: " + cefCookie.name + " Value: " + cefCookie.value);
                return false;
            }
        });

        if (!hasAccessToCookies)
            System.out.println("Failed to display cookies due to lack of access!");

    }
}
