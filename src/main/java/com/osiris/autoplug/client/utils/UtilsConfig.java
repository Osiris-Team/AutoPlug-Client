/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.utils.tasks.CoolDownReport;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import com.osiris.dyml.exceptions.IllegalListException;
import com.osiris.dyml.exceptions.YamlReaderException;
import com.osiris.dyml.exceptions.YamlWriterException;
import com.osiris.dyml.utils.UtilsYamlSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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
    public void checkForDeprecatedSections(Yaml yaml) throws YamlWriterException, IOException, DuplicateKeyException, YamlReaderException, IllegalListException {
        yaml.lockFile();
        List<YamlSection> inEditModules = yaml.getAllInEdit();
        List<YamlSection> loadedModules = yaml.getAllLoaded();
        List<YamlSection> oldModules = new ArrayList<>();
        UtilsYamlSection utils = new UtilsYamlSection();
        for (YamlSection m :
                loadedModules) {
            if (utils.getExisting(m, inEditModules) == null && m.asString() != null) {
                oldModules.add(m);
                AL.warn("Deprecated config section found: " + m.getKeys().toString());
            }
        }
        // Set the comments
        for (YamlSection oldM :
                oldModules) {
            yaml.get(oldM.getKeys()).setComments("DEPRECATION WARNING <---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------",
                    "THE BELOW WAS RENAMED OR REMOVED AND THUS ITS VALUE(S) WILL BE IGNORED!");
        }
        yaml.save();
        yaml.unlockFile();
    }

    public void printAllModulesToDebugExceptServerKey(@NotNull List<YamlSection> modules, String serverKey) {
        try {
            UtilsYamlSection utils = new UtilsYamlSection();
            for (YamlSection module :
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
