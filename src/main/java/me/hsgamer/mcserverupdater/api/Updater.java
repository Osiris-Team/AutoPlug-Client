package me.hsgamer.mcserverupdater.api;

import me.hsgamer.hscore.logger.common.LogLevel;
import me.hsgamer.hscore.logger.common.Logger;

import java.io.File;

public interface Updater {
    boolean update(File file) throws Exception;

    Logger getLogger();

    default void debug(String message) {
        getLogger().log(LogLevel.DEBUG, message);
    }

    default void debug(String format, Object... args) {
        debug(String.format(format, args));
    }

    default void debug(Throwable throwable) {
        getLogger().log(LogLevel.DEBUG, throwable);
    }

    default void debug(String message, Throwable throwable) {
        getLogger().log(LogLevel.DEBUG, message, throwable);
    }
}
