package com.osiris.autoplug.client.profiles;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/** Standalone commands do not initialize the legacy server or network services. */
public class LauncherEntry {
    public boolean isLauncherCommand(String[] args) {
        if (args == null || args.length == 0) return false;
        List<String> list = Arrays.asList(args);
        return args[0].equals(".profiles") || args[0].equals(".mc") || (args[0].equals(".check") || args[0].equals(".update")) && list.contains("--profile");
    }
    public int run(String[] args) {
        java.nio.file.Path root = Paths.get(System.getProperty("autoplug.home", System.getProperty("user.home") + "/.autoplug"));
        try {
            java.nio.file.Files.createDirectories(root.resolve("logs"));
            com.osiris.jlib.logger.AL.start("AP", false, root.resolve("logs/launcher.log").toFile(), true, false);
        } catch (Exception e) { System.err.println("Could not initialize launcher logging: " + e.getMessage()); return 1; }
        try (LauncherServices services = new LauncherServices(root, System.out::println)) {
            Thread hook = new Thread(services::close, "launcher-cleanup"); Runtime.getRuntime().addShutdownHook(hook);
            try {
                new LauncherCommands(services, System.out::println).execute(Arrays.asList(args)); services.waitForSessions(); return 0;
            } finally { Runtime.getRuntime().removeShutdownHook(hook); }
        } catch (Exception e) { System.err.println("AutoPlug: " + e.getMessage()); return 1; }
        finally { com.osiris.jlib.logger.AL.stop(); }
    }
}
