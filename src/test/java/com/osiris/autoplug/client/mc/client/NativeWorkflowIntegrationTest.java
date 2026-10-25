package com.osiris.autoplug.client.mc.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osiris.autoplug.client.mc.launch.Cancellation;
import com.osiris.autoplug.client.mc.launch.LaunchTransport;
import com.osiris.autoplug.client.mc.profiles.Profile;
import com.osiris.autoplug.client.mc.profiles.ProfileRepository;
import com.osiris.autoplug.client.mc.profiles.ProfileType;
import com.osiris.autoplug.client.mc.update.ProfileUpdateService;
import com.osiris.autoplug.client.mc.worlds.World;
import com.osiris.autoplug.client.mc.worlds.WorldSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic integration only: no Minecraft, authentication, router or network access. */
class NativeWorkflowIntegrationTest {
    @TempDir Path temporary;
    private static final byte[] ORIGINAL_MOD = syntheticJar("synthetic original mod");
    private static final byte[] UPGRADED_MOD = syntheticJar("synthetic upgraded mod");
    private static final ProfileUpdateService.Engine ENGINE = new ProfileUpdateService.Engine() {
        public ProfileUpdateService.Decision resolve(Profile profile, Path jar, String version) throws IOException {
            assertEquals("1.20.4", version);
            return jar.getFileName().toString().equals("unsupported.jar")
                    ? ProfileUpdateService.Decision.incompatible("Fixture has no compatible release")
                    : ProfileUpdateService.Decision.replace("https://fixture.invalid/upgraded.jar", hash("SHA-256", UPGRADED_MOD), "Synthetic compatible replacement");
        }
        public void download(ProfileUpdateService.Decision decision, Path target) throws IOException {
            assertEquals("https://fixture.invalid/upgraded.jar", decision.getDownloadUrl()); Files.write(target, UPGRADED_MOD);
        }
    };

    @Test @EnabledOnOs({OS.LINUX, OS.MAC})
    void templateMigrationActualSyntheticJvmAndCleanupUseTheSameContext() throws Exception {
        Path data = data(); SyntheticTransport transport = new SyntheticTransport(17);
        Path fakeHome = Files.createDirectories(data.resolve("synthetic-jre/bin")).getParent();
        Path arguments = data.resolve("synthetic-arguments.txt");
        // This executable only records synthetic arguments and becomes a short-lived sleep.
        // exec avoids a detached child process. It never interprets or runs a game/JAR.
        String script = "#!/bin/sh\nprintf '%s\\n' \"$@\" > " + quote(arguments.toString()) + "\nexec /bin/sleep 2\n";
        Path executable = fakeHome.resolve("bin/java"); Files.write(executable, script.getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        Files.write(fakeHome.resolve("release"), ("JAVA_VERSION=\"17.0.1\"\nOS_ARCH=\"" + System.getProperty("os.arch") + "\"\n").getBytes(StandardCharsets.UTF_8));
        ClientSettings settings = new ClientSettings(data); settings.set("javaHomes", fakeHome.toString()); settings.save();
        try (NativeClientContext context = new NativeClientContext(data, data, transport, ENGINE)) {
            Profile template = context.profiles().create("template", ProfileType.MODS, "1.20.2", "VANILLA", true);
            Files.write(template.getAssetsDirectory().resolve("upgrade.jar"), ORIGINAL_MOD);
            Files.write(template.getAssetsDirectory().resolve("unsupported.jar"), syntheticJar("synthetic unsupported mod"));
            Files.write(template.getConfigDirectory().resolve("settings.txt"), new byte[]{3});
            ProfileUpdateService.Plan plan = context.updater().planClone("template", "playable", "1.20.4");
            assertEquals(1, plan.getDisabledAssets().size());
            assertThrows(IOException.class, () -> context.updater().apply(plan, false));
            Profile playable = context.updater().apply(plan, true);
            assertFalse(playable.isTemplate()); assertEquals("1.20.4", playable.getGameVersion());
            assertEquals("1.20.2", context.profiles().require("template").getGameVersion());
            assertArrayEquals(ORIGINAL_MOD, Files.readAllBytes(template.getAssetsDirectory().resolve("upgrade.jar")));
            assertArrayEquals(UPGRADED_MOD, Files.readAllBytes(playable.getAssetsDirectory().resolve("upgrade.jar")));
            assertTrue(Files.isRegularFile(playable.getAssetsDirectory().resolve("unsupported.jar.disabled")));
            assertArrayEquals(new byte[]{3}, Files.readAllBytes(playable.getConfigDirectory().resolve("settings.txt")));

            Process process = context.launch("playable", "example.invalid:25577", new Cancellation());
            assertTrue(context.profiles().isInUse("playable"));
            assertThrows(IOException.class, () -> context.profiles().delete("playable"));
            await(() -> Files.isRegularFile(arguments));
            List<String> args = Files.readAllLines(arguments, StandardCharsets.UTF_8);
            assertEquals(playable.getDirectory().toString(), following(args, "--gameDir"));
            assertEquals("1.20.4", following(args, "--version"));
            assertEquals("example.invalid", following(args, "--server")); assertEquals("25577", following(args, "--port"));
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            await(() -> !context.profiles().isInUse("playable")); assertNoNatives(playable);
            int downloads = transport.downloads;
            Files.delete(arguments);
            Process second = context.launch("playable", null, new Cancellation()); await(() -> Files.isRegularFile(arguments));
            context.stopClient("playable"); assertTrue(second.waitFor(5, TimeUnit.SECONDS));
            await(() -> !context.profiles().isInUse("playable")); assertNoNatives(playable);
            assertEquals(downloads, transport.downloads, "Verified synthetic artifacts are reused from the shared cache");
        }
    }

    @Test void failedWorldStartRetainsServerLeaseUntilContextStopCanActuallyFinish() throws Exception {
        Path data = data(); ResistantProcess server = new ResistantProcess();
        AtomicReference<WorldSessionManager> manager = new AtomicReference<>();
        try (NativeClientContext context = new NativeClientContext(data, data, new SyntheticTransport(runtimeMajor()), ENGINE,
                (launcher, version, directory, session, host, port, cancel) -> { throw new AssertionError("No client before readiness"); },
                repository -> { WorldSessionManager sessions = new WorldSessionManager(repository, null, (command, directory) -> server,
                        (host, port) -> false, () -> 25577, new WorldSessionManager.Options(2, 1, 1)); manager.set(sessions); return sessions; })) {
            World world = createWorld(context); Path jar = Files.write(data.resolve("synthetic-server.jar"), new byte[]{1});
            assertThrows(IOException.class, () -> context.playWorld("island", "client", jar, false, new Cancellation()));
            assertTrue(context.profiles().isInUse("server")); assertFalse(context.profiles().isInUse("client"));
            assertNotNull(context.activeWorld("island")); assertTrue(context.activeWorld("island").isStopping());
            assertThrows(IOException.class, () -> context.profiles().delete("server"));
            server.allowExit.set(true); context.stopWorld("island");
            assertFalse(context.profiles().isInUse("server")); assertNull(context.activeWorld("island")); assertFalse(server.isAlive());
        } finally { server.finish(); if (manager.get() != null) manager.get().close(); }
    }

    @Test void contextStopRetriesFailedRouterCleanupWithoutReleasingServerLeaseEarly() throws Exception {
        Path data = data(); ResistantProcess server = new ResistantProcess(), client = new ResistantProcess();
        server.allowExit.set(true); client.allowExit.set(true); AtomicBoolean refuseRemoval = new AtomicBoolean(true);
        try (NativeClientContext context = new NativeClientContext(data, data, new SyntheticTransport(runtimeMajor()), ENGINE,
                (launcher, version, directory, session, host, port, cancel) -> new NativeClientContext.ClientLaunch() {
                    public Process start() { return client; }
                    public void close() { client.finish(); }
                }, repository -> new WorldSessionManager(repository, port -> new com.osiris.autoplug.client.mc.sharing.PortSharingService.Lease() {
                    public String getJoinAddress() { return "fixture.invalid:25577"; }
                    public void close() throws IOException { if (refuseRemoval.get()) throw new IOException("Synthetic router refused cleanup"); }
                }, (command, directory) -> server, (host, port) -> true, () -> 25577, new WorldSessionManager.Options(2, 1, 1)))) {
            createWorld(context); Path jar = Files.write(data.resolve("synthetic-server.jar"), new byte[]{1});
            context.playWorld("island", "client", jar, true, new Cancellation());
            assertThrows(IOException.class, () -> context.stopWorld("island"));
            assertNotNull(context.activeWorld("island")); assertTrue(context.profiles().isInUse("server"));
            assertFalse(context.activeWorld("island").isClosed());
            refuseRemoval.set(false); context.stopWorld("island");
            assertNull(context.activeWorld("island")); assertFalse(context.profiles().isInUse("server"));
            await(() -> !context.profiles().isInUse("client"));
        } finally { refuseRemoval.set(false); server.finish(); client.finish(); }
    }

    @Test void cancelledWorldClientThatResistsCleanupKeepsItsTransferredLease() throws Exception {
        Path data = data(); ResistantProcess server = new ResistantProcess(), client = new ResistantProcess();
        server.allowExit.set(true); AtomicBoolean refuse = new AtomicBoolean(true);
        try (NativeClientContext context = new NativeClientContext(data, data, new SyntheticTransport(runtimeMajor()), ENGINE,
                (launcher, version, directory, session, host, port, cancel) -> new NativeClientContext.ClientLaunch() {
                    public Process start() { cancel.cancel(); return client; }
                    public void close() throws IOException { if (refuse.get()) throw new IOException("Synthetic client refuses cleanup"); client.finish(); }
                }, repository -> new WorldSessionManager(repository, null, (command, directory) -> server,
                        (host, port) -> true, () -> 25577, new WorldSessionManager.Options(2, 1, 1)))) {
            createWorld(context); Path jar = Files.write(data.resolve("synthetic-server.jar"), new byte[]{1});
            assertThrows(IOException.class, () -> context.playWorld("island", "client", jar, false, new Cancellation()));
            assertFalse(context.profiles().isInUse("server")); assertTrue(context.profiles().isInUse("client"));
            assertTrue(client.isAlive()); assertThrows(IOException.class, () -> context.profiles().delete("client"));
            refuse.set(false); context.stopClient("client"); await(() -> !context.profiles().isInUse("client"));
            assertFalse(client.isAlive());
        } finally { refuse.set(false); server.finish(); client.finish(); }
    }

    private Path data() throws IOException {
        Path data = Files.createTempDirectory(temporary.toRealPath(), "workflow-").toRealPath();
        ClientSettings settings = new ClientSettings(data); settings.set("cacheDirectory", data.resolve("cache").toString());
        settings.set("offlineFixtureMode", "true"); settings.set("username", "Fixture"); settings.save(); return data;
    }
    private World createWorld(NativeClientContext context) throws IOException {
        context.profiles().create("server", ProfileType.PLUGINS, "1.20.4", "PAPER", false);
        context.profiles().create("client", ProfileType.MODS, "1.20.4", "VANILLA", false);
        World world = context.worlds().create("island", "server");
        // Explicit acceptance exists only in this synthetic fixture, never a production side effect.
        Files.write(world.getDirectory().resolve("eula.txt"), "eula=true\n".getBytes(StandardCharsets.UTF_8)); return world;
    }
    private static int runtimeMajor() { return Integer.parseInt(System.getProperty("java.specification.version")); }
    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    private static String following(List<String> values, String flag) { int index = values.indexOf(flag); assertTrue(index >= 0); return values.get(index + 1); }
    private static void assertNoNatives(Profile profile) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.list(profile.getDirectory().resolve(".autoplug"))) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith("natives-")));
        }
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue(condition.getAsBoolean());
    }
    private static String hash(String algorithm, byte[] bytes) throws IOException {
        try {
            StringBuilder text = new StringBuilder();
            for (byte value : MessageDigest.getInstance(algorithm).digest(bytes)) text.append(String.format("%02x", value & 255));
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IOException(error); }
    }
    private static byte[] syntheticJar(String content) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("synthetic-fixture.txt"); entry.setTime(0);
                zip.putNextEntry(entry); zip.write(content.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
            return bytes.toByteArray();
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }

    private static final class SyntheticTransport implements LaunchTransport {
        final Map<String, byte[]> bytes = new HashMap<>(); final JsonObject manifest = new JsonObject(); int downloads;
        SyntheticTransport(int javaMajor) throws IOException {
            JsonObject version = JsonParser.parseString("{\"id\":\"1.20.4\",\"type\":\"release\",\"mainClass\":\"fixture.Main\",\"libraries\":[],\"arguments\":{\"jvm\":[\"-Djava.library.path=${natives_directory}\",\"-cp\",\"${classpath}\"],\"game\":[\"--username\",\"${auth_player_name}\",\"--gameDir\",\"${game_directory}\",\"--version\",\"${version_name}\"]}}").getAsJsonObject();
            JsonObject runtime = new JsonObject(); runtime.addProperty("majorVersion", javaMajor); version.add("javaVersion", runtime);
            JsonObject artifacts = new JsonObject(); artifacts.add("client", artifact("https://fixture.invalid/client.jar", new byte[]{1, 2, 3})); version.add("downloads", artifacts);
            JsonObject assets = artifact("https://fixture.invalid/assets.json", "{\"objects\":{}}".getBytes(StandardCharsets.UTF_8)); assets.addProperty("id", "fixture-assets"); version.add("assetIndex", assets);
            JsonObject descriptor = artifact("https://fixture.invalid/version.json", version.toString().getBytes(StandardCharsets.UTF_8)); descriptor.addProperty("id", "1.20.4");
            JsonArray versions = new JsonArray(); versions.add(descriptor); manifest.add("versions", versions);
        }
        private JsonObject artifact(String url, byte[] data) throws IOException {
            bytes.put(url, data); JsonObject descriptor = new JsonObject(); descriptor.addProperty("url", url);
            descriptor.addProperty("sha1", hash("SHA-1", data)); descriptor.addProperty("size", data.length); return descriptor;
        }
        public JsonObject json(String method, URI uri, Map<String, String> headers, byte[] body, Cancellation cancel) throws IOException {
            cancel.check(); if (!uri.toString().equals("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")) throw new AssertionError("No fixture JSON for " + uri);
            return manifest.deepCopy();
        }
        public void download(URI uri, Path target, Cancellation cancel) throws IOException {
            cancel.check(); byte[] body = bytes.get(uri.toString()); if (body == null) throw new AssertionError("No fixture bytes for " + uri);
            downloads++; Files.write(target, body);
        }
    }
    private static final class ResistantProcess extends Process {
        final CountDownLatch exited = new CountDownLatch(1); final AtomicBoolean allowExit = new AtomicBoolean();
        public OutputStream getOutputStream() { return new OutputStream() { public void write(int value) { } public void flush() { if (allowExit.get()) finish(); } }; }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        public int waitFor() throws InterruptedException { exited.await(); return 0; }
        public boolean waitFor(long time, TimeUnit unit) { return !isAlive(); }
        public int exitValue() { if (isAlive()) throw new IllegalThreadStateException(); return 0; }
        public void destroy() { if (allowExit.get()) finish(); }
        public Process destroyForcibly() { destroy(); return this; }
        public boolean isAlive() { return exited.getCount() != 0; }
        void finish() { exited.countDown(); }
    }
}
