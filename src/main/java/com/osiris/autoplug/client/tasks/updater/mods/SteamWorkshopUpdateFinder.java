/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.SteamCMD;
import org.jetbrains.annotations.NotNull;

/**
 * Finds updates for Steam Workshop mods via the Steam Web API, similar to how
 * {@link com.osiris.autoplug.client.tasks.updater.plugins.ResourceFinder}
 * finds updates for regular mods. Only reports an update when the Workshop
 * item was actually updated after the currently cached version, so mods are
 * not re-downloaded on every run.
 */
public class SteamWorkshopUpdateFinder {
    private final UpdaterConfig updaterConfig;
    private final SteamCMD steamCMD;

    public SteamWorkshopUpdateFinder(UpdaterConfig updaterConfig, SteamCMD steamCMD) {
        this.updaterConfig = updaterConfig;
        this.steamCMD = steamCMD;
    }

    public SearchResult find(@NotNull SteamWorkshopMod mod) {
        SearchResult result = new SearchResult(null, SearchResult.Type.UP_TO_DATE, mod.getVersion(), null, "steam-workshop", null, null, false);
        result.mod = mod;

        try {
            SteamCMD.SteamWorkshopItemDetails details = steamCMD.getWorkshopItemDetails(mod.getPublishedId());
            String workshopAppId = details.getConsumerAppId();
            if (workshopAppId == null || !workshopAppId.matches("[1-9]\\d*"))
                workshopAppId = getWorkshopAppId();
            if (workshopAppId == null) {
                result.type = SearchResult.Type.API_ERROR;
                result.setException(new Exception("Steam Workshop mod '" + mod.getName() + "' was found, but neither Steam's response nor server-updater.software provided a numeric Steam app-id."));
                return result;
            }
            mod.setConsumerAppId(workshopAppId);
            result.latestVersion = details.getTimeUpdated();
            result.downloadUrl = details.getFileUrl();
            if (hasUpdate(mod, details.getTimeUpdated()))
                result.type = SearchResult.Type.UPDATE_AVAILABLE;
        } catch (Exception e) {
            result.type = SearchResult.Type.API_ERROR;
            result.setException(e);
        }
        return result;
    }

    /**
     * Returns the Steam app-id configured in server-updater.software,
     * or null when it is not a numeric Steam app-id (e.g. "paper").
     */
    public String getWorkshopAppId() {
        String workshopAppId = updaterConfig.server_software.asString();
        if (workshopAppId == null || !workshopAppId.matches("[1-9]\\d*"))
            return null;
        return workshopAppId;
    }

    /**
     * Compares the cached version with the time_updated value of the Workshop item.
     * A missing cached version means the mod was never update-checked before.
     */
    boolean hasUpdate(SteamWorkshopMod mod, String latestTimeUpdated) {
        if (latestTimeUpdated == null || latestTimeUpdated.isEmpty())
            return false;
        String currentVersion = mod.getVersion();
        if (currentVersion == null || currentVersion.isEmpty())
            return true;
        if (latestTimeUpdated.equals(currentVersion))
            return false;
        // A DayZ/Arma meta.cpp commonly stores the local version as a .NET
        // timestamp (16-19 digits), while Steam's time_updated is Unix time
        // (currently 10 digits). They are both numeric strings, so comparing
        // them as longs makes every real mod look up to date on its first run.
        if (currentVersion.matches("\\d{16,19}") && latestTimeUpdated.matches("\\d{9,11}"))
            return true;
        try {
            return Long.parseLong(latestTimeUpdated) > Long.parseLong(currentVersion);
        } catch (NumberFormatException e) {
            // Versions are not numerically comparable (e.g. a meta.cpp FILETIME
            // timestamp vs the unix time_updated from Steam). Assume an update.
            return true;
        }
    }
}
