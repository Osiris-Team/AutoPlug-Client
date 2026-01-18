package io.github.projectunified.mcserverupdater.api;

import java.io.File;

public interface Updater {
    boolean update(File file) throws Exception;

    Checksum getChecksumChecker();

    DebugConsumer getDebugConsumer();

    default void debug(String message) {
        getDebugConsumer().consume(message);
    }

    default void debug(String format, Object... args) {
        debug(String.format(format, args));
    }

    default void debug(Throwable throwable) {
        getDebugConsumer().consume(throwable);
    }

    default void debug(String message, Throwable throwable) {
        getDebugConsumer().consume(message, throwable);
    }
}
