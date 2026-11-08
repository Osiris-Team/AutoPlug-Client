package com.osiris.autoplug.client.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class LegacyWorldLaunchTest {
    @TempDir Path temporary;

    @Test void exactPublisherMappingsCoverLegacyReleasesAndBothSnapshotFlowApis() throws Exception {
        for (String version : Arrays.asList("1.14.4", "1.15.2", "1.16", "1.16.5", "1.17.1", "1.18.2", "1.19", "1.19.2", "1.19.3", "1.19.4", "22w11a", "22w13a")) {
            try (InputStream input = getClass().getResourceAsStream("/launcher/legacy-mappings-" + version + ".txt")) {
                assertNotNull(input, version);
                Properties names = LegacyWorldLaunch.resolve(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                assertFalse(names.getProperty("minecraft").contains("net.minecraft"), version);
                assertEquals("a", names.getProperty("load"), version);
                assertEquals(version.startsWith("1.14") || version.startsWith("1.15"), names.containsKey("levelSource"), version);
                if (version.equals("22w11a")) { assertNotNull(names.getProperty("flows")); assertNull(names.getProperty("screenType")); }
                if (version.startsWith("1.19")) assertNotNull(names.getProperty("screenType"), version);
            }
        }
        assertThrows(java.io.IOException.class, () -> LegacyWorldLaunch.resolve("unrecognized -> a:\n"));
    }

    @Test void bundledAgentIsJava8AndIdenticalCachedJarIsNotReplaced() throws Exception {
        Path jar = LegacyWorldLaunch.packageAgent(temporary);
        java.nio.file.attribute.FileTime marker = java.nio.file.attribute.FileTime.fromMillis(123456789);
        Files.setLastModifiedTime(jar, marker);
        assertEquals(jar, LegacyWorldLaunch.packageAgent(temporary));
        assertEquals(marker, Files.getLastModifiedTime(jar));
        try (JarFile packaged = new JarFile(jar.toFile()); InputStream bytes = packaged.getInputStream(packaged.getJarEntry(LegacyWorldLaunch.AGENT))) {
            byte[] header = new byte[8]; assertEquals(8, bytes.read(header)); assertEquals(52, header[7]);
            assertEquals("com.osiris.autoplug.legacy.LegacyWorldAgent", packaged.getManifest().getMainAttributes().getValue("Premain-Class"));
        }
        Files.write(jar, new byte[]{1, 2, 3});
        LegacyWorldLaunch.packageAgent(temporary);
        try (JarFile restored = new JarFile(jar.toFile())) { assertNotNull(restored.getJarEntry(LegacyWorldLaunch.AGENT)); }
    }

    @Test void preparationVerifiesPublisherMappingsAndReusesTheExactCacheOffline() throws Exception {
        byte[] mappings;
        try (InputStream input = getClass().getResourceAsStream("/launcher/legacy-mappings-1.18.2.txt")) { mappings = input.readAllBytes(); }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mappings", exchange -> {
            exchange.sendResponseHeaders(200, mappings.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(mappings); }
        });
        server.start();
        JsonObject descriptor = new JsonObject();
        descriptor.addProperty("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/mappings");
        descriptor.addProperty("sha1", com.osiris.autoplug.client.utils.UtilsCrypto.calculateSHA1Hash(mappings));
        descriptor.addProperty("size", mappings.length);
        JsonObject metadata = new JsonObject(), downloads = new JsonObject(); downloads.add("client_mappings", descriptor); metadata.add("downloads", downloads);
        Path cache = temporary.resolve("verified cache"), game = temporary.resolve("original game");
        LaunchRequest request = new LaunchRequest(game, "1.18.2", "VANILLA", null, MinecraftAccount.offline("Player"), null, 0, "Original folder");
        LegacyWorldLaunch prepared;
        try { prepared = LegacyWorldLaunch.prepare(cache, metadata, request, ignored -> { }); }
        finally { server.stop(0); }
        Properties names = new Properties(); try (InputStream input = Files.newInputStream(prepared.configuration)) { names.load(input); }
        assertEquals("dyr", names.getProperty("minecraft")); assertEquals("Original folder", names.getProperty("world"));
        assertEquals(game.toString(), names.getProperty("gameDir"));
        assertTrue(prepared.argument().startsWith("-javaagent:"));
        assertEquals(prepared.agent, LegacyWorldLaunch.prepare(cache, metadata, request, ignored -> { }).agent);
        assertThrows(java.io.IOException.class, () -> LegacyWorldLaunch.prepare(cache, metadata,
                new LaunchRequest(game, "1.18.2", "FABRIC", "test", MinecraftAccount.offline("Player"), null, 0, "Original folder"), ignored -> { }));
        Files.write(cache.resolve("versions/1.18.2/client-mappings.txt"), new byte[]{0});
        assertThrows(java.io.IOException.class, () -> LegacyWorldLaunch.prepare(cache, metadata, request, ignored -> { }));
    }

    @Test void startupAgentWaitsForTitleAndOverlayThenUsesMainThreadForAllNativeApis() throws Exception {
        for (String route : Arrays.asList("direct", "flow", "screen-flow", "original-name", "original-scheduler")) {
            Path game = Files.createDirectories(temporary.resolve("game with spaces " + route));
            Properties names = fixtureNames(route);
            Files.createDirectories(game.resolve("saves/World folder ü"));
            Files.write(game.resolve("saves/World folder ü/level.dat"), new byte[]{1});
            names.setProperty("gameDir", game.toString());
            Path config = game.resolve("launch ü.properties");
            try (OutputStream out = Files.newOutputStream(config)) { names.store(out, "fixture"); }
            Path agent = LegacyWorldLaunch.packageAgent(temporary.resolve("helper with spaces"));
            String classpath = Paths.get(LegacyWorldClientFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            String java = Paths.get(System.getProperty("java.home"), "bin", JavaRuntimeManager.executableName()).toString();
            PreparedLaunch prepared = new PreparedLaunch(Paths.get(java), game,
                    Arrays.asList("-javaagent:" + agent + "=" + config, "-cp", classpath, LegacyWorldClientFixture.class.getName(), route),
                    "fixture", 21, Collections.singletonList(config));
            Process process = new MinecraftLauncher(temporary.resolve("cache"), new JavaRuntimeManager(temporary.resolve("runtimes"))).launch(prepared);
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), route);
            assertEquals(0, process.exitValue(), () -> { try { return new String(Files.readAllBytes(game.resolve("logs/autoplug-launcher.log")), StandardCharsets.UTF_8); } catch (Exception e) { return e.toString(); } });
            assertEquals("World folder ü|" + route + "|main", new String(Files.readAllBytes(game.resolve("entered.txt")), StandardCharsets.UTF_8));
            for (int i = 0; i < 100 && Files.exists(config); i++) Thread.sleep(10);
            assertFalse(Files.exists(config), "helper config cleanup");
        }
    }

    @Test void prePublisherMappingAdapterIsPinnedToTheVerifiedOriginalClient() throws Exception {
        Properties adapter = LegacyWorldLaunch.bundled("1.12.2", "0f275bc1547d01fa5f56ba34bdc87d981ee12daf");
        assertEquals("bib", adapter.getProperty("minecraft"));
        assertEquals("a", adapter.getProperty("scheduler"));
        assertNull(adapter.getProperty("overlay"));
        assertThrows(java.io.IOException.class, () -> LegacyWorldLaunch.bundled("1.12.2", "different-client"));
        assertThrows(java.io.IOException.class, () -> LegacyWorldLaunch.bundled("unverified-build", ""));
        for (String version : Arrays.asList("1.7.10", "1.8.9", "1.12.2", "1.13.2")) {
            try (InputStream source = getClass().getResourceAsStream("/launcher/legacy/" + version + ".properties")) {
                Properties expected = new Properties(); expected.load(source);
                Properties actual = LegacyWorldLaunch.bundled(version, expected.getProperty("clientSha1"));
                assertEquals("a", actual.getProperty("scheduler"));
                assertNotNull(actual.getProperty("levelName"));
            }
        }
    }

    @Test void temporaryHelperConfigurationIsRemovedWhenStartingFails() throws Exception {
        Path config = Files.createTempFile(temporary, "legacy-", ".properties");
        PreparedLaunch launch = new PreparedLaunch(temporary.resolve("missing-java"), temporary,
                Collections.emptyList(), "fixture", 8, Collections.singletonList(config));
        MinecraftLauncher launcher = new MinecraftLauncher(temporary.resolve("cache"), new JavaRuntimeManager(temporary.resolve("runtimes")));
        assertThrows(java.io.IOException.class, () -> launcher.launch(launch));
        assertFalse(Files.exists(config));
    }

    private Properties fixtureNames(String route) {
        Properties p = new Properties(); String base = LegacyWorldClientFixture.class.getName();
        p.setProperty("minecraft", base); p.setProperty("instance", "getInstance"); p.setProperty("title", base + "$TitleScreen");
        p.setProperty("screen", "screen"); p.setProperty("overlay", "getOverlay"); p.setProperty("world", "World folder ü"); p.setProperty("load", "loadLevel");
        if (route.contains("flow")) p.setProperty("flows", "createWorldOpenFlows");
        if (route.equals("screen-flow")) p.setProperty("screenType", base + "$TitleScreen");
        if (route.startsWith("original-")) {
            p.setProperty("load", "selectLevel"); p.setProperty("levelSource", "getLevelSource"); p.setProperty("settingsType", base + "$Settings");
            p.setProperty("levelExists", "levelExists"); p.setProperty("levelList", "getLevelList"); p.setProperty("levelId", "getLevelId"); p.setProperty("levelName", "getLevelName");
        }
        if (route.equals("original-scheduler")) { p.setProperty("scheduler", "schedule"); p.remove("overlay"); }
        return p;
    }
}
