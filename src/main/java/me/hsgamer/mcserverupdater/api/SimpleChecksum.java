package me.hsgamer.mcserverupdater.api;

import java.io.File;

public interface SimpleChecksum extends Checksum {
    String getChecksum();

    String getCurrentChecksum(File file) throws Exception;

    @Override
    default boolean checksum(File file) throws Exception {
        String checksum = getChecksum();
        if (this instanceof Updater) {
            ((Updater) this).debug("Checksum: " + checksum);
        }
        if (checksum == null) {
            return false;
        }
        String currentChecksum = getCurrentChecksum(file);
        if (this instanceof Updater) {
            ((Updater) this).debug("Current checksum: " + currentChecksum);
        }
        return currentChecksum.equals(checksum);
    }
}
