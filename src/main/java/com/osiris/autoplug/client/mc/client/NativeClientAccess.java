package com.osiris.autoplug.client.mc.client;

import com.osiris.autoplug.client.utils.GD;
import java.io.IOException;
import java.nio.file.Path;

/** UI and console must share profile-use locks and running processes. */
public final class NativeClientAccess {
    private static NativeClientContext instance;
    private static NativeClientCommands commands;
    private NativeClientAccess() { }
    public static synchronized NativeClientContext get() throws IOException {
        if (instance == null) {
            Path working = GD.WORKING_DIR.toPath();
            instance = new NativeClientContext(working.resolve("autoplug/minecraft"), working);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (commands != null) commands.close();
                try { instance.close(); } catch (IOException error) { System.err.println("Minecraft shutdown: " + error.getMessage()); }
            }, "AutoPlug-native-shutdown"));
        }
        return instance;
    }
    public static synchronized NativeClientCommands commands() throws IOException {
        if (commands == null) commands = new NativeClientCommands(get());
        return commands;
    }
}
