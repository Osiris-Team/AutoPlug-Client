package com.osiris.autoplug.client.mc.profiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ProfileRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAllThreeTypesWithTheirOwnAssetsAndConfig() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        Profile client = repository.create("client", ProfileType.MODS, "1.20.4", "FABRIC", false);
        Profile plugins = repository.create("plugins", ProfileType.PLUGINS, "1.20.4", "PAPER", false);
        Profile server = repository.create("server", ProfileType.MODS_SERVER, "1.20.4", "NEOFORGE", false);
        assertEquals("mods", client.getAssetsDirectory().getFileName().toString());
        assertEquals("plugins", plugins.getAssetsDirectory().getFileName().toString());
        assertEquals("mods", server.getAssetsDirectory().getFileName().toString());
        for (Profile profile : repository.list()) {
            assertTrue(Files.isDirectory(profile.getAssetsDirectory()));
            assertTrue(Files.isDirectory(profile.getConfigDirectory()));
            assertEquals(temporaryDirectory.toRealPath().resolve("profiles").resolve(profile.getId()), profile.getDirectory());
        }
    }

    @Test
    void metadataRoundTripsInANewRepositoryInstance() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        repository.create("server-template", ProfileType.MODS_SERVER, "26w10a", "FORGE", true);
        Profile restored = new ProfileRepository(temporaryDirectory).find("server-template").orElseThrow(() -> new AssertionError("Missing profile"));
        assertEquals("server-template", restored.getId());
        assertEquals(ProfileType.MODS_SERVER, restored.getType());
        assertEquals("26w10a", restored.getGameVersion());
        assertEquals("FORGE", restored.getLoader());
        assertTrue(restored.isTemplate());
    }

    @Test
    void configurationsOfDifferentProfilesAreIsolated() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        Profile first = repository.create("first", ProfileType.MODS, "1.20.4", "FABRIC", false);
        Profile second = repository.create("second", ProfileType.MODS, "1.20.4", "FABRIC", false);
        Files.write(first.getConfigDirectory().resolve("settings.txt"), "first".getBytes(StandardCharsets.UTF_8));
        assertFalse(Files.exists(second.getConfigDirectory().resolve("settings.txt")));
        assertFalse(Files.exists(temporaryDirectory.resolve("config")));
        assertFalse(Files.exists(temporaryDirectory.resolve("mods")));
    }

    @Test
    void clonePreservesTypeVersionAndLoaderAndIsNoLongerATemplate() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        repository.create("base", ProfileType.PLUGINS, "1.21", "PURPUR", true);
        Profile clone = repository.cloneProfile("base", "copy");
        assertEquals("copy", clone.getId());
        assertEquals(ProfileType.PLUGINS, clone.getType());
        assertEquals("1.21", clone.getGameVersion());
        assertEquals("PURPUR", clone.getLoader());
        assertFalse(clone.isTemplate());
        assertFalse(new ProfileRepository(temporaryDirectory).find("copy").orElseThrow(() -> new AssertionError("Missing profile")).isTemplate());
        assertTrue(repository.find("base").orElseThrow(() -> new AssertionError("Missing profile")).isTemplate());
    }

    @Test
    void cloneCopiesNestedConfigAssetsAndDisabledFilesWithoutSharingMutableFiles() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        Profile source = repository.create("base", ProfileType.MODS, "1.20.4", "FABRIC", true);
        Path sourceConfig = Files.createDirectories(source.getConfigDirectory().resolve("nested")).resolve("settings.txt");
        Files.write(sourceConfig, "original".getBytes(StandardCharsets.UTF_8));
        Path sourceJar = source.getAssetsDirectory().resolve("example.jar");
        Files.write(sourceJar, new byte[]{1, 2, 3});
        Files.write(source.getAssetsDirectory().resolve("incompatible.jar.disabled"), new byte[]{4, 5});
        Profile clone = repository.cloneProfile("base", "copy");
        Path clonedConfig = clone.getConfigDirectory().resolve("nested/settings.txt");
        Path clonedJar = clone.getAssetsDirectory().resolve("example.jar");
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(clonedJar));
        assertArrayEquals(new byte[]{4, 5}, Files.readAllBytes(clone.getAssetsDirectory().resolve("incompatible.jar.disabled")));
        assertFalse(Files.isSameFile(sourceJar, clonedJar));
        Files.write(clonedConfig, "changed".getBytes(StandardCharsets.UTF_8));
        Files.write(clonedJar, new byte[]{9});
        assertEquals("original", new String(Files.readAllBytes(sourceConfig), StandardCharsets.UTF_8));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(sourceJar));
    }

    @Test
    void pluginCloneRetainsPluginDirectories() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        Profile source = repository.create("hub", ProfileType.PLUGINS, "1.21", "SPIGOT", false);
        Path pluginConfig = Files.createDirectories(source.getAssetsDirectory().resolve("Example")).resolve("config.yml");
        Files.write(pluginConfig, "enabled: true".getBytes(StandardCharsets.UTF_8));
        Profile clone = repository.cloneProfile("hub", "hub-copy");
        assertTrue(Files.isRegularFile(clone.getAssetsDirectory().resolve("Example/config.yml")));
        assertFalse(Files.exists(clone.getDirectory().resolve("mods")));
    }

    @Test
    void duplicateCreationDoesNotOverwriteExistingMetadataOrFiles() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        Profile first = repository.create("existing", ProfileType.MODS, "1.20.4", "FABRIC", false);
        Files.write(first.getConfigDirectory().resolve("keep.txt"), new byte[]{7});
        assertThrows(FileAlreadyExistsException.class,
                () -> repository.create("existing", ProfileType.PLUGINS, "1.21", "PAPER", true));
        assertEquals(ProfileType.MODS, repository.find("existing").orElseThrow(() -> new AssertionError("Missing profile")).getType());
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(first.getConfigDirectory().resolve("keep.txt")));
    }

    @Test
    void duplicateCloneAndMissingSourceLeaveRepositoryUnchanged() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        repository.create("base", ProfileType.MODS, "1.20.4", "FABRIC", false);
        repository.create("existing", ProfileType.PLUGINS, "1.21", "PAPER", false);
        assertThrows(FileAlreadyExistsException.class, () -> repository.cloneProfile("base", "existing"));
        assertThrows(IOException.class, () -> repository.cloneProfile("missing", "new"));
        assertEquals(2, repository.list().size());
    }

    @Test
    void rejectsInvalidIdsBeforeTouchingOtherPaths() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        for (String id : new String[]{"../outside", "/absolute", "", ".hidden", "two/parts", "two\\parts", null}) {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.create(id, ProfileType.MODS, "1.20.4", "FABRIC", false));
            assertThrows(IllegalArgumentException.class, () -> repository.find(id));
        }
        assertTrue(repository.list().isEmpty());
    }

    @Test
    void rejectsMissingVersionsAndLoadersWithoutLeavingPartialProfiles() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        assertThrows(IllegalArgumentException.class, () -> repository.create("a", ProfileType.MODS, "", "FABRIC", false));
        assertThrows(IllegalArgumentException.class, () -> repository.create("b", ProfileType.MODS, "1.20.4", "", false));
        assertTrue(repository.list().isEmpty());
    }

    @Test
    void listingIsDeterministicAndDoesNotExposeUnpublishedStagingDirectories() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        repository.create("z-last", ProfileType.MODS, "1.20.4", "FABRIC", false);
        repository.create("a-first", ProfileType.MODS, "1.20.4", "FABRIC", false);
        Files.createDirectory(temporaryDirectory.resolve("profiles/.create-interrupted"));
        assertEquals(List.of("a-first", "z-last"),
                repository.list().stream().map(Profile::getId).collect(Collectors.toList()));
    }

    @Test
    void doesNotTrustADirectoryEntryInMetadata() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        Profile profile = repository.create("local", ProfileType.MODS, "1.20.4", "FABRIC", false);
        changeMetadata(profile, "directory", temporaryDirectory.resolve("elsewhere").toString());
        assertEquals(profile.getDirectory(), repository.find("local").orElseThrow(() -> new AssertionError("Missing profile")).getDirectory());
        assertFalse(Files.exists(temporaryDirectory.resolve("elsewhere")));
    }

    @Test
    void corruptOrMismatchedMetadataIsReportedRatherThanInventingDefaults() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        Profile profile = repository.create("local", ProfileType.MODS, "1.20.4", "FABRIC", false);
        changeMetadata(profile, "id", "some-other-profile");
        assertThrows(IOException.class, () -> repository.find("local"));
        changeMetadata(profile, "id", "local");
        changeMetadata(profile, "isTemplate", "not-a-boolean");
        assertThrows(IOException.class, repository::list);
        changeMetadata(profile, "isTemplate", "false");
        changeMetadata(profile, "formatVersion", "999");
        assertThrows(IOException.class, () -> repository.find("local"));
    }

    @Test
    void linkedFilesCannotEscapeProfileIsolationAndFailedCloneIsNotPublished() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        Profile source = repository.create("base", ProfileType.MODS, "1.20.4", "FABRIC", true);
        Path external = temporaryDirectory.resolve("outside.txt");
        Files.write(external, new byte[]{4});
        Files.createSymbolicLink(source.getConfigDirectory().resolve("outside.txt"), external);
        assertThrows(IOException.class, () -> repository.cloneProfile("base", "copy"));
        assertFalse(repository.find("copy").isPresent());
        assertArrayEquals(new byte[]{4}, Files.readAllBytes(external));
        try (Stream<Path> entries = Files.list(temporaryDirectory.resolve("profiles"))) {
            assertEquals(List.of("base"), entries.map(path -> path.getFileName().toString()).collect(Collectors.toList()));
        }
    }

    @Test
    void rejectsLinkedProfileDirectory() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        Files.createSymbolicLink(temporaryDirectory.resolve("profiles/linked"), outside);
        assertThrows(IOException.class, () -> repository.find("linked"));
    }

    private void changeMetadata(Profile profile, String key, String value) throws IOException {
        Path file = profile.getDirectory().resolve("profile.properties");
        Properties metadata = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            metadata.load(input);
        }
        metadata.setProperty(key, value);
        try (OutputStream output = Files.newOutputStream(file)) {
            metadata.store(output, "fixture");
        }
    }
}
