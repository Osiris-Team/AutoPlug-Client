package com.osiris.autoplug.client.launcher;

import org.apache.commons.compress.archivers.tar.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class ManagedJavaRuntimeTest {
    @TempDir Path temp;
    @Test void releaseFilesReadExactJava8Java17Java21Java25Majors() throws Exception {
        Path release = temp.resolve("release");
        for (String version : new String[]{"1.8.0_472","17.0.17","21.0.9","25.0.0"}) {
            Files.write(release,("JAVA_VERSION=\""+version+"\"\n").getBytes(StandardCharsets.UTF_8));
            assertEquals(version.startsWith("1.")?8:Integer.parseInt(version.split("\\.")[0]),ManagedJavaRuntime.javaMajorFromRelease(release));
        }
        assertEquals(-1,ManagedJavaRuntime.javaMajorFromRelease(temp.resolve("missing")));
    }
    @Test void currentJavaAndConfiguredOverrideAreCheckedWithoutNetworkOrGameLaunch() throws Exception {
        AtomicInteger requests = new AtomicInteger(); ManagedJavaRuntime runtime = new ManagedJavaRuntime(temp.resolve("runtime"),uri -> { requests.incrementAndGet(); throw new IOException("No network"); });
        int actual = Runtime.version().major(); Path java = Paths.get(System.getProperty("java.home"),"bin",LauncherPlatform.current().os.equals("windows")?"java.exe":"java");
        assertEquals(java,runtime.resolve(actual)); assertEquals(0,requests.get());
        runtime.configure(actual,java); assertEquals(java,runtime.resolve(actual));
        assertThrows(IOException.class,()->ManagedJavaRuntime.verifyMajor(java,actual==8?17:8));
        runtime.configure(21,temp.resolve("missing-java")); assertThrows(IOException.class,()->runtime.resolve(21)); assertEquals(0,requests.get());
    }
    static void entry(TarArchiveOutputStream tar, String name, byte[] bytes) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name); entry.setSize(bytes.length); tar.putArchiveEntry(entry); tar.write(bytes); tar.closeArchiveEntry();
    }
    @Test void runtimeTarExtractsFilesAndSafeLinksButNeverExternalLinkTargets() throws Exception {
        Path archive = temp.resolve("runtime.tar.gz");
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(Files.newOutputStream(archive)))) {
            entry(tar,"jdk/lib/real.txt","runtime".getBytes(StandardCharsets.UTF_8));
            TarArchiveEntry link = new TarArchiveEntry("jdk/lib/alias.txt",TarConstants.LF_SYMLINK); link.setLinkName("real.txt"); tar.putArchiveEntry(link); tar.closeArchiveEntry();
        }
        Path output = temp.resolve("safe"); ManagedJavaRuntime.extractTarGz(archive,output);
        assertEquals("runtime",new String(Files.readAllBytes(output.resolve("jdk/lib/alias.txt")),StandardCharsets.UTF_8));
        Path malicious = temp.resolve("unsafe.tar.gz");
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(Files.newOutputStream(malicious)))) {
            TarArchiveEntry link = new TarArchiveEntry("jdk/link",TarConstants.LF_SYMLINK); link.setLinkName("../../outside"); tar.putArchiveEntry(link); tar.closeArchiveEntry();
        }
        assertThrows(IOException.class,()->ManagedJavaRuntime.extractTarGz(malicious,temp.resolve("unsafe"))); assertFalse(Files.exists(temp.resolve("outside")));
    }
    @Test void incompleteStagingRuntimeCannotBePickedAsAnInstalledRuntime() throws Exception {
        Path root = temp.resolve("17"); Path staging = root.resolve(".staging-incomplete/jdk"); Files.createDirectories(staging.resolve("bin"));
        Files.write(staging.resolve("release"),"JAVA_VERSION=\"17.0.17\"".getBytes(StandardCharsets.UTF_8)); Files.write(staging.resolve("bin/java"),new byte[]{0});
        assertNull(ManagedJavaRuntime.findExecutable(root,17,"java"));
    }
}
