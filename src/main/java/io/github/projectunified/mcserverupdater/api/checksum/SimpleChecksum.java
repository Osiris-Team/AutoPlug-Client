package io.github.projectunified.mcserverupdater.api.checksum;

import io.github.projectunified.mcserverupdater.api.Checksum;
import io.github.projectunified.mcserverupdater.api.DebugConsumer;

import java.io.File;

public interface SimpleChecksum extends Checksum {
    String getChecksum();

    String getCurrentChecksum(File file) throws Exception;

    DebugConsumer getDebugConsumer();

    @Override
    default boolean checksum(File file) throws Exception {
        String checksum = getChecksum();
        getDebugConsumer().consume("Checksum: " + checksum);
        if (checksum == null) {
            return false;
        }
        String currentChecksum = getCurrentChecksum(file);
        getDebugConsumer().consume("Current checksum: " + currentChecksum);
        return currentChecksum.equals(checksum);
    }
}
