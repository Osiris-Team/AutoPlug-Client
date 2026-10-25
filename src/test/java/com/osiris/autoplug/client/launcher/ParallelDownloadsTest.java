package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.utils.UtilsCrypto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ParallelDownloadsTest {
    @TempDir Path temporary;

    @Test void completePreparationCombinesFilesInBoundedQueueAndReusesVerifiedCache() throws Exception {
        try (FixtureServer server = new FixtureServer()) {
            JsonObject metadata = baseMetadata();
            JsonArray libraries = new JsonArray();
            List<String> expectedClasspath = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                String path = "test/library-" + i + ".jar";
                JsonObject artifact = server.descriptor("/library-" + i, bytes("library " + i), true);
                artifact.addProperty("path", path);
                JsonObject downloads = new JsonObject(); downloads.add("artifact", artifact);
                JsonObject library = new JsonObject(); library.addProperty("name", "test:library:" + i); library.add("downloads", downloads);
                libraries.add(library);
                expectedClasspath.add(temporary.resolve("cache/libraries").resolve(path).toString());
            }
            metadata.add("libraries", libraries);
            JsonObject clientDownloads = new JsonObject(); clientDownloads.add("client", server.descriptor("/client", bytes("client"), true));
            metadata.add("downloads", clientDownloads);
            JsonObject objects = new JsonObject();
            for (int i = 0; i < 4; i++) {
                byte[] contents = bytes("asset " + i);
                String hash = UtilsCrypto.calculateSHA1Hash(contents);
                server.add("/objects/" + hash.substring(0, 2) + "/" + hash, contents, true);
                JsonObject object = new JsonObject(); object.addProperty("hash", hash); object.addProperty("size", contents.length);
                objects.add("sounds/" + i, object);
            }
            JsonObject index = new JsonObject(); index.add("objects", objects);
            index.addProperty("virtual", true); index.addProperty("map_to_resources", true);
            JsonObject assetIndex = server.descriptor("/index", bytes(index.toString()), false); assetIndex.addProperty("id", "fixture");
            metadata.add("assetIndex", assetIndex);
            JsonObject loggingClient = new JsonObject();
            JsonObject loggingFile = server.descriptor("/logging", bytes("logging"), true); loggingFile.addProperty("id", "fixture.xml");
            loggingClient.add("file", loggingFile); loggingClient.addProperty("argument", "-Dlog4j.configurationFile=${path}");
            JsonObject logging = new JsonObject(); logging.add("client", loggingClient); metadata.add("logging", logging);
            JsonObject version = server.descriptor("/version", bytes(metadata.toString()), false); version.addProperty("id", "fixture");
            JsonArray versions = new JsonArray(); versions.add(version);
            JsonObject manifest = new JsonObject(); manifest.add("versions", versions); server.add("/manifest", bytes(manifest.toString()), false);
            MinecraftLauncher launcher = launcher(server);
            LaunchRequest request = new LaunchRequest(temporary.resolve("game"), "fixture", "VANILLA", null, MinecraftAccount.offline("Player"), null, 0);
            List<String> callbacks = new ArrayList<>();
            List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
            PreparedLaunch prepared;
            try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
                prepared = launcher.prepare(request, callbacks::add);
            }
            assertTrue(server.maximum.get() >= 2, "Downloads must overlap");
            assertTrue(server.maximum.get() <= ParallelDownloads.WORKERS, "Active requests must remain bounded");
            assertEquals(18, server.artifactRequests.get());
            assertBatch(callbacks, "Minecraft files", 18);
            expectedClasspath.add(temporary.resolve("cache/versions/fixture/fixture.jar").toString());
            assertEquals(String.join(java.io.File.pathSeparator, expectedClasspath), prepared.arguments.get(prepared.arguments.indexOf("-cp") + 1));
            assertTrue(prepared.arguments.contains("--fullscreen"));
            for (int i = 0; i < 4; i++) {
                assertArrayEquals(bytes("asset " + i), Files.readAllBytes(temporary.resolve("game/resources/sounds/" + i)));
                assertArrayEquals(bytes("asset " + i), Files.readAllBytes(temporary.resolve("cache/assets/virtual/fixture/sounds/" + i)));
            }
            assertTrue(events.stream().anyMatch(event -> event.sourceUrl != null && event.sourceUrl.contains("/objects/") && event.complete));
            assertEquals(0, events.get(events.size() - 1).activeTransfers);
            server.stop();
            // All metadata/artifacts have been cached. A second preparation remains offline-capable.
            assertEquals(prepared.command(), launcher.prepare(request, value -> { }).command());
            assertNoPartialFiles();
        }
    }

    @Test void mavenAndNativeDownloadsPreserveClasspathAndOrderedExtraction() throws Exception {
        try (FixtureServer server = new FixtureServer()) {
            JsonObject metadata = baseMetadata();
            JsonArray libraries = new JsonArray();
            for (int i = 0; i < 2; i++) {
                JsonObject library = new JsonObject(); library.addProperty("name", "fixture:native:" + i);
                JsonObject nativeMap = new JsonObject(); nativeMap.addProperty(MinecraftLauncher.osName(), "native"); library.add("natives", nativeMap);
                JsonObject nativeArtifact = server.descriptor("/native-" + i, nativeJar("native " + i), true);
                nativeArtifact.addProperty("path", "native-" + i + ".jar");
                JsonObject classifiers = new JsonObject(); classifiers.add("native", nativeArtifact);
                JsonObject downloads = new JsonObject(); downloads.add("classifiers", classifiers); library.add("downloads", downloads);
                libraries.add(library);
            }
            for (int i = 0; i < 8; i++) {
                JsonObject library = new JsonObject(); library.addProperty("name", "fixture:maven:" + i);
                library.addProperty("url", server.base() + "/maven/");
                byte[] content = bytes("maven " + i);
                library.addProperty("sha1", UtilsCrypto.calculateSHA1Hash(content)); library.addProperty("size", content.length);
                server.add("/maven/fixture/maven/" + i + "/maven-" + i + ".jar", content, true);
                libraries.add(library);
            }
            Path generated = temporary.resolve("cache/libraries/fixture/generated/1/generated-1.jar");
            Files.createDirectories(generated.getParent()); Files.write(generated, bytes("generated"));
            JsonObject installed = new JsonObject(); installed.addProperty("name", "fixture:generated:1"); installed.addProperty("url", ""); libraries.add(installed);
            metadata.add("libraries", libraries);
            List<Path> classpath = launcher(server).libraries(metadata, temporary.resolve("natives"), value -> { });
            assertEquals(9, classpath.size());
            for (int i = 0; i < 8; i++) assertEquals("maven-" + i + ".jar", classpath.get(i).getFileName().toString());
            assertEquals(generated, classpath.get(8));
            assertArrayEquals(bytes("native 1"), Files.readAllBytes(temporary.resolve("natives/shared-native.bin")));
            assertFalse(Files.exists(temporary.resolve("natives/META-INF/ignored")));
            assertTrue(server.maximum.get() >= 2);
        }
    }

    @Test void transientHttpAndCorruptBodiesRetryWithoutReplacingExistingFileEarly() throws Exception {
        try (FixtureServer server = new FixtureServer()) {
            byte[] original = new byte[]{7, 7, 7}, wanted = new byte[]{1, 2, 3};
            Path target = temporary.resolve("artifact.jar"); Files.write(target, original);
            AtomicInteger attempts = new AtomicInteger();
            AtomicBoolean originalPreserved = new AtomicBoolean(true);
            server.server.createContext("/retry", exchange -> {
                if (!Arrays.equals(original, Files.readAllBytes(target))) originalPreserved.set(false);
                int attempt = attempts.incrementAndGet();
                respond(exchange, attempt == 1 ? 503 : 200, attempt == 2 ? new byte[]{9, 9, 9} : wanted);
            });
            List<String> callbacks = new ArrayList<>();
            List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
            try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
                ParallelDownloads.run("Fixture files", Collections.singletonList(() -> {
                    LauncherFiles.download(server.base() + "/retry", target, UtilsCrypto.calculateSHA1Hash(wanted), wanted.length, callbacks::add);
                    return null;
                }), callbacks::add);
            }
            assertEquals(3, attempts.get()); assertTrue(originalPreserved.get());
            assertArrayEquals(wanted, Files.readAllBytes(target));
            assertEquals(2, callbacks.stream().filter(value -> value.startsWith("Retrying ")).count());
            assertEquals(1, events.stream().filter(event -> event.complete).count());
            assertBatch(callbacks, "Fixture files", 1);
            assertNoPartialFiles();
        }
    }

    @Test void exhaustedRetriesPropagateFailureWithoutPublishingCompletion() throws Exception {
        try (FixtureServer server = new FixtureServer()) {
            AtomicInteger attempts = new AtomicInteger();
            server.server.createContext("/unavailable", exchange -> {
                attempts.incrementAndGet(); respond(exchange, 503, bytes("unavailable"));
            });
            Path target = temporary.resolve("existing.jar");
            byte[] original = bytes("previous file"); Files.write(target, original);
            List<String> callbacks = new ArrayList<>();
            List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
            try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
                IOException failure = assertThrows(IOException.class, () -> ParallelDownloads.run("Fixture files", Collections.singletonList(() -> {
                    LauncherFiles.download(server.base() + "/unavailable", target, UtilsCrypto.calculateSHA1Hash(bytes("wanted")), 6, null);
                    return null;
                }), callbacks::add));
                assertTrue(failure.getMessage().contains("HTTP 503"));
            }
            assertEquals(3, attempts.get());
            assertArrayEquals(original, Files.readAllBytes(target));
            assertEquals(Collections.singletonList("Fixture files: 0/1"), callbacks);
            assertFalse(events.stream().anyMatch(event -> event.complete));
            assertEquals(0, events.get(events.size() - 1).activeTransfers);
            assertNoPartialFiles();
        }
    }

    @Test void permanentFailureCancelsBlockedSocketsAndDoesNotStartQueuedFiles() throws Exception {
        try (FixtureServer server = new FixtureServer()) {
            CountDownLatch blocked = new CountDownLatch(2), release = new CountDownLatch(1);
            AtomicInteger requests = new AtomicInteger();
            server.server.createContext("/failure", exchange -> {
                requests.incrementAndGet();
                await(blocked);
                respond(exchange, 404, bytes("missing"));
            });
            server.server.createContext("/blocked", exchange -> {
                requests.incrementAndGet();
                exchange.sendResponseHeaders(200, 1024);
                exchange.getResponseBody().write(1); exchange.getResponseBody().flush(); blocked.countDown();
                await(release); exchange.close();
            });
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                int index = i;
                jobs.add(() -> { LauncherFiles.download(server.base() + (index == 0 ? "/failure" : "/blocked"),
                        temporary.resolve("file-" + index), null, 1024, null); return null; });
            }
            List<String> callbacks = new ArrayList<>();
            List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
            try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
                assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
                    IOException failure = assertThrows(IOException.class, () -> ParallelDownloads.run("Fixture files", jobs, callbacks::add));
                    assertTrue(failure.getMessage().contains("HTTP 404"));
                });
            } finally { release.countDown(); }
            assertTrue(requests.get() <= ParallelDownloads.WORKERS);
            assertFalse(callbacks.contains("Fixture files: 40/40"));
            assertFalse(events.stream().anyMatch(event -> event.complete));
            assertEquals(0, events.get(events.size() - 1).activeTransfers);
            assertNoPartialFiles();
            for (int i = 0; i < 40; i++) assertFalse(Files.exists(temporary.resolve("file-" + i)));
        }
    }

    @Test void callerInterruptionCancelsActiveRequestAndRetainsInterruptStatus() throws Exception {
        try (FixtureServer server = new FixtureServer()) {
            CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), stopped = new CountDownLatch(1);
            server.server.createContext("/interrupt", exchange -> {
                exchange.sendResponseHeaders(200, 1024); exchange.getResponseBody().write(1); exchange.getResponseBody().flush();
                started.countDown(); await(release); exchange.close();
            });
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread caller = new Thread(() -> {
                try {
                    ParallelDownloads.run("Fixture files", Collections.singletonList(() -> {
                        LauncherFiles.download(server.base() + "/interrupt", temporary.resolve("interrupted"), null, 1024, null); return null;
                    }), value -> { });
                } catch (Throwable e) { failure.set(e); interrupted.set(Thread.currentThread().isInterrupted()); }
                finally { stopped.countDown(); }
            });
            caller.setDaemon(true); caller.start();
            try {
                assertTrue(started.await(3, TimeUnit.SECONDS)); caller.interrupt();
                assertTrue(stopped.await(7, TimeUnit.SECONDS), "Cancellation must stop a blocked socket read");
                assertTrue(failure.get() instanceof InterruptedIOException); assertTrue(interrupted.get());
                assertFalse(Files.exists(temporary.resolve("interrupted"))); assertNoPartialFiles();
            } finally { release.countDown(); caller.interrupt(); }
        }
    }

    @Test void fullscreenDefaultsToEnabledForLegacyAndModernLoadersAndCanBeDisabled() throws Exception {
        for (String version : Arrays.asList("1.16.5", "1.21.1")) {
            JsonObject vanilla = MinecraftLauncherTest.fixture(version);
            for (String loader : Arrays.asList("VANILLA", "FABRIC", "FORGE", "NEOFORGE", "QUILT")) {
                LaunchRequest defaults = new LaunchRequest(temporary, version, loader, null, MinecraftAccount.offline("Player"), null, 0);
                LaunchRequest windowed = new LaunchRequest(temporary, version, loader, null, MinecraftAccount.offline("Player"), null, 0, false);
                assertTrue(defaults.fullscreen);
                List<String> args = arguments(vanilla, defaults);
                assertEquals(1, Collections.frequency(args, "--fullscreen"));
                assertTrue(args.indexOf("--fullscreen") > args.indexOf(vanilla.get("mainClass").getAsString()));
                assertFalse(arguments(vanilla, windowed).contains("--fullscreen"));
            }
        }
        assertFalse(new LaunchRequest(temporary, "1.21.1", "VANILLA", null, MinecraftAccount.offline("Player"), null, 0, "World", false).fullscreen);
    }

    private List<String> arguments(JsonObject metadata, LaunchRequest request) throws Exception {
        return MinecraftLauncher.buildArguments(metadata, request, Collections.singletonList(temporary.resolve("client.jar")),
                temporary.resolve("natives"), temporary.resolve("assets"), "fixture", null);
    }
    private MinecraftLauncher launcher(FixtureServer server) {
        JavaRuntimeManager runtime = new JavaRuntimeManager(temporary.resolve("runtimes")) {
            @Override public Path resolve(int major, java.util.function.Consumer<String> progress) {
                return Paths.get(System.getProperty("java.home"), "bin", JavaRuntimeManager.executableName());
            }
        };
        return new MinecraftLauncher(temporary.resolve("cache"), runtime, server.base() + "/manifest", server.base() + "/objects/");
    }
    private static JsonObject baseMetadata() {
        JsonObject metadata = new JsonObject(); metadata.addProperty("id", "fixture");
        metadata.addProperty("mainClass", "net.minecraft.client.main.Main"); metadata.addProperty("minecraftArguments", "--username ${auth_player_name}");
        return metadata;
    }
    private static void assertBatch(List<String> callbacks, String label, int total) {
        List<String> actual = new ArrayList<>();
        for (String callback : callbacks) if (callback.startsWith(label + ": ")) actual.add(callback);
        assertEquals(total + 1, actual.size());
        for (int i = 0; i <= total; i++) assertEquals(label + ": " + i + "/" + total, actual.get(i));
    }
    private void assertNoPartialFiles() throws IOException {
        try (java.util.stream.Stream<Path> files = Files.walk(temporary)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static byte[] nativeJar(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("shared-native.bin")); zip.write(bytes(value)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/ignored")); zip.write(1); zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    private static void await(CountDownLatch latch) {
        try { latch.await(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    private static void respond(HttpExchange exchange, int status, byte[] contents) throws IOException {
        exchange.sendResponseHeaders(status, contents.length);
        try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(contents); }
        finally { exchange.close(); }
    }
    private static final class FixtureServer implements AutoCloseable {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final ExecutorService workers = Executors.newCachedThreadPool();
        final AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger(), artifactRequests = new AtomicInteger();
        final Map<String, byte[]> bodies = new ConcurrentHashMap<>();
        final java.util.Set<String> delayed = ConcurrentHashMap.newKeySet();
        private boolean stopped;
        FixtureServer() throws IOException {
            server.setExecutor(workers);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                byte[] content = bodies.get(path);
                if (content == null) { respond(exchange, 404, bytes("missing")); return; }
                if (delayed.contains(path)) {
                    artifactRequests.incrementAndGet();
                    maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                    try { Thread.sleep(80); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { active.decrementAndGet(); }
                }
                respond(exchange, 200, content);
            });
            server.start();
        }
        String base() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        void add(String path, byte[] body, boolean delay) { bodies.put(path, body); if (delay) delayed.add(path); }
        JsonObject descriptor(String path, byte[] body, boolean delay) {
            add(path, body, delay);
            JsonObject descriptor = new JsonObject(); descriptor.addProperty("url", base() + path);
            descriptor.addProperty("sha1", UtilsCrypto.calculateSHA1Hash(body)); descriptor.addProperty("size", body.length);
            return descriptor;
        }
        void stop() { if (!stopped) { server.stop(0); stopped = true; } }
        @Override public void close() { stop(); workers.shutdownNow(); }
    }
}
