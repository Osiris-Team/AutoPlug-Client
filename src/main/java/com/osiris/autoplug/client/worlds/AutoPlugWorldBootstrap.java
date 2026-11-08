package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.profiles.JsonFiles;
import com.osiris.autoplug.client.profiles.Profile;
import com.osiris.dyml.Yaml;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Each managed world runs a real, isolated copy of AutoPlug's server wrapper. */
public final class AutoPlugWorldBootstrap {
    public static final String CHILD_ARGUMENT = "--managed-world-child";
    public static final String UPDATER_POLICY = "Managed world: automatic legacy updates stay disabled to preserve the selected profile, exact game version and runtime. Use the parent launcher's profile update workflow.";
    public static final String RESTARTER_POLICY = "Managed world: the parent launcher owns this session. Scheduled restarts stay disabled; use Restart in its server console.";
    public static final String COMMAND_POLICY = "Managed world: informational preview. The parent launcher supplies the exact argument vector in managed-server-command.json on each launch.";
    private static volatile List<String> childCommand;
    private final Path sourceJar, wrapperJava;
    static class LaunchMetadata { public List<String> command = new ArrayList<>(); }

    public AutoPlugWorldBootstrap() { this.sourceJar = null; this.wrapperJava = null; }
    AutoPlugWorldBootstrap(Path sourceJar, Path wrapperJava) { this.sourceJar = sourceJar; this.wrapperJava = wrapperJava; }

    public ServerLaunch wrap(Profile profile, ServerLaunch server) throws Exception {
        Path source = sourceJar == null ? Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()) : sourceJar;
        if (!Files.isRegularFile(source) || !source.getFileName().toString().endsWith(".jar"))
            throw new IOException("Managed worlds require the packaged AutoPlug JAR. Run the built AutoPlug-Client.jar, rather than a classes directory.");
        try (JarFile jar = new JarFile(source.toFile())) {
            if (jar.getEntry("com/osiris/autoplug/client/Main.class") == null) throw new IOException("The wrapper JAR does not contain AutoPlug");
        }
        Path java = wrapperJava == null ? Paths.get(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java") : wrapperJava;
        java = java.toAbsolutePath().normalize();
        if (!Files.isRegularFile(java)) throw new IOException("AutoPlug wrapper Java executable is unavailable: " + java);
        validateCommand(server.command);
        Path config = server.directory.resolve("autoplug");
        if (Files.isSymbolicLink(config)) throw new IOException("World AutoPlug configuration must not be a symbolic link");
        Files.createDirectories(config);
        Path copy = config.resolve("AutoPlug-Client.jar");
        if (Files.isSymbolicLink(copy)) throw new IOException("World AutoPlug JAR must not be a symbolic link");
        Path temporary = Files.createTempFile(config, ".autoplug-copy-", ".jar");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, copy, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, copy, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        LaunchMetadata metadata = new LaunchMetadata(); metadata.command.addAll(server.command);
        Path commandFile = config.resolve("managed-server-command.json");
        if (Files.isSymbolicLink(commandFile)) throw new IOException("Managed command file must not be a link");
        new JsonFiles().write(commandFile, metadata);
        configure(config, profile, server.command);
        List<String> command = Arrays.asList(java.toString(), "-Djava.awt.headless=true",
                "-Dautoplug.home=" + config.resolve("launcher"), "-jar", copy.toString(), CHILD_ARGUMENT);
        return new ServerLaunch(command, server.directory, ".stop both", ".restart");
    }

    private void configure(Path directory, Profile profile, List<String> command) throws Exception {
        Yaml general = yaml(directory, "general");
        general.put("general", "autoplug", "target-software").setValues("MINECRAFT_SERVER");
        general.put("general", "autoplug", "auto-stop").setValues("true");
        general.put("general", "autoplug", "start-on-boot").setValues("false");
        general.put("general", "autoplug", "system-tray", "enable").setValues("false");
        general.put("general", "server", "auto-start").setValues("true");
        general.put("general", "server", "auto-eula").setValues("false");
        general.put("general", "server", "restart-on-crash").setValues("false");
        general.put("general", "server", "version").setValues(profile.gameVersion);
        general.put("general", "server", "start-command").setValues(displayCommand(command))
                .setComments(COMMAND_POLICY);
        general.put("general", "server", "stop-command").setValues("stop");
        general.put("general", "directory-cleaner", "enabled").setDefValues("false");
        general.save();
        Yaml updater = yaml(directory, "updater");
        for (String name : Arrays.asList("global-recurring-checks", "self-updater", "java-updater", "server-updater", "plugins-updater", "mods-updater"))
            updater.put("updater", name, "enable").setValues("false").setComments(UPDATER_POLICY);
        updater.save();
        Yaml restarter = yaml(directory, "restarter");
        restarter.put("restarter", "daily-restarter", "enable").setValues("false").setComments(RESTARTER_POLICY);
        restarter.put("restarter", "custom-restarter", "enable").setValues("false").setComments(RESTARTER_POLICY); restarter.save();
        Yaml backup = yaml(directory, "backup"); backup.put("backup", "enable").setDefValues("false"); backup.save();
    }
    private Yaml yaml(Path directory, String name) throws Exception {
        Path path = directory.resolve(name + ".yml");
        if (Files.isSymbolicLink(path)) throw new IOException("World configuration must not be a symbolic link: " + name);
        Yaml yaml = new Yaml(path.toString()); yaml.load(); return yaml;
    }
    private String displayCommand(List<String> command) {
        // This is informational only; Server uses the exact persisted vector for this child.
        List<String> parts = new ArrayList<>(); for (String value : command) parts.add('"' + value.replace("\"", "\\\"") + '"');
        return String.join(" ", parts);
    }
    public static boolean configureIfChild(String[] args) throws IOException {
        if (args == null || args.length == 0 || !CHILD_ARGUMENT.equals(args[0])) return false;
        if (args.length != 1) throw new IOException("Managed child accepts only its world-local command file");
        childCommand = readCommand(Paths.get(System.getProperty("user.dir")));
        return true;
    }
    static List<String> readCommand(Path directory) throws IOException {
        Path config = directory.resolve("autoplug"), file = config.resolve("managed-server-command.json");
        if (Files.isSymbolicLink(config) || Files.isSymbolicLink(file)) throw new IOException("Managed command path must not be linked");
        LaunchMetadata metadata = new JsonFiles().read(file, LaunchMetadata.class);
        validateCommand(metadata.command);
        return Collections.unmodifiableList(new ArrayList<>(metadata.command));
    }
    private static void validateCommand(List<String> command) throws IOException {
        if (command == null || command.isEmpty() || command.size() > 256) throw new IOException("Invalid managed server argument vector");
        for (String argument : command) if (argument == null || argument.length() > 32768 || argument.indexOf('\0') >= 0)
            throw new IOException("Invalid managed server argument");
        Path executable = Paths.get(command.get(0));
        if (!executable.isAbsolute() || !Files.isRegularFile(executable)) throw new IOException("Managed server executable must be an existing absolute path");
    }
    public static boolean isChild() { return childCommand != null; }
    public static List<String> childCommand() { return childCommand; }
}
