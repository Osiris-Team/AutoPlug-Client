package com.osiris.autoplug.client.tasks.updater;

import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.tasks.updater.search.CustomUpdateCheck;


class TestCustomUpdateCheck {
    @Test
    void test {
      MinecraftPlugin pl = new MinecraftPlugin();
      pl.customCheckURL = "";
      SearchResult sr = new CustomUpdateCheck().doCustomCheck(pl);
      assertTrue(expected, actual);
    }
}