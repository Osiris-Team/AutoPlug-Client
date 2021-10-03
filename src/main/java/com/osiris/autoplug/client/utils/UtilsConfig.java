/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.utils.UtilsDYModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Frequently used code of config stuff.
 */
public class UtilsConfig {

    /**
     * Adds a deprecation note (comment) for config sections that have been renamed or removed. <br>
     * Also warns the user about these in the console. <br>
     * Note that this only works for config sections that have values. <br>
     */
    public void setCommentsOfNotUsedOldDYModules(List<DYModule> inEditModules, List<DYModule> loadedModules) {
        List<DYModule> oldModules = new ArrayList<>();
        UtilsDYModule utils = new UtilsDYModule();
        for (DYModule m :
                loadedModules) {
            if (utils.getExisting(m, inEditModules) == null && m.asString() != null) {
                oldModules.add(m);
                AL.warn("Deprecated config section found: " + m.getKeys().toString());
            }
        }
        // Set the comments
        for (DYModule oldM :
                oldModules) {
            oldM.setComments("[!!!] DEPRECATION WARNING [!!!]",
                    "THIS CONFIG SECTION WAS RENAMED OR REMOVED AND THUS ITS VALUE WILL BE IGNORED!");
        }
    }

    public void printAllModulesToDebugExceptServerKey(@NotNull List<DYModule> modules, String serverKey) {
        try {
            UtilsDYModule utils = new UtilsDYModule();
            for (DYModule module :
                    modules) {
                if (module.asString() != null && module.asString().equals(serverKey)) {
                    AL.debug(this.getClass(), module.getKeys().toString() + " VAL: SERVER KEY NOT SHOWN DUE TO SECURITY RISK  DEF: " + utils.valuesListToStringList(module.getDefValues()).toString());
                } else {
                    AL.debug(this.getClass(), module.getKeys().toString() + " VAL: " + utils.valuesListToStringList(module.getValues()).toString() + " DEF: " + utils.valuesListToStringList(module.getDefValues()).toString());
                }
            }
        } catch (Exception e) {
            AL.warn("Couldn't show/write ConfigModule information!", e);
        }
    }


    @NotNull
    public CoolDownReport getCoolDown(int coolDownInMinutes, @NotNull SimpleDateFormat format, @Nullable String lastTasksTimestamp) {
        try {
            if (lastTasksTimestamp != null) {
                long last = format.parse(lastTasksTimestamp).getTime();
                long now = System.currentTimeMillis();
                long msSinceLast = now - last;
                long msCoolDown = ((coolDownInMinutes * 60L) * 1000);
                return new CoolDownReport(msSinceLast, msCoolDown);
            }
        } catch (Exception e) {
            AL.warn(e);
        }
        return new CoolDownReport(0, 0);
    }

}
