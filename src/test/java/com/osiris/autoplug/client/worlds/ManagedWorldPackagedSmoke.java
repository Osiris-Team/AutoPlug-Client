package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.profiles.Profile;
import com.osiris.autoplug.client.profiles.ProfileType;
import com.osiris.autoplug.client.worlds.AutoPlugWorldBootstrap;
import com.osiris.autoplug.client.worlds.ManagedServer;
import com.osiris.autoplug.client.worlds.ServerLaunch;
import com.osiris.autoplug.client.utils.MineStat;
import com.osiris.dyml.Yaml;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Offline packaged smoke helper; invoke after Maven package. Runs actual packaged AutoPlug wrappers around test-only JVM servers. */
public class ManagedWorldPackagedSmoke {
    public static void main(String[] args) throws Exception {
        Path repo = Paths.get(args[0]), root = Paths.get(args[1]); Files.createDirectories(root);
        Path java = Paths.get(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        int firstPort = port(), secondPort = port(); while (secondPort == firstPort) secondPort = port();
        Path first = Files.createDirectories(root.resolve("first world")), second = Files.createDirectories(root.resolve("second world"));
        Profile profile = new Profile(); profile.gameVersion = "1.20.4"; profile.loader = "VANILLA"; profile.type = ProfileType.MODS_SERVER;
        AutoPlugWorldBootstrap bootstrap = new AutoPlugWorldBootstrap();
        ServerLaunch a = configure(bootstrap, profile, java, repo, first, firstPort);
        ServerLaunch b = configure(bootstrap, profile, java, repo, second, secondPort);
        Process pa = start(a), pb;
        try { pb = start(b); } catch (Exception e) { pa.descendants().forEach(ProcessHandle::destroyForcibly); pa.destroyForcibly(); throw e; }
        try (ManagedServer one = new ManagedServer(pa, 15000, a.stopCommand, a.restartCommand);
             ManagedServer two = new ManagedServer(pb, 15000, b.stopCommand, b.restartCommand)) {
            ready(pa, firstPort); ready(pb, secondPort);
            long original = child(pa, first);
            if (original < 0 || child(pb, second) < 0) throw new IllegalStateException("Both wrappers must own a real test server process");
            one.command("say first world"); two.command("say second world"); one.restart();
            long deadline = System.nanoTime() + 90_000_000_000L;
            while (System.nanoTime() < deadline && (child(pa, first) == original || child(pa, first) < 0)) {
                if (!pa.isAlive()) throw new IllegalStateException("Wrapper exited on restart"); Thread.sleep(250);
            }
            if (child(pa, first) == original || child(pa, first) < 0) throw new IllegalStateException("Restart did not replace native server process");
            ready(pa, firstPort);
            if (!Files.exists(first.resolve("stopped.txt"))) throw new IllegalStateException("Restart did not gracefully save the fixture server");
            one.close();
            if (pa.isAlive() || !pb.isAlive()) throw new IllegalStateException("First stop crossed instance boundaries");
            ready(pb, secondPort);
            two.command("stop"); // Native server exit must make the wrapper auto-exit without its lifecycle lock blocking shutdown hooks.
            if (!pb.waitFor(15, TimeUnit.SECONDS))
                throw new IllegalStateException("Second wrapper did not automatically exit after raw server stop; possible shutdown-hook deadlock");
            if (pb.exitValue() != 0) throw new IllegalStateException("Second wrapper automatic exit was not clean");
            two.close();
            String secondLog = new String(Files.readAllBytes(second.resolve("wrapper.log")), StandardCharsets.UTF_8);
            if (!secondLog.contains("Stopping AutoPlug too")) throw new IllegalStateException("Automatic wrapper exit path was not exercised");
            String firstCommands = new String(Files.readAllBytes(first.resolve("commands.log")), StandardCharsets.UTF_8);
            String secondCommands = new String(Files.readAllBytes(second.resolve("commands.log")), StandardCharsets.UTF_8);
            if (!firstCommands.contains("say first world") || firstCommands.contains("say second world")
                    || !secondCommands.contains("say second world") || secondCommands.contains("say first world"))
                throw new IllegalStateException("Console commands crossed world boundaries");
            for (Path dir : Arrays.asList(first, second)) {
                if (!"graceful".equals(new String(Files.readAllBytes(dir.resolve("stopped.txt")), StandardCharsets.UTF_8))) throw new IllegalStateException("Not gracefully stopped");
                String config = new String(Files.readAllBytes(dir.resolve("autoplug/updater.yml")), StandardCharsets.UTF_8);
                if (!config.contains(AutoPlugWorldBootstrap.UPDATER_POLICY)) throw new IllegalStateException("Managed policy comment was lost");
                if (Files.exists(dir.resolve("eula.txt"))) throw new IllegalStateException("Wrapper created EULA acceptance unexpectedly");
            }
            System.out.println("PASS: actual packaged wrappers started two isolated fixture servers, restarted one, preserved the other, then saved and stopped both; raw server stop automatically exited its wrapper within 15 seconds. No Minecraft world or remote service used.");
        } finally {
            for (Process p : Arrays.asList(pa, pb)) if (p.isAlive()) { p.descendants().forEach(ProcessHandle::destroyForcibly); p.destroyForcibly(); }
        }
    }
    static ServerLaunch configure(AutoPlugWorldBootstrap bootstrap, Profile profile, Path java, Path repo, Path dir, int port) throws Exception {
        Files.write(dir.resolve("server.properties"), ("server-ip=127.0.0.1\nserver-port=" + port + "\n").getBytes(StandardCharsets.UTF_8));
        ServerLaunch launch = bootstrap.wrap(profile, new ServerLaunch(Arrays.asList(java.toString(), "-cp", repo.resolve("target/test-classes").toString(), "com.osiris.autoplug.client.worlds.WorldFixtureProcess", "server"), dir));
        // Disable animated terminal rendering for this redirected-output fixture only.
        Yaml logger = new Yaml(dir.resolve("autoplug/logger.yml").toString()); logger.load(); logger.put("logger", "tasks", "live-tasks", "enable").setValues("false"); logger.save();
        return launch;
    }
    static Process start(ServerLaunch launch) throws Exception { return new ProcessBuilder(launch.command).directory(launch.directory.toFile()).redirectErrorStream(true).redirectOutput(launch.directory.resolve("wrapper.log").toFile()).start(); }
    static int port() throws Exception { try (ServerSocket socket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) { return socket.getLocalPort(); } }
    static long child(Process process, Path directory) throws Exception {
        Path file = directory.resolve("fixture.pid");
        if (!Files.exists(file)) return -1;
        long pid = Long.parseLong(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim());
        return process.descendants().anyMatch(p -> p.pid() == pid && p.isAlive()) ? pid : -1;
    }
    static void ready(Process process, int port) throws Exception {
        long deadline = System.nanoTime() + 90_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) throw new IllegalStateException("AutoPlug wrapper exited before fixture readiness");
            if (new MineStat("127.0.0.1", port, 1, MineStat.Request.JSON).isServerUp()) return;
            Thread.sleep(250);
        }
        throw new IllegalStateException("Fixture never became ready; inspect wrapper.log");
    }
}
