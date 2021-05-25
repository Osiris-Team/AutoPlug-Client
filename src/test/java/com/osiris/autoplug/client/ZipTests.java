package com.osiris.autoplug.client;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

public class ZipTests {

    @Test
    void testForExistingFoldersInsideAJar() throws IOException {
        File file = new File(System.getProperty("user.dir") + "/src/test/Test.jar");
        if (!file.exists()) file.createNewFile();
        ZipFile zipFile = new ZipFile(file.toString());
        //zipFile.addFolder();
        FileHeader fileHeader = zipFile.getFileHeader("fileNameInZipToRemove");

        if (fileHeader == null) {
            // file does not exist
        }

        zipFile.removeFile(fileHeader);
    }
}
