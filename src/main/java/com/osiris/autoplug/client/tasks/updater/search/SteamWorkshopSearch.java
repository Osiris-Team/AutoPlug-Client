/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search;

import com.osiris.autoplug.client.tasks.updater.mods.SteamWorkshopMod;
import com.osiris.autoplug.client.utils.SteamCMD;
import org.jetbrains.annotations.NotNull;

public class SteamWorkshopSearch {

    public SearchResult search(@NotNull SteamWorkshopMod mod, String workshopAppId, SteamCMD steamCMD) {
        SearchResult result = new SearchResult(null, SearchResult.Type.UP_TO_DATE, mod.getVersion(), null, "steam-workshop", null, null, false);
        result.mod = mod;
        if (workshopAppId == null || !workshopAppId.matches("\\d+")) {
            result.type = SearchResult.Type.API_ERROR;
            result.setException(new Exception("Steam Workshop mod '" + mod.getName() + "' was found, but server-updater.software is not a numeric Steam app-id."));
            return result;
        }

        try {
            SteamCMD.SteamWorkshopItemDetails details = steamCMD.getWorkshopItemDetails(mod.getPublishedId());
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

    boolean hasUpdate(SteamWorkshopMod mod, String latestVersion) {
        if (latestVersion == null || latestVersion.isEmpty())
            return false;
        String currentVersion = mod.getVersion();
        if (currentVersion == null || currentVersion.isEmpty())
            return true;
        if (latestVersion.equals(currentVersion))
            return false;
        if (currentVersion.equals(mod.getPublishedId()))
            return true;
        if (isSteamUnixTimestamp(latestVersion) && !isSteamUnixTimestamp(currentVersion))
            return true;
        try {
            return Long.parseLong(latestVersion) > Long.parseLong(currentVersion);
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private boolean isSteamUnixTimestamp(String version) {
        return version != null && version.matches("\\d{1,10}");
    }
}
