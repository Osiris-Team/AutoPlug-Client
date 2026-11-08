package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.profiles.*;
import com.osiris.autoplug.client.utils.MineStat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class WorldServiceTest {
    @TempDir Path temporary;

    @Test void realLocalServerBecomesReadyThenStopsWhenClientExits() throws Exception {
        Fixture fixture = fixture(true);
        AtomicInteger mappings = new AtomicInteger();
        AtomicInteger unmappings = new AtomicInteger();
        SharingService share = port -> { mappings.incrementAndGet(); return new SharingService.Lease() {
            public String address() { return "203.0.113.1:25565"; }
            public void close() { unmappings.incrementAndGet(); }
        }; };
        try (WorldService service = service(fixture, share, fixtureInstaller(), realProbe(), Duration.ofSeconds(10))) {
            WorldSession session = service.launch(fixture.world.id, false);
            assertTrue(session.server().isAlive());
            assertTrue(session.client().isAlive());
            assertEquals(0, mappings.get(), "Local launch must never discover or map a router");
            Properties properties = new Properties();
            try (java.io.InputStream input = Files.newInputStream(fixture.worlds.getDirectory(fixture.world.id).resolve("server.properties"))) { properties.load(input); }
            assertEquals("127.0.0.1", properties.getProperty("server-ip"));
            assertEquals("true", properties.getProperty("online-mode"));
            assertTrue(session.share().isShared());
            assertTrue(session.share().isShared());
            assertEquals(1, mappings.get(), "A second click reuses the owned lease");
            session.client().destroy();
            assertTrue(session.client().waitFor(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while ((!service.activeSessions().isEmpty() || session.server().isAlive()) && System.nanoTime() < deadline) Thread.sleep(50);
            assertFalse(session.server().isAlive());
            assertTrue(service.activeSessions().isEmpty());
            assertEquals("graceful", new String(Files.readAllBytes(fixture.worlds.getDirectory(fixture.world.id).resolve("stopped.txt")), java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(1, unmappings.get());
            session.close();
            assertEquals(1, unmappings.get());
        }
    }

    @Test void refusesUnacceptedEulaBeforeAnyInstallOrNetwork() throws Exception {
        Fixture fixture = fixture(false);
        try (WorldService service = service(fixture, port -> { throw new AssertionError("No sharing expected"); },
                (p, d) -> { throw new AssertionError("Must not install before EULA acceptance"); }, realProbe(), Duration.ofSeconds(5))) {
            assertTrue(assertThrows(IllegalStateException.class, () -> service.launch(fixture.world.id, false)).getMessage().contains("EULA"));
            assertFalse(Files.exists(fixture.worlds.getDirectory(fixture.world.id).resolve("eula.txt")));
        }
    }

    @Test void moddedClientCanJoinVanillaWorldButNonVanillaLoadersMustMatch() throws Exception {
        Fixture fixture = fixture(true);
        Profile server = fixture.profiles.get(fixture.world.serverProfileId);
        server.type = ProfileType.MODS_SERVER; server.loader = "FORGE"; fixture.profiles.save(server);
        Profile client = fixture.profiles.get(fixture.world.clientProfileId);
        client.loader = "FABRIC"; fixture.profiles.save(client);
        try (WorldService service = service(fixture, port -> { throw new AssertionError("No sharing"); }, fixtureInstaller(), realProbe(), Duration.ofSeconds(10))) {
            assertThrows(IllegalArgumentException.class, () -> service.launch(fixture.world.id, false));
            server.loader = "VANILLA"; fixture.profiles.save(server);
            WorldSession session = service.launch(fixture.world.id, false);
            assertTrue(session.server().isAlive());
        }
    }

    @Test void timeoutStopsTheRealChildAndDoesNotLaunchClient() throws Exception {
        Fixture fixture = fixture(true);
        AtomicReference<Process> server = new AtomicReference<>();
        try (WorldService service = new WorldService(fixture.profiles, fixture.worlds, fixtureInstaller(),
                (p, h, n) -> { throw new AssertionError("Client must wait for readiness"); },
                port -> { throw new AssertionError("No router actions"); }, (host, port) -> false,
                launch -> { Process process = process(launch); server.set(process); return process; }, Duration.ofMillis(700))) {
            assertThrows(IOException.class, () -> service.launch(fixture.world.id, false));
            assertNotNull(server.get());
            assertFalse(server.get().isAlive());
            assertTrue(service.activeSessions().isEmpty());
        }
    }

    @Test void sharingFailureLeavesLocalWorldAliveAndReturnsFallback() throws Exception {
        Fixture fixture = fixture(true);
        try (WorldService service = service(fixture, port -> { throw new IOException("No gateway"); }, fixtureInstaller(), realProbe(), Duration.ofSeconds(10))) {
            WorldSession session = service.launch(fixture.world.id, true);
            assertFalse(session.getShareResult().isShared());
            assertTrue(session.getShareResult().message.contains("TCP tunnel"));
            assertTrue(session.server().isAlive());
            assertTrue(session.client().isAlive());
        }
    }

    @Test void offlineWorldAllowsLocalJoinButNeverSharesUnauthenticatedServer() throws Exception {
        Fixture fixture = fixture(true);
        try (WorldService service = service(fixture, port -> { throw new AssertionError("Offline mode must not map a router"); }, fixtureInstaller(), realProbe(), Duration.ofSeconds(10))) {
            WorldSession session = service.launch(fixture.world.id, true, false);
            Properties properties = new Properties();
            try (java.io.InputStream input = Files.newInputStream(fixture.worlds.getDirectory(fixture.world.id).resolve("server.properties"))) { properties.load(input); }
            assertEquals("false", properties.getProperty("online-mode"));
            assertEquals("127.0.0.1", properties.getProperty("server-ip"));
            assertFalse(session.getShareResult().isShared());
            assertTrue(session.share().message.contains("licensed Microsoft account"));
            assertTrue(session.server().isAlive());
        }
    }

    @Test void clientLaunchFailureStillStopsServerAndReleasesWorldLease() throws Exception {
        Fixture fixture = fixture(true);
        AtomicReference<Process> server = new AtomicReference<>();
        try (WorldService service = new WorldService(fixture.profiles, fixture.worlds, fixtureInstaller(),
                (p, h, n) -> { throw new IOException("Client fixture failure"); }, port -> { throw new AssertionError(); }, realProbe(),
                launch -> { Process result = process(launch); server.set(result); return result; }, Duration.ofSeconds(10))) {
            assertThrows(IOException.class, () -> service.launch(fixture.world.id, false));
            assertFalse(server.get().isAlive());
            try (ProfileLease ignored = new ProfileLease(fixture.worlds.getDirectory(fixture.world.id))) { assertTrue(true); }
        }
    }

    @Test void disabledSharingMakesNoRouterRequestAndUsesPreferredPort() throws Exception {
        Fixture fixture = fixture(true);
        int preferred;
        try (ServerSocket free = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { preferred = free.getLocalPort(); }
        try (WorldService service = service(fixture, port -> { throw new AssertionError("Settings prohibit router requests"); }, fixtureInstaller(), realProbe(), Duration.ofSeconds(10))) {
            service.setPreferredPort(preferred);
            service.setSharingEnabled(false);
            WorldSession session = service.launch(fixture.world.id, true);
            assertEquals(preferred, session.getPort());
            assertFalse(session.getShareResult().isShared());
            assertTrue(service.shareWorld(fixture.world.id).message.contains("disabled in Settings"));
            service.setPreferredPort(preferred == 65535 ? 25565 : preferred + 1);
            assertEquals(preferred, session.getPort(), "Settings do not change an active world's port");
        }
    }

    @Test void busyPreferredPortFallsBackAndDisablingSettingsReleasesExistingShare() throws Exception {
        Fixture fixture = fixture(true);
        AtomicInteger removed = new AtomicInteger();
        SharingService sharing = port -> new SharingService.Lease() {
            public String address() { return "203.0.113.10:25565"; }
            public void close() { removed.incrementAndGet(); }
        };
        try (ServerSocket busy = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             WorldService service = service(fixture, sharing, fixtureInstaller(), realProbe(), Duration.ofSeconds(10))) {
            service.setPreferredPort(busy.getLocalPort());
            WorldSession session = service.launch(fixture.world.id, true);
            assertNotEquals(busy.getLocalPort(), session.getPort());
            assertTrue(session.getShareResult().isShared());
            service.setSharingEnabled(false);
            assertEquals(1, removed.get());
            assertFalse(session.share().isShared());
            assertTrue(session.server().isAlive());
        }
        assertEquals(1, removed.get(), "Closing the world must not delete an already-removed mapping again");
    }

    private Fixture fixture(boolean eula) throws Exception {
        ProfileStore profiles = new ProfileStore(temporary.resolve("profiles"));
        Profile server = profiles.create("Server", "1.20.4", "PAPER", ProfileType.PLUGINS);
        Profile client = profiles.create("Client", "1.20.4", "VANILLA", ProfileType.MODS);
        WorldStore worlds = new WorldStore(temporary.resolve("worlds"));
        return new Fixture(profiles, worlds, worlds.create("Local fixture", server.id, client.id, eula));
    }
    private WorldService service(Fixture fixture, SharingService sharing, ServerInstaller installer, WorldService.Probe probe, Duration timeout) {
        return new WorldService(fixture.profiles, fixture.worlds, installer,
                (profile, host, port) -> new ProcessBuilder(command("client")).start(), sharing, probe, WorldServiceTest::process, timeout);
    }
    private ServerInstaller fixtureInstaller() { return (profile, directory) -> new ServerLaunch(command("server"), directory); }
    private WorldService.Probe realProbe() { return (host, port) -> new MineStat(host, port, 1, MineStat.Request.JSON).isServerUp(); }
    private static Process process(ServerLaunch launch) throws IOException {
        return new ProcessBuilder(launch.command).directory(launch.directory.toFile()).redirectErrorStream(true)
                .redirectOutput(launch.directory.resolve("fixture.log").toFile()).start();
    }
    private static List<String> command(String mode) {
        String java = Paths.get(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        try {
            String classes = Paths.get(WorldFixtureProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            return Arrays.asList(java, "-cp", classes, WorldFixtureProcess.class.getName(), mode);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static final class Fixture {
        final ProfileStore profiles; final WorldStore worlds; final VirtualWorld world;
        Fixture(ProfileStore profiles, WorldStore worlds, VirtualWorld world) { this.profiles = profiles; this.worlds = worlds; this.world = world; }
    }
}
