package com.osiris.autoplug.client.browser;

import com.machinepublishers.jbrowserdriver.JBrowserDriver;
import com.machinepublishers.jbrowserdriver.Settings;
import com.machinepublishers.jbrowserdriver.Timezone;
import org.junit.jupiter.api.Test;

public class JBrowser {

    @Test
    void test() throws InterruptedException {
        // You can optionally pass a Settings object here,
        // constructed using Settings.Builder
        JBrowserDriver driver = new JBrowserDriver(Settings.builder().
                timezone(Timezone.AMERICA_NEWYORK).build());

        // This will block for the page load and any
        // associated AJAX requests
        driver.get("https://www.spigotmc.org/login");
        Thread.sleep(7000); // Cloudflare is only 5 seconds, just to be sure we do 7 though...
        driver.get("https://www.spigotmc.org/login");

        driver.manage().getCookies().forEach(cookie -> {
            System.out.println(cookie.getName() + ": " + cookie.getValue());
        });

        // You can get status code unlike other Selenium drivers.
        // It blocks for AJAX requests and page loads after clicks
        // and keyboard events.
        System.out.println(driver.getStatusCode());

        // Returns the page source in its current state, including
        // any DOM updates that occurred after page load
        System.out.println(driver.getPageSource());

        // Close the browser. Allows this thread to terminate.
        driver.quit();
    }
}
