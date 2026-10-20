package com.osiris.autoplug.client.profiles;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class FabricDefaultProfileTest {
    @TempDir Path temporary;

    @Test void bootstrapIsVersionSpecificAndPreservesRenamedCustomizedDefault() throws Exception {
        ProfileStore store = store(); Profile first = store.ensureFabricDefault("1.21.1");
        first.name = "My utilities"; store.save(first);
        Files.write(first.getDirectory().resolve("options.txt"), "fullscreen:true".getBytes(StandardCharsets.UTF_8));
        assertEquals(first.id, store.ensureFabricDefault("1.21.1").id);
        Profile other = store.ensureFabricDefault("1.20.1"); assertNotEquals(first.id, other.id);
        assertEquals(2, store.list().size()); assertEquals("My utilities", store.get(first.id).name);
        assertEquals("fullscreen:true", new String(Files.readAllBytes(first.getDirectory().resolve("options.txt")), StandardCharsets.UTF_8));
        assertEquals("FABRIC", other.loader); assertEquals(ProfileType.MODS, other.type);
        assertFalse(first.builtinPresetInstalled);
    }

    @Test void exactClientPackIncludesOnlyRequiredDependenciesAndInstallsOnce() throws Exception {
        ProfileStore store = store(); Profile profile = store.ensureFabricDefault("1.21.1"); Fixture fixture = new Fixture();
        fixture.add("placeholder", "optional", "optional", "*");
        fixture.dependency("modmenu", "placeholder", "placeholder-release", "required");
        fixture.dependency("entityculling", "fabric-api", null, "required");
        fixture.dependency("placeholder", "fabric-api", null, "required");
        fixture.dependency("fabric-api", "placeholder", null, "required"); // Real cycle shape; each project must be visited once.
        fixture.dependency("modmenu", "unwanted-optional", null, "optional");
        fixture.dependency("modmenu", "unwanted-embedded", null, "embedded");
        prepare(store, profile, fixture);
        assertEquals(6, fixture.downloads.size()); assertEquals(6, new HashSet<>(fixture.downloads).size());
        Profile installed = store.get(profile.id); assertTrue(installed.builtinPresetInstalled);
        ProfileCollection collection = new ProfileCollection().scan(installed);
        assertEquals(6, collection.entries.size());
        assertTrue(collection.entries.stream().anyMatch(e -> "placeholder".equals(e.modrinthId)));
        assertTrue(collection.entries.stream().allMatch(e -> e.modrinthId != null && e.version != null));
        Files.delete(installed.getDirectory().resolve("mods/sodium.jar")); // Deliberate user customization must not be re-seeded.
        int metadataLookups = fixture.requestedGames.size();
        prepare(store, installed, fixture);
        assertEquals(6, fixture.downloads.size()); assertFalse(Files.exists(installed.getDirectory().resolve("mods/sodium.jar")));
        assertEquals(metadataLookups, fixture.requestedGames.size(), "Installed presets must launch offline without provider lookups");
        assertTrue(fixture.requestedGames.stream().allMatch("1.21.1"::equals));
    }

    @Test void noExactStableReleaseOrPinnedWrongGameCannotProducePartialPack() throws Exception {
        for (boolean pinned : new boolean[]{false, true}) {
            ProfileStore store = new ProfileStore(temporary.resolve("profiles-" + pinned));
            Profile profile = store.ensureFabricDefault("1.21.1"); Fixture fixture = new Fixture();
            if (pinned) {
                fixture.add("library", "optional", "optional", "*");
                fixture.releases.get("library").add("game_versions", array("1.20.1"));
                fixture.dependency("modmenu", "library", "library-release", "required");
            } else {
                fixture.releases.get("sodium").add("game_versions", array("1.21.2"));
            }
            IOException error = assertThrows(IOException.class, () -> prepare(store, profile, fixture));
            assertTrue(error.getMessage().contains("exact Minecraft 1.21.1"));
            assertTrue(fixture.downloads.isEmpty()); assertEmptyMods(profile); assertFalse(store.get(profile.id).builtinPresetInstalled);
        }
    }

    @Test void serverRequiredDependencyAndServerOnlyJarAreRejected() throws Exception {
        for (boolean inMetadata : new boolean[]{true, false}) {
            ProfileStore store = new ProfileStore(temporary.resolve("profiles-" + inMetadata));
            Profile profile = store.ensureFabricDefault("1.21.1"); Fixture fixture = new Fixture();
            fixture.add("library", "optional", inMetadata ? "required" : "optional", inMetadata ? "*" : "server");
            fixture.dependency("modmenu", "library", null, "required");
            assertThrows(IOException.class, () -> prepare(store, profile, fixture));
            assertEmptyMods(profile); assertFalse(store.get(profile.id).builtinPresetInstalled);
            if (inMetadata) assertTrue(fixture.downloads.isEmpty());
        }
    }

    @Test void interruptedDownloadAndFailedCommitLeaveNoInstalledSubsetAndCanRetry() throws Exception {
        AtomicBoolean failCommit = new AtomicBoolean();
        ProfileStore store = new ProfileStore(temporary.resolve("profiles")) {
            @Override public void save(Profile profile) throws IOException { if (failCommit.get()) throw new IOException("fixture metadata failure"); super.save(profile); }
        };
        Profile profile = store.ensureFabricDefault("1.21.1"); Fixture fixture = new Fixture();
        fixture.failAfter = 2;
        assertThrows(IOException.class, () -> prepare(store, profile, fixture));
        assertEmptyMods(profile); assertFalse(store.get(profile.id).builtinPresetInstalled);
        fixture.failAfter = -1; failCommit.set(true);
        assertThrows(IOException.class, () -> prepare(store, store.get(profile.id), fixture));
        assertEmptyMods(profile); assertFalse(store.get(profile.id).builtinPresetInstalled);
        assertFalse(Files.exists(profile.getDirectory().resolve("collection.json")));
        failCommit.set(false); prepare(store, store.get(profile.id), fixture);
        assertTrue(store.get(profile.id).builtinPresetInstalled);
        assertEquals(5, new ProfileCollection().scan(store.get(profile.id)).entries.size());
    }

    @Test void providerConflictAndExistingUserCollectionArePreserved() throws Exception {
        ProfileStore store = store(); Profile profile = store.ensureFabricDefault("1.21.1"); Fixture fixture = new Fixture();
        fixture.dependency("sodium", "entityculling", null, "incompatible");
        assertThrows(IOException.class, () -> prepare(store, profile, fixture));
        assertTrue(fixture.downloads.isEmpty()); assertEmptyMods(profile);
        fixture.releases.get("sodium").add("dependencies", new JsonArray());
        Path custom = profile.getDirectory().resolve("mods/custom.jar"); byte[] bytes = fixture.jars.get("sodium"); Files.write(custom, bytes);
        assertThrows(IOException.class, () -> prepare(store, profile, fixture));
        assertArrayEquals(bytes, Files.readAllBytes(custom)); assertFalse(store.get(profile.id).builtinPresetInstalled);
    }

    @Test void malformedJarStillAppearsInCollectionBeforeApplicationLoggerStarts() throws Exception {
        ProfileStore store = store(); Profile profile = store.ensureFabricDefault("1.21.1");
        Files.write(profile.getDirectory().resolve("mods/broken.jar"), "not a zip".getBytes(StandardCharsets.UTF_8));
        ProfileCollection collection = new ProfileCollection().scan(profile);
        assertEquals(1, collection.entries.size()); assertEquals("broken.jar", collection.entries.get(0).file);
    }

    @Test void emptyProviderReleaseListAndCorruptedDownloadNeverActivateSubset() throws Exception {
        for (boolean emptyProvider : new boolean[]{true, false}) {
            ProfileStore store = new ProfileStore(temporary.resolve("profiles-" + emptyProvider));
            Profile profile = store.ensureFabricDefault("1.21.1"); Fixture fixture = new Fixture();
            if (emptyProvider) fixture.emptyVersionsFor = "sodium";
            else fixture.jars.put("sodium", new byte[]{1, 2, 3});
            IOException failure = assertThrows(IOException.class, () -> prepare(store, profile, fixture));
            assertTrue(failure.getMessage().contains("Nothing will launch"));
            assertFalse(store.get(profile.id).builtinPresetInstalled); assertEmptyMods(profile);
            if (emptyProvider) assertTrue(fixture.downloads.isEmpty());
        }
    }

    private ProfileStore store() throws Exception { return new ProfileStore(temporary.resolve("profiles")); }
    private static void assertEmptyMods(Profile profile) throws Exception {
        try (java.util.stream.Stream<Path> files = Files.list(profile.getDirectory().resolve("mods"))) { assertEquals(0, files.count()); }
    }
    private static void prepare(ProfileStore store, Profile profile, Fixture fixture) throws Exception {
        try (ProfileLease ignored = new ProfileLease(profile.getDirectory())) {
            new FabricDefaultProfile(store, fixture, (release, path) -> {
                if (fixture.failAfter >= 0 && fixture.downloads.size() >= fixture.failAfter) throw new IOException("fixture connection lost");
                fixture.downloads.add(release.modrinthProjectId); Files.write(path, fixture.jars.get(release.modrinthProjectId));
            }).prepare(profile, ignoredMessage -> { });
        }
    }
    private static JsonArray array(String... values) { JsonArray array = new JsonArray(); for (String value : values) array.add(value); return array; }
    private static class Fixture implements FabricDefaultProfile.Catalog {
        final Map<String, JsonObject> projects = new LinkedHashMap<>(), releases = new LinkedHashMap<>();
        final Map<String, byte[]> jars = new HashMap<>();
        final List<String> downloads = new ArrayList<>(), requestedGames = new ArrayList<>();
        int failAfter = -1;
        String emptyVersionsFor;
        Fixture() throws Exception { for (String root : FabricDefaultProfile.ROOTS) add(root, root.equals("fabric-api") ? "optional" : "required", root.equals("fabric-api") ? "optional" : "unsupported", root.equals("fabric-api") ? "*" : "client"); }
        void add(String id, String client, String server, String environment) throws Exception {
            JsonObject project = new JsonObject(); project.addProperty("id", id); project.addProperty("title", id); project.addProperty("project_type", "mod");
            project.addProperty("client_side", client); project.addProperty("server_side", server); projects.put(id, project);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream jar = new ZipOutputStream(bytes)) {
                jar.putNextEntry(new ZipEntry("fabric.mod.json"));
                jar.write(("{\"schemaVersion\":1,\"id\":\"" + id.replace('-', '_') + "\",\"version\":\"1.0\",\"name\":\"" + id + "\",\"environment\":\"" + environment + "\"}").getBytes(StandardCharsets.UTF_8)); jar.closeEntry();
            }
            byte[] content = bytes.toByteArray(); jars.put(id, content);
            JsonObject version = new JsonObject(); version.addProperty("id", id + "-release"); version.addProperty("project_id", id);
            version.addProperty("version_number", "1.0+1.21.1"); version.addProperty("version_type", "release"); version.addProperty("status", "listed");
            version.addProperty("date_published", "2026-10-01T00:00:00Z"); version.add("game_versions", array("1.21.1")); version.add("loaders", array("fabric")); version.add("dependencies", new JsonArray());
            JsonObject file = new JsonObject(); file.addProperty("filename", id + ".jar"); file.addProperty("url", "https://cdn.modrinth.com/data/" + id + "/mod.jar"); file.addProperty("primary", true); file.addProperty("size", content.length);
            StringBuilder hash = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-512").digest(content)) hash.append(String.format("%02x", b & 255));
            JsonObject hashes = new JsonObject(); hashes.addProperty("sha512", hash.toString()); file.add("hashes", hashes);
            JsonArray files = new JsonArray(); files.add(file); version.add("files", files); releases.put(id, version);
        }
        void dependency(String from, String project, String version, String type) {
            JsonObject dependency = new JsonObject(); dependency.addProperty("project_id", project); dependency.addProperty("version_id", version); dependency.addProperty("dependency_type", type);
            releases.get(from).getAsJsonArray("dependencies").add(dependency);
        }
        public JsonObject project(String id) throws Exception { if (!projects.containsKey(id)) throw new IOException("Unexpected dependency " + id); return projects.get(id); }
        public JsonArray versions(String id, String gameVersion) { requestedGames.add(gameVersion); JsonArray result = new JsonArray(); if (!id.equals(emptyVersionsFor)) result.add(releases.get(id)); return result; }
        public JsonObject version(String id) throws Exception { for (JsonObject version : releases.values()) if (id.equals(version.get("id").getAsString())) return version; throw new IOException("Unknown version " + id); }
    }
}
