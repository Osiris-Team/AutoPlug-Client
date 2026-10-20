package com.osiris.autoplug.client.tasks.updater;

import com.osiris.autoplug.client.launcher.DownloadProgress;
import com.osiris.autoplug.client.tasks.updater.java.AdoptV3API;
import com.osiris.autoplug.client.tasks.updater.java.TaskJavaDownload;
import com.osiris.autoplug.client.tasks.updater.mods.TaskModDownload;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginDownload;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterthread.BThreadManager;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual legacy loops, which do not inherit TaskDownload. */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class LegacyDownloadProgressTest {
    enum Kind { MOD, PLUGIN, JAVA }
    private static final byte[] CONTENT = "local legacy download fixture".getBytes(StandardCharsets.UTF_8);
    @TempDir Path temporary;
    private String originalUserDir;
    private File originalWorkingDir;

    @BeforeEach void isolateDownloadCache() {
        originalUserDir = System.getProperty("user.dir");
        originalWorkingDir = GD.WORKING_DIR;
        System.setProperty("user.dir", temporary.toString());
    }

    @AfterEach void restoreDownloadCache() {
        System.setProperty("user.dir", originalUserDir);
        GD.WORKING_DIR = originalWorkingDir;
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void redirectedDownloadsReportSourcesBytesAndCompletion(Kind kind) throws Exception {
        HttpServer server = server(false, 200, "application/octet-stream");
        List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
        String source = url(server) + "/redirect?token=private";
        try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
            assertArrayEquals(CONTENT, Files.readAllBytes(download(kind, source)));
        } finally { server.stop(0); }
        assertEquals(DownloadProgress.sourceUrl(source), events.get(0).sourceUrl);
        assertTrue(events.stream().anyMatch(event -> event.sourceUrl.contains("/artifact?version=1&token=[redacted]")));
        assertTrue(events.stream().anyMatch(event -> !event.finished && event.downloadedBytes > 0
                && event.totalBytes == CONTENT.length));
        assertCompleted(events, CONTENT.length);
        assertFalse(events.stream().anyMatch(event -> event.sourceUrl.contains("private")));
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void unknownLengthDownloadsRemainIndeterminateUntilFinished(Kind kind) throws Exception {
        HttpServer server = server(true, 200, "application/octet-stream");
        List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
        try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
            assertArrayEquals(CONTENT, Files.readAllBytes(download(kind, url(server) + "/artifact")));
        } finally { server.stop(0); }
        assertCompleted(events, -1);
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void rejectedContentClosesProgressWithoutFalseCompletion(Kind kind) throws Exception {
        HttpServer server = server(false, 200, "text/html");
        List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
        try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
            Exception failure = assertThrows(Exception.class, () -> download(kind, url(server) + "/artifact"));
            assertTrue(failure.getMessage().contains("invalid content type"));
        } finally { server.stop(0); }
        assertFailed(events);
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void failedHttpRequestsReleaseActiveCountAndRedactSource(Kind kind) throws Exception {
        HttpServer server = server(false, 404, "application/octet-stream");
        List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
        try (AutoCloseable ignored = DownloadProgress.subscribe(events::add)) {
            Exception failure = assertThrows(Exception.class, () -> download(kind, url(server) + "/artifact?token=private"));
            assertTrue(failure.getMessage().contains("404"));
            assertTrue(failure.getMessage().contains("token=[redacted]"));
            assertFalse(failure.getMessage().contains("private"));
        } finally { server.stop(0); }
        assertFailed(events);
    }

    @Test void javaCacheCopyFailureMustNotReportSuccessfulCompletion() throws Exception {
        HttpServer server = server(false, 200, "application/octet-stream");
        List<DownloadProgress.Event> events = new CopyOnWriteArrayList<>();
        AtomicBoolean blocked = new AtomicBoolean();
        AtomicReference<Exception> fixtureFailure = new AtomicReference<>();
        Path output = temporary.resolve("java/runtime.zip");
        try (AutoCloseable ignored = DownloadProgress.subscribe(event -> {
            events.add(event);
            if (!event.finished && event.downloadedBytes > 0 && blocked.compareAndSet(false, true)) {
                try {
                    // Simulate a destination becoming unavailable after HTTP succeeds.
                    Files.delete(output);
                    Files.createDirectory(output);
                    Files.write(output.resolve("occupied"), CONTENT);
                } catch (Exception failure) { fixtureFailure.set(failure); }
            }
        })) {
            assertThrows(Exception.class, () -> download(Kind.JAVA, url(server) + "/artifact"));
        } finally { server.stop(0); }
        assertTrue(blocked.get());
        assertNull(fixtureFailure.get());
        assertFailed(events);
    }

    private Path download(Kind kind, String source) throws Exception {
        BThreadManager manager = new BThreadManager();
        if (kind == Kind.MOD) {
            TaskModDownload task = new TaskModDownload("fixture", manager, "fixture-mod", "1", source,
                    "MANUAL", temporary.resolve("unused.jar").toFile());
            task.download();
            return task.getDownloadDest().toPath();
        }
        if (kind == Kind.PLUGIN) {
            TaskPluginDownload task = new TaskPluginDownload("fixture", manager, "fixture-plugin", "1", source,
                    "MANUAL", temporary.resolve("unused.jar").toFile());
            task.download();
            return task.getDownloadDest().toPath();
        }
        TaskJavaDownload task = new TaskJavaDownload("fixture", manager, source,
                temporary.resolve("java/runtime.file").toFile(), AdoptV3API.OperatingSystemType.WINDOWS);
        task.runAtStart();
        assertTrue(task.getNewCacheDest().getName().endsWith(".zip"));
        return task.getNewCacheDest().toPath();
    }

    private static void assertCompleted(List<DownloadProgress.Event> events, long expectedTotal) {
        assertFalse(events.isEmpty());
        DownloadProgress.Event last = events.get(events.size() - 1);
        assertTrue(last.complete);
        assertTrue(last.finished);
        assertEquals(CONTENT.length, last.downloadedBytes);
        assertEquals(expectedTotal, last.totalBytes);
        assertEquals(0, last.activeTransfers);
        assertEquals(1, events.stream().filter(event -> event.finished).count());
        assertTrue(events.stream().allMatch(event -> event.transferId == last.transferId));
    }

    private static void assertFailed(List<DownloadProgress.Event> events) {
        assertFalse(events.isEmpty());
        assertFalse(events.stream().anyMatch(event -> event.complete));
        DownloadProgress.Event last = events.get(events.size() - 1);
        assertTrue(last.finished);
        assertEquals(0, last.activeTransfers);
    }

    private static HttpServer server(boolean chunked, int status, String contentType) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/artifact?version=1&token=private");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/artifact", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=fixture.zip");
            exchange.sendResponseHeaders(status, chunked ? 0 : CONTENT.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(CONTENT); }
        });
        server.start();
        return server;
    }

    private static String url(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }
}
