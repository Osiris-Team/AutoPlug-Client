/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.browser.*;
import com.osiris.autoplug.client.clientapp.*;
import com.osiris.autoplug.client.launcher.*;
import com.osiris.autoplug.client.profiles.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs original child JVM fixtures and loopback MineStat readiness; never launches Minecraft or maps a real router. */
class VirtualWorldServiceTest {
    @TempDir Path temporary;
    private Path java() { return Paths.get(System.getProperty("java.home"), "bin", LauncherPlatform.current().os.equals("windows") ? "java.exe" : "java"); }
    private WorldStore store() throws IOException {
        ProfileStore profiles = new ProfileStore(temporary.resolve("autoplug")); profiles.create("paper", Profile.Type.PLUGINS, "1.20.4", "PAPER", false); return new WorldStore(profiles.getRoot(), profiles);
    }
    private WorldDefinition readyWorld(WorldStore store) throws IOException {
        WorldDefinition world = store.create("Fixture world", "paper"); store.setServerExecutable(world.id, WorldServerPreparerTest.fixtureJar(temporary, WorldFixtureProcesses.class.getName(), "1.20.4")); return store.acceptEula(world.id, true);
    }
    private WorldServerPreparer preparer() throws IOException { return WorldServerPreparerTest.offlinePreparer(temporary.resolve("cache"), java(), new ArrayList<>(), false); }
    private Process client(WorldStore store, WorldDefinition world, String id, ServerAddress address, long delay) throws IOException {
        Profile profile = store.getProfiles().get(id);
        return new ProcessBuilder(java().toString(), "-jar", world.serverExecutable.toString(), "client", address.host + ":" + address.port, Long.toString(delay)).directory(profile.directoryPath.toFile()).start();
    }
    private void awaitClosed(VirtualWorldService.RunningWorld running) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (!running.isClosed() && System.nanoTime() < deadline) Thread.sleep(40);
        assertTrue(running.isClosed(), "World lifecycle did not clean up"); assertFalse(running.serverProcess().isAlive());
        if (running.clientProcess() != null) assertFalse(running.clientProcess().isAlive());
    }
    @Test void realChildServerAndNativeServiceClientBridgeUseLocalPortThenGracefulStopAndUnmapOnClientExit() throws Exception {
        WorldStore store = store(); WorldDefinition world = readyWorld(store); AtomicInteger mapped = new AtomicInteger(), unmapped = new AtomicInteger();
        NativeClientService nativeClient = new NativeClientService(store.getProfiles(), new ServerCatalog(store.getRoot()), new ClientSettings(store.getRoot()), (profile, account, address) -> {
            assertEquals(Profile.Type.MODS, profile.type); assertEquals("VANILLA", profile.loader); assertEquals("1.20.4", profile.gameVersion); assertEquals("Player", account.getName());
            return client(store, world, profile.id, address, 1400);
        }, new NativeClientService.Migration() {
            public ProfileUpdater.Plan check(Profile profile) { throw new AssertionError("No world migration requested"); }
            public void apply(ProfileUpdater.Plan plan, boolean allowDisabling) { throw new AssertionError("No world migration requested"); }
        });
        VirtualWorldService.Sharing sharing = port -> {
            mapped.incrementAndGet(); assertTrue(port > 0);
            return new VirtualWorldService.Mapping() { public String joinAddress() { return "198.51.100.10:" + port; } public void close() { unmapped.incrementAndGet(); } };
        };
        try (VirtualWorldService service = new VirtualWorldService(store, preparer(), nativeClient::launch, sharing, Duration.ofSeconds(8), Duration.ofSeconds(3))) {
            VirtualWorldService.RunningWorld active = service.launch(world.id, true);
            assertEquals("127.0.0.1", active.address.host); assertTrue(active.serverProcess().isAlive()); assertNotNull(active.publicAddress()); assertEquals(1, mapped.get());
            assertThrows(IOException.class, () -> service.launch(world.id, false)); assertThrows(IOException.class, () -> store.delete(world.id));
            assertTrue(active.clientProcess().waitFor(8, TimeUnit.SECONDS)); assertEquals(0, active.clientProcess().exitValue()); awaitClosed(active);
            assertEquals(1, unmapped.get()); assertFalse(service.isRunning(world.id)); assertTrue(Files.exists(world.directoryPath.resolve("fixture-stopped.txt")));
            assertTrue(active.recentOutput().size() <= 80); for (String line : active.recentOutput()) assertTrue(line.length() <= 2048);
            Profile clientProfile = store.getProfiles().get("world-client-" + world.id); Properties record = new Properties();
            try (InputStream input = Files.newInputStream(clientProfile.directoryPath.resolve("fixture-client.properties"))) { record.load(input); }
            assertEquals("127.0.0.1:" + active.address.port, record.getProperty("address")); assertEquals(clientProfile.directoryPath.toString(), record.getProperty("workingDirectory"));
            assertFalse(Files.exists(temporary.resolve("server.properties"))); assertFalse(Files.exists(store.getProfiles().get("paper").directoryPath.resolve("server.properties")));
        }
    }
    @Test void failedUpnpFallsBackToLocalGameWithNoticeAndLoopbackOnlyWorldRequiresRestartToShare() throws Exception {
        WorldStore store = store(); WorldDefinition world = readyWorld(store);
        try (VirtualWorldService service = new VirtualWorldService(store, preparer(), (id, address) -> client(store, world, id, address, 2500), port -> { throw new IOException("Fixture router unavailable"); }, Duration.ofSeconds(8), Duration.ofSeconds(2))) {
            VirtualWorldService.RunningWorld active = service.launch(world.id, true);
            assertNull(active.publicAddress()); assertTrue(active.shareFailure().contains("Fixture router unavailable")); assertTrue(active.shareFailure().contains("CGNAT"));
            active.close(); awaitClosed(active);
            VirtualWorldService.RunningWorld local = service.launch(world.id, false);
            IOException failure = assertThrows(IOException.class, () -> service.share(world.id)); assertTrue(failure.getMessage().contains("Restart"));
            local.close(); awaitClosed(local);
        }
    }
    @Test void clientPreparationFailureStopsRealServerAndRemovesMapping() throws Exception {
        WorldStore store = store(); WorldDefinition world = readyWorld(store); AtomicReference<VirtualWorldService> owner = new AtomicReference<>(); AtomicReference<VirtualWorldService.RunningWorld> observed = new AtomicReference<>(); AtomicInteger unmapped = new AtomicInteger();
        WorldServerPreparer production = preparer();
        try (VirtualWorldService service = new VirtualWorldService(store, (definition, port, share) -> { observed.set(owner.get().running(definition.id)); return production.prepare(definition, port, share); }, (id, address) -> { throw new IOException("Fixture client preparation failed"); }, port -> new VirtualWorldService.Mapping() { public String joinAddress() { return "198.51.100.10:" + port; } public void close() { unmapped.incrementAndGet(); } }, Duration.ofSeconds(8), Duration.ofSeconds(2))) {
            owner.set(service); assertThrows(IOException.class, () -> service.launch(world.id, true)); awaitClosed(observed.get()); assertEquals(1, unmapped.get()); assertFalse(service.isRunning(world.id)); assertTrue(Files.exists(world.directoryPath.resolve("fixture-stopped.txt")));
        }
    }
    @Test void readinessFailureAndEarlyServerExitCleanUpWithoutLaunchingClient() throws Exception {
        WorldStore store = store(); WorldDefinition world = readyWorld(store); AtomicReference<VirtualWorldService> owner = new AtomicReference<>(); AtomicReference<VirtualWorldService.RunningWorld> observed = new AtomicReference<>(); WorldServerPreparer production = preparer(); AtomicInteger clientCalls = new AtomicInteger();
        try (VirtualWorldService service = new VirtualWorldService(store, (definition, port, share) -> { observed.set(owner.get().running(definition.id)); return production.prepare(definition, port, share); }, (id, address) -> { clientCalls.incrementAndGet(); throw new AssertionError("Unready server must not launch a client"); }, null, Duration.ofMillis(1300), Duration.ofSeconds(2))) {
            owner.set(service);
            Files.write(world.directoryPath.resolve("fixture-mode.txt"), Collections.singletonList("silent")); assertThrows(IOException.class, () -> service.launch(world.id, false)); awaitClosed(observed.get()); assertEquals(0, clientCalls.get());
            Files.write(world.directoryPath.resolve("fixture-mode.txt"), Collections.singletonList("exit")); assertThrows(IOException.class, () -> service.launch(world.id, false)); awaitClosed(observed.get()); assertEquals(0, clientCalls.get());
        }
    }
    @Test void startupCancellationKillsCreatedServerAndNeverStartsClientAndStopAllKillsStubbornChildren() throws Exception {
        WorldStore store = store(); WorldDefinition world = readyWorld(store); AtomicReference<VirtualWorldService> owner = new AtomicReference<>(); AtomicReference<VirtualWorldService.RunningWorld> observed = new AtomicReference<>(); WorldServerPreparer production = preparer(); AtomicInteger clientCalls = new AtomicInteger();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (VirtualWorldService service = new VirtualWorldService(store, (definition, port, share) -> { observed.set(owner.get().running(definition.id)); return production.prepare(definition, port, share); }, (id, address) -> { clientCalls.incrementAndGet(); return client(store, world, id, address, 20000); }, null, Duration.ofSeconds(8), Duration.ofMillis(150))) {
            owner.set(service); Files.write(world.directoryPath.resolve("fixture-mode.txt"), Collections.singletonList("silent"));
            Future<?> launching = worker.submit(() -> { try { service.launch(world.id, false); throw new AssertionError("Cancelled startup unexpectedly completed"); } catch (InterruptedException cancelled) { } catch (Exception failed) { throw new CompletionException(failed); } });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
            while ((observed.get() == null || observed.get().serverProcess() == null) && System.nanoTime() < deadline) Thread.sleep(30);
            assertNotNull(observed.get()); assertNotNull(observed.get().serverProcess()); service.stop(world.id); launching.get(8, TimeUnit.SECONDS); awaitClosed(observed.get()); assertEquals(0, clientCalls.get());
            Files.write(world.directoryPath.resolve("fixture-mode.txt"), Collections.singletonList("stubborn")); VirtualWorldService.RunningWorld active = service.launch(world.id, false);
            service.stopAll(); awaitClosed(active); assertFalse(service.isRunning(world.id));
        } finally { worker.shutdownNow(); }
    }
    @Test void explicitEulaAndServerModClientConfigurationAreRequiredBeforeAnyPreparation() throws Exception {
        WorldStore store = store(); WorldDefinition unaccepted = store.create("No EULA", "paper"); AtomicInteger preparations = new AtomicInteger();
        try (VirtualWorldService service = new VirtualWorldService(store, (world, port, share) -> { preparations.incrementAndGet(); throw new AssertionError("Unaccepted world must not be prepared"); }, (id, address) -> { throw new AssertionError("Unaccepted world must not launch a client"); }, null, Duration.ofSeconds(2), Duration.ofSeconds(1))) {
            assertThrows(IOException.class, () -> service.launch(unaccepted.id, false)); assertEquals(0, preparations.get()); assertFalse(Files.exists(unaccepted.directoryPath.resolve("eula.txt")));
            store.getProfiles().create("servermods", Profile.Type.MODS_SERVER, "1.20.4", "FABRIC", false); WorldDefinition mods = store.create("mods", "servermods"); store.acceptEula(mods.id, true);
            assertThrows(IOException.class, () -> service.launch(mods.id, false)); assertEquals(0, preparations.get());
        }
    }
    @Test void longWorldIdsGenerateSeparateClientProfilesInsteadOfSharingTruncatedConfigDirectories() throws Exception {
        WorldStore store = store(); String prefix = String.join("", Collections.nCopies(65, "x"));
        WorldDefinition first = store.create(prefix + "a", "first", "paper"), second = store.create(prefix + "b", "second", "paper");
        store.acceptEula(first.id, true); store.acceptEula(second.id, true);
        try (VirtualWorldService service = new VirtualWorldService(store, (world, port, share) -> { throw new IOException("Fixture stops after client profile allocation"); }, (id, address) -> { throw new AssertionError("No client spawn expected"); }, null, Duration.ofSeconds(2), Duration.ofSeconds(1))) {
            assertThrows(IOException.class, () -> service.launch(first.id, false)); assertThrows(IOException.class, () -> service.launch(second.id, false));
            List<Profile> clients = new ArrayList<>(); for (Profile profile : store.getProfiles().list()) if (profile.type == Profile.Type.MODS) clients.add(profile);
            assertEquals(2, clients.size()); assertNotEquals(clients.get(0).id, clients.get(1).id); assertNotEquals(clients.get(0).directoryPath, clients.get(1).directoryPath);
            Files.write(clients.get(0).directoryPath.resolve("options.txt"), Collections.singletonList("first world only")); assertFalse(Files.exists(clients.get(1).directoryPath.resolve("options.txt")));
        }
    }
    @Test void configuredPortIsUsedAndAnOccupiedConfiguredPortFailsWithoutSilentlyChoosingAnother() throws Exception {
        WorldStore store = store(); WorldDefinition world = readyWorld(store); int preferred;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { preferred = socket.getLocalPort(); }
        try (VirtualWorldService service = new VirtualWorldService(store, preparer(), (id, address) -> client(store, world, id, address, 20000), null, () -> preferred, () -> true, Duration.ofSeconds(8), Duration.ofSeconds(2))) {
            VirtualWorldService.RunningWorld active = service.launch(world.id, false); assertEquals(preferred, active.address.port);
            WorldDefinition second = store.create("second", "paper"); store.acceptEula(second.id, true);
            IOException occupied = assertThrows(IOException.class, () -> service.launch(second.id, false)); assertTrue(occupied.getMessage().contains(Integer.toString(preferred))); assertTrue(occupied.getMessage().contains("unavailable"));
            assertFalse(service.isRunning(second.id)); assertTrue(active.serverProcess().isAlive()); active.close(); awaitClosed(active);
        }
    }
    @Test void disabledUpnpPreferencePreventsSharingBeforeAnyPreparationOrRouterCall() throws Exception {
        WorldStore store = store(); WorldDefinition world = readyWorld(store); AtomicInteger preparations = new AtomicInteger(), routerCalls = new AtomicInteger();
        try (VirtualWorldService service = new VirtualWorldService(store, (definition, port, share) -> { preparations.incrementAndGet(); throw new AssertionError("Sharing is disabled"); }, (id, address) -> { throw new AssertionError("No client expected"); }, port -> { routerCalls.incrementAndGet(); throw new AssertionError("No router request expected"); }, () -> 0, () -> false, Duration.ofSeconds(2), Duration.ofSeconds(1))) {
            IOException disabled = assertThrows(IOException.class, () -> service.launch(world.id, true)); assertTrue(disabled.getMessage().contains("disabled")); assertEquals(0, preparations.get()); assertEquals(0, routerCalls.get()); assertFalse(service.isRunning(world.id));
        }
    }
    @Test void closeWaitsForCancelledStartupCleanupRatherThanJoiningItsReusableWorkerThread() throws Exception {
        WorldStore store = store(); WorldDefinition world = readyWorld(store); CountDownLatch entered = new CountDownLatch(1); AtomicReference<VirtualWorldService.RunningWorld> active = new AtomicReference<>(); AtomicReference<VirtualWorldService> owner = new AtomicReference<>(); ExecutorService worker = Executors.newSingleThreadExecutor();
        try (VirtualWorldService service = new VirtualWorldService(store, (definition, port, share) -> { active.set(owner.get().running(definition.id)); entered.countDown(); new CountDownLatch(1).await(); throw new AssertionError("Cancelled startup advanced"); }, (id, address) -> { throw new AssertionError("Cancelled startup must not spawn a client"); }, null, Duration.ofSeconds(2), Duration.ofSeconds(1))) {
            owner.set(service);
            Future<Boolean> result = worker.submit(() -> { try { service.launch(world.id, false); return false; } catch (InterruptedException cancelled) { return true; } });
            assertTrue(entered.await(5, TimeUnit.SECONDS)); assertThrows(IOException.class, () -> store.delete(world.id));
            long start = System.nanoTime(); service.close();
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000, "Close must wait for the startup operation, not the reusable executor thread");
            assertTrue(result.get(3, TimeUnit.SECONDS)); assertTrue(active.get().isClosed()); assertNull(active.get().serverProcess()); assertFalse(service.isRunning(world.id));
            store.delete(world.id); assertFalse(Files.exists(world.directoryPath));
        } finally { worker.shutdownNow(); }
    }
    @Test void oneShotWorldCommandWaitsForActualFixtureClientExitThenStopsServerAndClosesMapping() throws Exception {
        WorldStore store = store(); WorldDefinition world = readyWorld(store); AtomicReference<VirtualWorldService> owner = new AtomicReference<>(); AtomicReference<VirtualWorldService.RunningWorld> active = new AtomicReference<>(); AtomicInteger unmapped = new AtomicInteger();
        try (VirtualWorldService service = new VirtualWorldService(store, preparer(), (id, address) -> { active.set(owner.get().running(world.id)); return client(store, world, id, address, 650); }, port -> new VirtualWorldService.Mapping() { public String joinAddress() { return "198.51.100.10:" + port; } public void close() { unmapped.incrementAndGet(); } }, Duration.ofSeconds(8), Duration.ofSeconds(2))) {
            owner.set(service); List<String> output = new ArrayList<>();
            assertTrue(new WorldCommands(store, service).handle(".mc worlds launch " + world.id + " --share", output::add, true));
            assertNotNull(active.get()); awaitClosed(active.get()); assertEquals(0, active.get().clientProcess().exitValue()); assertEquals(1, unmapped.get());
            assertTrue(Files.exists(world.directoryPath.resolve("fixture-stopped.txt"))); assertFalse(service.isRunning(world.id));
            assertTrue(output.stream().anyMatch(line -> line.startsWith("World client started: 127.0.0.1:"))); assertTrue(output.stream().anyMatch(line -> line.startsWith("Join address: 198.51.100.10:"))); assertFalse(output.stream().anyMatch(line -> line.startsWith("World command failed")));
        }
    }
    @Test void oneShotCommandOutputFailureImmediatelyAfterLaunchCleansBothRealChildrenAndMapping() throws Exception {
        WorldStore store = store(); WorldDefinition world = readyWorld(store); AtomicReference<VirtualWorldService> owner = new AtomicReference<>(); AtomicReference<VirtualWorldService.RunningWorld> active = new AtomicReference<>(); AtomicInteger unmapped = new AtomicInteger(); AtomicBoolean rejectOutput = new AtomicBoolean(true);
        try (VirtualWorldService service = new VirtualWorldService(store, preparer(), (id, address) -> { active.set(owner.get().running(world.id)); return client(store, world, id, address, 20000); }, port -> new VirtualWorldService.Mapping() { public String joinAddress() { return "198.51.100.10:" + port; } public void close() { unmapped.incrementAndGet(); } }, Duration.ofSeconds(8), Duration.ofSeconds(2))) {
            owner.set(service); List<String> failureOutput = new ArrayList<>();
            assertTrue(new WorldCommands(store, service).handle(".mc worlds launch " + world.id + " --share", line -> { if (rejectOutput.getAndSet(false)) throw new IllegalStateException("Fixture output consumer failed"); failureOutput.add(line); }, true));
            assertNotNull(active.get()); awaitClosed(active.get()); assertEquals(1, unmapped.get()); assertFalse(service.isRunning(world.id)); assertTrue(Files.exists(world.directoryPath.resolve("fixture-stopped.txt")));
            assertTrue(failureOutput.stream().anyMatch(line -> line.contains("Fixture output consumer failed")));
        }
    }
    @Test void stoppingBlockedClientPreparationImmediatelyUnmapsWhileRetainingServerOwnershipUntilFinalCleanup() throws Exception {
        WorldStore store = store(); WorldDefinition world = readyWorld(store); CountDownLatch clientPreparing = new CountDownLatch(1), releaseClient = new CountDownLatch(1); AtomicReference<VirtualWorldService> owner = new AtomicReference<>(); AtomicReference<VirtualWorldService.RunningWorld> active = new AtomicReference<>(); AtomicInteger unmapped = new AtomicInteger(); ExecutorService worker = Executors.newSingleThreadExecutor();
        try (VirtualWorldService service = new VirtualWorldService(store, preparer(), (id, address) -> {
            active.set(owner.get().running(world.id)); clientPreparing.countDown(); boolean interrupted = false;
            while (releaseClient.getCount() > 0) try { releaseClient.await(); } catch (InterruptedException cancelled) { interrupted = true; }
            if (interrupted) { Thread.currentThread().interrupt(); throw new InterruptedException("Fixture client preparation released after cancellation"); }
            throw new AssertionError("Blocked client preparation was not cancelled");
        }, port -> new VirtualWorldService.Mapping() { public String joinAddress() { return "198.51.100.10:" + port; } public void close() { unmapped.incrementAndGet(); } }, Duration.ofSeconds(8), Duration.ofSeconds(2))) {
            owner.set(service);
            Future<Boolean> result = worker.submit(() -> { try { service.launch(world.id, true); return false; } catch (InterruptedException cancelled) { return true; } });
            assertTrue(clientPreparing.await(8, TimeUnit.SECONDS)); assertNotNull(active.get().publicAddress()); service.stop(world.id);
            assertEquals(1, unmapped.get(), "Owned mapping must close even while client preparation ignores interruption"); assertNull(active.get().publicAddress()); assertFalse(active.get().serverProcess().isAlive());
            assertFalse(result.isDone()); assertFalse(active.get().isClosed()); assertThrows(IOException.class, () -> store.delete(world.id));
            releaseClient.countDown(); assertTrue(result.get(5, TimeUnit.SECONDS)); awaitClosed(active.get()); assertEquals(1, unmapped.get(), "Final cleanup must not unmap twice");
            assertFalse(service.isRunning(world.id)); store.delete(world.id); assertFalse(Files.exists(world.directoryPath));
        } finally { releaseClient.countDown(); worker.shutdownNow(); }
    }
}
