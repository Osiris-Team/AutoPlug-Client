/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.profiles;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.tasks.updater.mods.ModrinthAPI;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.tasks.updater.search.bukkit.BukkitSearchById;
import java.time.Instant;
import java.util.Collections;

/** Explicit opt-in production-transport smoke. Only public metadata GETs; no artifact downloads, launches or accounts. */
public final class ProfileProviderLiveSmoke {
    public static void main(String[] args) throws Exception {
        ModrinthAPI api = new ModrinthAPI();
        JsonObject compatible = api.compatibleProfileRelease("P7dR8mSH", Collections.singletonList("fabric"), "1.20.1", null);
        if (compatible == null || !"P7dR8mSH".equals(compatible.get("project_id").getAsString())) throw new IllegalStateException("No actual compatible Fabric API release");
        JsonObject pinned = api.compatibleProfileRelease("P7dR8mSH", Collections.singletonList("fabric"), "1.20.1", compatible.get("id").getAsString());
        if (pinned == null || !pinned.get("id").equals(compatible.get("id"))) throw new IllegalStateException("Pinned release validation failed");
        SearchResult bukkit = new BukkitSearchById().searchCompatible(new MinecraftPlugin("not-read-or-written.jar", "WorldEdit", "0", "", 0, 31043, null), "1.20.1");
        if (bukkit.isError()) throw new IllegalStateException("Actual Bukkit compatible metadata lookup failed", bukkit.exception);
        if (bukkit.type == SearchResult.Type.RESOURCE_NOT_FOUND) throw new IllegalStateException("No actual compatible WorldEdit release");
        if (bukkit.downloadUrl == null || bukkit.fileName == null || bukkit.expectedMD5 == null) throw new IllegalStateException("Missing actual Bukkit artifact identity/checksum");
        System.out.println("PASS production Java public metadata transports at " + Instant.now());
        System.out.println("Modrinth project=" + compatible.get("project_id").getAsString() + " version=" + compatible.get("id").getAsString() + " target=1.20.1/fabric; pinned exact match");
        System.out.println("Bukkit project=31043 target=1.20.1 artifact=" + bukkit.fileName + "; MD5 supplied");
        System.out.println("No artifact downloaded; no account, game or global server configuration accessed");
    }
}
