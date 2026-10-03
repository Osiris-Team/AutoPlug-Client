package com.osiris.autoplug.client.launcher;

/** Harmless isolated child JVM used to verify the complete offline launch path. */
public class OfflineClientFixture {
    public static void main(String[] args) throws Exception {
        java.nio.file.Path directory = java.nio.file.Paths.get(System.getProperty("user.dir"));
        java.nio.file.Files.write(directory.resolve("fixture-working-directory.txt"), directory.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
