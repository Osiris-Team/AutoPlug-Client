package com.osiris.autoplug.client.launcher;

import com.osiris.autoplug.client.tasks.updater.TaskDownload;
import com.osiris.betterthread.BThreadManager;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DownloadProgressTest {
    @TempDir Path temporary;

    @Test void sourceUrlsRetainUsefulFiltersAndRedactCredentials() {
        String original = "https://name:secret@example.test/download?version=1.21.1&arch=x64&access_token=private&X-Amz-Signature=signature&api%5Fkey=private#access_token=private";
        String safe = DownloadProgress.sourceUrl(original);
        assertEquals("https://[redacted]@example.test/download?version=1.21.1&arch=x64&access_token=[redacted]&X-Amz-Signature=[redacted]&api%5Fkey=[redacted]#[redacted]", safe);
        assertFalse(safe.contains("secret"));
        assertFalse(safe.contains("private"));
        assertEquals("https://example.test/a%20b.jar?version=1.21&loader=fabric", DownloadProgress.sourceUrl("https://example.test/a%20b.jar?version=1.21&loader=fabric"));
        assertEquals("[invalid source URL]", DownloadProgress.sourceUrl("not-a-url?token=private"));
        assertEquals("Source HTTPS://example.test/file?token=[redacted]", DownloadProgress.sanitizeUrls("Source HTTPS://example.test/file?token=private"));
    }

    @Test void metadataFetchEmitsSourceAndUnknownLengthProgress() throws Exception {
        HttpServer server = server("{\"versions\":[]}", true, 200);
        List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
        String source = url(server) + "/manifest?channel=release";
        try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
            assertTrue(LauncherFiles.json(source).has("versions"));
        } finally { server.stop(0); }
        assertEquals(source, events.get(0).sourceUrl);
        assertEquals(-1, events.get(0).totalBytes);
        DownloadProgress.Event last = events.get(events.size() - 1);
        assertTrue(last.complete);
        assertTrue(last.downloadedBytes > 0);
        assertEquals(-1, last.totalBytes);
    }

    @Test void downloadsEmitMeasuredProgressAndCacheHitsDoNotClaimDownloads() throws Exception {
        String content = "verified fixture";
        HttpServer server = server(content, false, 200);
        List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
        List<String> callbacks = new ArrayList<>();
        String source = url(server) + "/client.jar?version=1.21&token=private";
        Path target = temporary.resolve("client.jar");
        try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
            LauncherFiles.download(source, target, null, content.length(), callbacks::add);
            int before = events.size();
            LauncherFiles.download(source, target, null, content.length(), callbacks::add);
            assertEquals(before, events.size());
        } finally { server.stop(0); }
        assertEquals(content, new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
        assertTrue(events.stream().anyMatch(event -> event.totalBytes == content.length() && !event.complete));
        assertTrue(events.get(events.size() - 1).complete);
        assertTrue(callbacks.get(0).contains("from " + url(server)));
        assertFalse(callbacks.toString().contains("private"));
        assertFalse(events.stream().anyMatch(event -> event.sourceUrl != null && event.sourceUrl.contains("private")));
    }

    @Test void assetWorkerDownloadsReachSubscribersEvenWithoutCallbacks() throws Exception {
        HttpServer server = server("asset", false, 200);
        List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
        ExecutorService workers = Executors.newFixedThreadPool(3);
        try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                final int id = i;
                futures.add(workers.submit(() -> {
                    try { LauncherFiles.download(url(server) + "/objects/" + id, temporary.resolve("asset-" + id), null, 5, value -> { }); }
                    catch (IOException e) { throw new IllegalStateException(e); }
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally { workers.shutdownNow(); server.stop(0); }
        assertEquals(3, events.stream().filter(event -> event.complete).count());
        for (int i = 0; i < 3; i++) {
            String suffix = "/objects/" + i;
            assertTrue(events.stream().anyMatch(event -> event.sourceUrl.endsWith(suffix)));
        }
    }

    @Test void failedDownloadsExposeSafeSourceAndNeverCompletion() throws Exception {
        HttpServer server = server("missing", false, 404);
        List<DownloadProgress.Event> events = new ArrayList<>();
        try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
            IOException failure = assertThrows(IOException.class, () -> LauncherFiles.download(url(server) + "/library?token=private", temporary.resolve("missing.jar"), null, -1, null));
            assertTrue(failure.getMessage().contains("token=[redacted]"));
            assertFalse(failure.getMessage().contains("private"));
            assertFalse(events.stream().anyMatch(event -> event.complete));
            assertFalse(Files.exists(temporary.resolve("missing.jar")));
        } finally { server.stop(0); }
    }

    @Test void updaterArtifactsPublishSourceAndBytes() throws Exception {
        HttpServer server = server("plugin", false, 200);
        List<DownloadProgress.Event> events = new ArrayList<>();
        Path target = temporary.resolve("plugin.jar");
        String source = url(server) + "/plugin.jar?token=private";
        try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
            new TaskDownload("test", new BThreadManager(), source, target.toFile(), true).runAtStart();
        } finally { server.stop(0); }
        assertTrue(events.stream().anyMatch(event -> event.sourceUrl.equals(DownloadProgress.sourceUrl(source))));
        DownloadProgress.Event last = events.get(events.size() - 1);
        assertTrue(last.complete); assertEquals(6, last.downloadedBytes); assertEquals(6, last.totalBytes);
    }

    @Test void officialInstallerOutputIsForwardedAndPersistedWithoutTokens() throws Exception {
        List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
        List<String> logs = new CopyOnWriteArrayList<>();
        Path java = Paths.get(System.getProperty("java.home"), "bin", JavaRuntimeManager.executableName());
        try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
            LoaderInstaller.run(java, Arrays.asList("-cp", System.getProperty("java.class.path"), InstallerFixture.class.getName()), temporary, logs::add);
        }
        assertTrue(events.stream().anyMatch(event -> event.sourceUrl != null && event.sourceUrl.contains("/library.jar?version=1&token=[redacted]")));
        String file = new String(Files.readAllBytes(temporary.resolve("autoplug-loader-install.log")), StandardCharsets.UTF_8);
        assertTrue(file.contains("Installing processors"));
        assertFalse(file.contains("private"));
        assertFalse(logs.toString().contains("private"));
    }

    @Test void listenerDisposalAndFailureDoNotAffectOtherObservers() throws Exception {
        AtomicInteger events = new AtomicInteger();
        try (AutoCloseable faulty = DownloadProgress.subscribe(event -> { throw new IllegalStateException("closed view"); })) {
            AutoCloseable listener = DownloadProgress.subscribe(event -> events.incrementAndGet());
            try (DownloadProgress.Transfer transfer = DownloadProgress.begin("Downloading", "https://example.test/file")) {
                transfer.complete(1, 1);
            }
            listener.close();
            try (DownloadProgress.Transfer transfer = DownloadProgress.begin("Downloading", "https://example.test/file2")) {
                transfer.complete(1, 1);
            }
        }
        assertEquals(2, events.get());
    }

    @Test void overlappingAndFailedTransfersHaveStableIdsAndAccurateActiveCount() throws Exception {
        List<DownloadProgress.Event> events = new ArrayList<>();
        try (AutoCloseable ignored = DownloadProgress.subscribe(events::add);
             DownloadProgress.Transfer first = DownloadProgress.begin("First", "https://example.test/a");
             DownloadProgress.Transfer second = DownloadProgress.begin("Second", "https://example.test/b")) {
            assertEquals(2, events.get(1).activeTransfers);
            assertNotEquals(events.get(0).transferId, events.get(1).transferId);
            first.complete(10, 10);
            assertEquals(1, events.get(2).activeTransfers);
            assertEquals(events.get(0).transferId, events.get(2).transferId);
            second.close();
            DownloadProgress.Event stopped = events.get(3);
            assertEquals(0, stopped.activeTransfers);
            assertTrue(stopped.finished);
            assertFalse(stopped.complete);
        }
    }

    private static HttpServer server(String content, boolean chunked, int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(status, chunked ? 0 : bytes.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start(); return server;
    }
    private static String url(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    public static class InstallerFixture {
        public static void main(String[] args) {
            System.out.println("Downloading https://example.test/library.jar?version=1&token=private");
            System.out.println("Installing processors");
        }
    }
}
