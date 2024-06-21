package com.osiris.autoplug.client.tasks.updater;

import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.tasks.updater.search.CustomCheckURL;
import com.osiris.autoplug.client.UtilsTest;


class TestCustomUpdateCheck {
    @Test
    void test() {
      MinecraftPlugin pl = new MinecraftPlugin("./plugins/", "Chunky", "0.0.0", "pop4959", 0, 0, null);
      pl.customCheckURL = "https://api.modrinth.com/v2/project/chunky/version";
      SearchResult sr = new CustomCheckURL().doCustomCheck(pl);
      assertTrue(expected, actual);
    }
}
