package com.osiris.autoplug.client.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class JavaRuntimeManagerTest {
    @TempDir Path temporary;
    @Test void javaVersionParsingDoesNotConfuseJava8AndModernVersions() {
        assertEquals(8, JavaRuntimeManager.parseMajor("\"1.8.0_462\""));
        assertEquals(21, JavaRuntimeManager.parseMajor("\"21.0.12.1\""));
        assertEquals(25, JavaRuntimeManager.parseMajor("25-ea"));
    }
    @Test void explicitRuntimeMustMatchRequestedMajorWithoutMutatingOtherInstalls() throws Exception {
        Path home = temporary.resolve("jdk-17"); Files.createDirectories(home.resolve("bin"));
        Path java = home.resolve("bin").resolve(JavaRuntimeManager.executableName()); Files.write(java, new byte[]{0});
        Files.write(home.resolve("release"), "JAVA_VERSION=\"17.0.16\"".getBytes(StandardCharsets.UTF_8));
        JavaRuntimeManager manager = new JavaRuntimeManager(temporary.resolve("managed"));
        manager.setRuntime(17, java); assertEquals(java, manager.resolve(17, ignored -> { }));
        manager.setRuntime(21, java); assertThrows(IOException.class, () -> manager.resolve(21, ignored -> { }));
        assertTrue(Files.exists(home.resolve("release"))); assertFalse(Files.exists(temporary.resolve("managed")));
    }
    @Test void extractionRejectsArchiveTraversal() throws Exception {
        Path archive = temporary.resolve("runtime.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) { zip.putNextEntry(new ZipEntry("../outside")); zip.write(1); }
        assertThrows(IOException.class, () -> JavaRuntimeManager.extract(archive, temporary.resolve("runtime")));
        assertFalse(Files.exists(temporary.resolve("outside")));
    }
    @Test void neoForgeSelectionUsesCorrectGameVersionPrefixAndNumericOrder() throws Exception {
        assertEquals("21.1.", LoaderInstaller.neoPrefix("1.21.1"));
        assertEquals("20.2.", LoaderInstaller.neoPrefix("1.20.2"));
        assertEquals("26.3.", LoaderInstaller.neoPrefix("26.3"));
        assertTrue(LoaderInstaller.compareVersions("21.1.100", "21.1.99") > 0);
        assertTrue(LoaderInstaller.compareVersions("21.1.100", "21.1.100-beta") > 0);
    }
}
