package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.profiles.*;
import com.osiris.dyml.Yaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class AutoPlugWorldBootstrapTest {
    @TempDir Path temporary;

    @Test void wrapsPackagedAutoPlugWithExactArgumentsAndIndependentWrapperRuntime() throws Exception {
        Path source = jar(), world = Files.createDirectories(temporary.resolve("world with spaces"));
        Path nativeJava = Files.write(temporary.resolve("older server java.exe"), new byte[]{1});
        List<String> nativeCommand = Arrays.asList(nativeJava.toString(), "@libraries/forge/win_args.txt", "-Dnote=space and \"quoted\" value", "nogui");
        Profile profile = profile();
        ServerLaunch result = new AutoPlugWorldBootstrap(source, java()).wrap(profile, new ServerLaunch(nativeCommand, world));
        assertEquals(java().toString(), result.command.get(0)); assertNotEquals(nativeCommand.get(0), result.command.get(0));
        assertEquals(world.toAbsolutePath(), result.directory); assertEquals(".stop both", result.stopCommand); assertEquals(".restart", result.restartCommand);
        assertTrue(result.command.contains(AutoPlugWorldBootstrap.CHILD_ARGUMENT));
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(world.resolve("autoplug/AutoPlug-Client.jar")));
        assertEquals(nativeCommand, AutoPlugWorldBootstrap.readCommand(world));
        assertFalse(Files.exists(world.resolve("eula.txt")), "Wrapper must not accept an EULA");
        Yaml general = read(world, "general");
        assertEquals("MINECRAFT_SERVER", general.get("general", "autoplug", "target-software").asString());
        assertTrue(general.get("general", "autoplug", "auto-stop").asBoolean());
        assertTrue(general.get("general", "server", "auto-start").asBoolean());
        assertFalse(general.get("general", "server", "auto-eula").asBoolean());
        assertFalse(general.get("general", "server", "restart-on-crash").asBoolean());
        assertFalse(general.get("general", "autoplug", "start-on-boot").asBoolean());
        assertFalse(general.get("general", "autoplug", "system-tray", "enable").asBoolean());
        Yaml updater = read(world, "updater");
        for (String name : Arrays.asList("self-updater", "java-updater", "server-updater", "mods-updater", "plugins-updater", "global-recurring-checks"))
            assertFalse(updater.get("updater", name, "enable").asBoolean());
    }

    @Test void repeatLaunchKeepsUserConfigurationAndDoesNotTouchAnotherWorld() throws Exception {
        Path source = jar(), first = Files.createDirectories(temporary.resolve("first")), second = Files.createDirectories(temporary.resolve("second"));
        AutoPlugWorldBootstrap bootstrap = new AutoPlugWorldBootstrap(source, java()); Profile profile = profile();
        bootstrap.wrap(profile, new ServerLaunch(Arrays.asList(java().toString(), "-version"), first));
        bootstrap.wrap(profile, new ServerLaunch(Arrays.asList(java().toString(), "-version"), second));
        byte[] otherConfig = Files.readAllBytes(second.resolve("autoplug/general.yml"));
        Yaml general = read(first, "general"); general.put("general", "server", "key").setValues("fixture-personal-key"); general.save();
        Yaml backup = read(first, "backup"); backup.put("backup", "enable").setValues("true"); backup.save();
        bootstrap.wrap(profile, new ServerLaunch(Arrays.asList(java().toString(), "-version"), first));
        assertEquals("fixture-personal-key", read(first, "general").get("general", "server", "key").asString());
        assertTrue(read(first, "backup").get("backup", "enable").asBoolean());
        assertArrayEquals(otherConfig, Files.readAllBytes(second.resolve("autoplug/general.yml")));
    }

    @Test void unbundledDevelopmentLaunchFailsClearlyBeforeMakingAnInstance() throws Exception {
        Path world = Files.createDirectories(temporary.resolve("world"));
        IOException error = assertThrows(IOException.class, () -> new AutoPlugWorldBootstrap(temporary, java()).wrap(profile(), new ServerLaunch(Arrays.asList(java().toString(), "-version"), world)));
        assertTrue(error.getMessage().contains("packaged AutoPlug JAR"));
        assertFalse(Files.exists(world.resolve("autoplug")));
    }

    @Test void independentRealConsoleProcessesReceiveOnlyTheirOwnCommandsAndStopGracefully() throws Exception {
        Path first = Files.createDirectories(temporary.resolve("first")), second = Files.createDirectories(temporary.resolve("second"));
        Process a = console(first), b = console(second);
        try (ManagedServer one = new ManagedServer(a, 5000, ".stop both", ".restart");
             ManagedServer two = new ManagedServer(b, 5000, ".stop both", ".restart")) {
            one.command("say first world"); two.command("say second world"); one.restart();
            assertThrows(IOException.class, () -> one.command("say invalid\nstop"));
            one.close(); assertFalse(a.isAlive()); assertTrue(b.isAlive());
            two.close(); assertFalse(b.isAlive());
            assertEquals(Arrays.asList("say first world", ".restart", ".stop both"), Files.readAllLines(first.resolve("commands.txt"), StandardCharsets.UTF_8));
            assertEquals(Arrays.asList("say second world", ".stop both"), Files.readAllLines(second.resolve("commands.txt"), StandardCharsets.UTF_8));
            assertThrows(IOException.class, () -> one.command("say closed"));
        } finally { if (a.isAlive()) a.destroyForcibly(); if (b.isAlive()) b.destroyForcibly(); }
    }

    private Process console(Path directory) throws Exception {
        Path classes = Paths.get(ManagedConsoleFixtureProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return new ProcessBuilder(java().toString(), "-cp", classes.toString(), ManagedConsoleFixtureProcess.class.getName())
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve("fixture.log").toFile()).start();
    }
    private Path jar() throws Exception {
        Path file = temporary.resolve("AutoPlug fixture.jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new ZipEntry("com/osiris/autoplug/client/Main.class")); output.write(new byte[]{1, 2, 3}); output.closeEntry();
        }
        return file;
    }
    private Profile profile() { Profile p = new Profile(); p.gameVersion = "1.20.4"; p.loader = "VANILLA"; p.type = ProfileType.MODS_SERVER; return p; }
    private static Path java() { return Paths.get(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toAbsolutePath().normalize(); }
    private Yaml read(Path world, String name) throws Exception { Yaml yaml = new Yaml(world.resolve("autoplug/" + name + ".yml").toString()); yaml.load(); return yaml; }
}
