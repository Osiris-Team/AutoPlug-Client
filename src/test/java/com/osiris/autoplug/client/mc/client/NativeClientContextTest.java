package com.osiris.autoplug.client.mc.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osiris.autoplug.client.mc.launch.Cancellation;
import com.osiris.autoplug.client.mc.launch.LaunchTransport;
import com.osiris.autoplug.client.mc.profiles.Profile;
import com.osiris.autoplug.client.mc.profiles.ProfileRepository;
import com.osiris.autoplug.client.mc.profiles.ProfileType;
import com.osiris.autoplug.client.mc.update.ProfileUpdateService;
import com.osiris.autoplug.client.mc.worlds.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NativeClientContextTest {
    @TempDir Path temporary;
    private static final ProfileUpdateService.Engine ENGINE = new ProfileUpdateService.Engine() {
        public ProfileUpdateService.Decision resolve(Profile profile, Path jar, String version) { return ProfileUpdateService.Decision.keep("fixture"); }
        public void download(ProfileUpdateService.Decision decision, Path target) { throw new AssertionError("No updater network expected"); }
    };
    private static final LaunchTransport NO_NETWORK = new LaunchTransport() {
        public JsonObject json(String method, URI uri, Map<String, String> headers, byte[] body, Cancellation cancellation) { throw new AssertionError("No JSON request expected"); }
        public void download(URI uri, Path target, Cancellation cancellation) { throw new AssertionError("No download expected"); }
    };

    @Test void startFailureClosesPreparedNativesAndReleasesTheProfileLease() throws Exception {
        AtomicReference<FakeLaunch> handle = new AtomicReference<>();
        try (NativeClientContext context = context(NO_NETWORK, (launcher, version, directory, session, host, port, cancellation) -> {
            FakeLaunch fake = new FakeLaunch(directory, cancellation); fake.failStart = true; handle.set(fake); return fake;
        })) {
            context.profiles().create("client", ProfileType.MODS, "1.20.4", "VANILLA", false);
            assertThrows(IOException.class, () -> context.launch("client", "example.invalid", new Cancellation()));
            assertTrue(handle.get().closed); assertFalse(Files.exists(handle.get().natives));
            assertFalse(context.profiles().isInUse("client"));
        }
    }

    @Test void processExitClosesNativesAndRetainsItsLeaseUntilExit() throws Exception {
        AtomicReference<FakeLaunch> handle = new AtomicReference<>();
        try (NativeClientContext context = context(NO_NETWORK, (launcher, version, directory, session, host, port, cancellation) -> {
            FakeLaunch fake = new FakeLaunch(directory, cancellation); handle.set(fake); return fake;
        })) {
            context.profiles().create("client", ProfileType.MODS, "1.20.4", "VANILLA", false);
            context.launch("client", null, new Cancellation()); assertTrue(context.profiles().isInUse("client"));
            assertThrows(IOException.class, () -> context.profiles().delete("client"));
            handle.get().process.finish(); await(() -> !context.profiles().isInUse("client"));
            assertTrue(handle.get().closed); assertFalse(Files.exists(handle.get().natives));
        }
    }

    @Test void closingContextDuringPreparationCancelsAndClosesALatePreparedHandle() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<FakeLaunch> handle = new AtomicReference<>();
        NativeClientContext context = context(NO_NETWORK, (launcher, version, directory, session, host, port, cancellation) -> {
            entered.countDown(); waitFixture(release);
            FakeLaunch fake = new FakeLaunch(directory, cancellation); handle.set(fake); return fake;
        });
        context.profiles().create("client", ProfileType.MODS, "1.20.4", "VANILLA", false);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> task = executor.submit(() -> assertThrows(IOException.class, () -> context.launch("client", null, new Cancellation())));
            assertTrue(entered.await(1, TimeUnit.SECONDS)); context.close(); release.countDown(); task.get(1, TimeUnit.SECONDS);
            assertTrue(handle.get().closed); assertEquals(0, handle.get().starts); assertFalse(context.profiles().isInUse("client"));
            assertThrows(IOException.class, () -> context.launch("client", null, new Cancellation()));
        } finally { release.countDown(); executor.shutdownNow(); context.close(); }
    }

    @Test void stopClientCancelsPreparationAndDuplicateStartDoesNotDisturbTheFirstScope() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1); Cancellation first = new Cancellation();
        try (NativeClientContext context = context(NO_NETWORK, (launcher, version, directory, session, host, port, cancellation) -> {
            entered.countDown(); waitFixture(release); return new FakeLaunch(directory, cancellation);
        })) {
            context.profiles().create("client", ProfileType.MODS, "1.20.4", "VANILLA", false);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<?> task = executor.submit(() -> assertThrows(IOException.class, () -> context.launch("client", null, first)));
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                assertThrows(IOException.class, () -> context.launch("client", null, new Cancellation())); assertFalse(first.isCancelled());
                context.stopClient("client"); assertTrue(first.isCancelled()); release.countDown(); task.get(1, TimeUnit.SECONDS);
                assertFalse(context.profiles().isInUse("client"));
            } finally { release.countDown(); executor.shutdownNow(); }
        }
    }

    @Test void recordLoaderCannotChangeAnActiveProfileAndPersistsWhenIdle() throws Exception {
        try (NativeClientContext context = context(NO_NETWORK, (launcher, version, directory, session, host, port, cancellation) -> new FakeLaunch(directory, cancellation))) {
            Profile profile = context.profiles().create("client", ProfileType.MODS, "1.20.4", "FABRIC", false);
            try (ProfileRepository.UseLease ignored = context.profiles().acquireUse("client")) {
                assertThrows(IOException.class, () -> context.recordLoader("client", "fabric-fixture"));
                assertFalse(Files.exists(profile.getDirectory().resolve("loader.properties")));
            }
            context.recordLoader("client", "fabric-fixture");
            assertEquals("fabric-fixture", new ClientStorage().read(profile.getDirectory().resolve("loader.properties")).getProperty("versionId"));
        }
    }

    @Test void cancellationAfterStartKeepsLeaseWhenProcessCleanupFailsUntilStopRetry() throws Exception {
        FakeProcess process = new FakeProcess(); java.util.concurrent.atomic.AtomicBoolean refuse = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (NativeClientContext context = context(NO_NETWORK, (launcher, version, directory, session, host, port, cancellation) -> new NativeClientContext.ClientLaunch() {
            public Process start() { cancellation.cancel(); return process; }
            public void close() throws IOException { if (refuse.get()) throw new IOException("Synthetic process refuses cleanup"); process.finish(); }
        })) {
            context.profiles().create("client", ProfileType.MODS, "1.20.4", "VANILLA", false);
            assertThrows(IOException.class, () -> context.launch("client", null, new Cancellation()));
            assertTrue(process.isAlive()); assertTrue(context.profiles().isInUse("client"));
            assertThrows(IOException.class, () -> context.profiles().delete("client"));
            assertThrows(IOException.class, () -> context.stopClient("client"));
            refuse.set(false); context.stopClient("client"); await(() -> !context.profiles().isInUse("client"));
            assertFalse(process.isAlive());
        } finally { refuse.set(false); process.finish(); }
    }

    @Test void loaderInstallKeepsLeaseUntilMarkerAndSelectsStableOfficialMetadata() throws Exception {
        AtomicReference<NativeClientContext> owner = new AtomicReference<>(); AtomicInteger requests = new AtomicInteger();
        LaunchTransport transport = new LaunchTransport() {
            public void download(URI uri, Path target, Cancellation cancellation) throws IOException {
                requests.incrementAndGet(); assertTrue(owner.get().profiles().isInUse("fabric"));
                assertTrue(uri.toString().endsWith("/1.20.4"));
                Files.write(target, "[{\"loader\":{\"version\":\"beta-fixture\",\"stable\":false}},{\"loader\":{\"version\":\"0.15.0\",\"stable\":true}}]".getBytes(StandardCharsets.UTF_8));
            }
            public JsonObject json(String method, URI uri, Map<String, String> headers, byte[] body, Cancellation cancellation) {
                requests.incrementAndGet(); assertTrue(owner.get().profiles().isInUse("fabric"));
                assertTrue(uri.toString().contains("/0.15.0/profile/json"));
                return JsonParser.parseString("{\"id\":\"fabric-fixture\",\"inheritsFrom\":\"1.20.4\",\"mainClass\":\"fixture.Main\",\"libraries\":[]}").getAsJsonObject();
            }
        };
        try (NativeClientContext context = context(transport, (launcher, version, directory, session, host, port, cancellation) -> new FakeLaunch(directory, cancellation))) {
            owner.set(context); Profile profile = context.profiles().create("fabric", ProfileType.MODS, "1.20.4", "FABRIC", false);
            assertEquals("fabric-fixture", context.installLoader("fabric", null, new Cancellation()));
            assertFalse(context.profiles().isInUse("fabric")); assertEquals(2, requests.get());
            assertEquals("fabric-fixture", new ClientStorage().read(profile.getDirectory().resolve("loader.properties")).getProperty("versionId"));
            assertEquals("fabric-fixture", context.ensureLoader("fabric", new Cancellation()));
            assertEquals(2, requests.get(), "A coherent local import must not rerun installation");
            Path imported = context.getDataRoot().resolve("cache/launcher/versions/fabric-fixture/fabric-fixture.json");
            Files.write(imported, "{\"id\":\"wrong-id\"}".getBytes(StandardCharsets.UTF_8));
            assertEquals("fabric-fixture", context.ensureLoader("fabric", new Cancellation())); assertEquals(4, requests.get());
            Files.delete(imported.resolveSibling("autoplug-loader-profile"));
            assertEquals("fabric-fixture", context.ensureLoader("fabric", new Cancellation())); assertEquals(6, requests.get());
            java.util.Properties stale = new ClientStorage().read(profile.getDirectory().resolve("loader.properties"));
            stale.setProperty("game", "1.20.2"); new ClientStorage().write(profile.getDirectory().resolve("loader.properties"), stale);
            assertEquals("fabric-fixture", context.ensureLoader("fabric", new Cancellation())); assertEquals(8, requests.get());
            assertEquals("fabric-fixture", context.installLoader("fabric", "0.15.0", new Cancellation()));
            assertEquals(9, requests.get(), "Explicit installation remains an intentional reinstall");
            try (java.util.stream.Stream<Path> files = Files.list(context.getDataRoot())) { assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".loader-list-"))); }
        }
    }

    @Test void worldLaunchConfigPersistsAndRejectsVersionLoaderAndJarMismatches() throws Exception {
        try (NativeClientContext context = context(NO_NETWORK, (launcher, version, directory, session, host, port, cancellation) -> new FakeLaunch(directory, cancellation))) {
            context.profiles().create("server", ProfileType.MODS_SERVER, "1.20.4", "FABRIC", false);
            context.profiles().create("matching", ProfileType.MODS, "1.20.4", "FABRIC", false);
            context.profiles().create("wrong-loader", ProfileType.MODS, "1.20.4", "VANILLA", false);
            context.profiles().create("wrong-version", ProfileType.MODS, "1.20.2", "FABRIC", false);
            World world = context.worlds().create("island", "server"); Path jar = context.getDataRoot().resolve("synthetic-server.jar"); Files.write(jar, new byte[]{1});
            assertThrows(IOException.class, () -> context.configureWorld("island", "wrong-loader", jar));
            assertThrows(IOException.class, () -> context.configureWorld("island", "wrong-version", jar));
            context.configureWorld("island", "matching", jar);
            assertEquals("matching", context.worldConfig("island").getClientProfileId()); assertEquals(jar.toRealPath(), context.worldConfig("island").getServerJar());
            assertFalse(new ClientStorage().read(world.getDirectory().resolve("eula.txt")).getProperty("eula").equals("true"));
            assertThrows(IOException.class, () -> context.playWorld("island", false, new Cancellation()));
            assertFalse(context.profiles().isInUse("server")); assertFalse(context.profiles().isInUse("matching"));
            Files.delete(jar); assertThrows(IOException.class, () -> context.worldConfig("island"));
        }
    }

    @Test void cancelledLoaderInstallPublishesNoMarkerAndReleasesItsLease() throws Exception {
        Cancellation cancellation = new Cancellation();
        LaunchTransport cancelled = new LaunchTransport() {
            public void download(URI uri, Path target, Cancellation scope) throws IOException {
                Files.write(target, "[]".getBytes(StandardCharsets.UTF_8)); scope.cancel();
            }
            public JsonObject json(String method, URI uri, Map<String, String> headers, byte[] body, Cancellation scope) {
                throw new AssertionError("Cancelled list must not be used to install a loader");
            }
        };
        try (NativeClientContext context = context(cancelled, (launcher, version, directory, session, host, port, scope) -> new FakeLaunch(directory, scope))) {
            Profile profile = context.profiles().create("fabric", ProfileType.MODS, "1.20.4", "FABRIC", false);
            assertThrows(IOException.class, () -> context.installLoader("fabric", null, cancellation));
            assertFalse(context.profiles().isInUse("fabric")); assertFalse(Files.exists(profile.getDirectory().resolve("loader.properties")));
            try (java.util.stream.Stream<Path> files = Files.list(context.getDataRoot())) { assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".loader-list-"))); }
        }
    }

    private NativeClientContext context(LaunchTransport transport, NativeClientContext.LaunchPreparation preparation) throws Exception {
        Path data = Files.createTempDirectory(temporary.toRealPath(), "context-").toRealPath();
        ClientSettings settings = new ClientSettings(data); settings.set("cacheDirectory", data.resolve("cache").toString());
        settings.set("offlineFixtureMode", "true"); settings.set("username", "Fixture"); settings.save();
        return new NativeClientContext(data, data, transport, ENGINE, preparation);
    }

    private static void waitFixture(CountDownLatch latch) throws IOException {
        try { if (!latch.await(2, TimeUnit.SECONDS)) throw new IOException("Fixture release timed out"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException("Fixture interrupted", error); }
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        assertTrue(condition.getAsBoolean());
    }

    private static final class FakeLaunch implements NativeClientContext.ClientLaunch {
        final FakeProcess process = new FakeProcess(); final Path natives; final Cancellation cancellation;
        volatile boolean closed; boolean failStart; int starts;
        FakeLaunch(Path directory, Cancellation cancellation) throws IOException {
            this.cancellation = cancellation; natives = Files.createTempDirectory(directory, ".fixture-natives-");
        }
        public Process start() throws IOException { cancellation.check(); starts++; if (failStart) throw new IOException("fixture start failed"); return process; }
        public synchronized void close() throws IOException { cancellation.cancel(); process.finish(); Files.deleteIfExists(natives); closed = true; }
    }
    private static final class FakeProcess extends Process {
        final CountDownLatch exited = new CountDownLatch(1);
        public OutputStream getOutputStream() { return new java.io.ByteArrayOutputStream(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        public int waitFor() throws InterruptedException { exited.await(); return 0; }
        public int exitValue() { if (isAlive()) throw new IllegalThreadStateException(); return 0; }
        public void destroy() { finish(); }
        public boolean isAlive() { return exited.getCount() != 0; }
        void finish() { exited.countDown(); }
    }
}
