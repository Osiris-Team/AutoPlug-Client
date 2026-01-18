package io.github.projectunified.mcserverupdater.api.checksum;

import io.github.projectunified.mcserverupdater.util.Utils;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;

public interface FileDigestChecksum extends SimpleChecksum {
    MessageDigest getMessageDigest() throws Exception;

    @Override
    default String getCurrentChecksum(File file) throws Exception {
        MessageDigest messageDigest = getMessageDigest();
        messageDigest.update(Files.readAllBytes(file.toPath()));
        byte[] checksumValue = messageDigest.digest();
        return Utils.toHex(checksumValue);
    }

    @Override
    default void setChecksum(File file) throws Exception {
        // IGNORED
    }
}
