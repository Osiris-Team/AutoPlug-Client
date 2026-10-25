package com.osiris.autoplug.legacy;

import java.io.InputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java 8 bootstrap for unmodified, pre-Quick-Play vanilla clients. No transformer,
 * replacement game classes, save conversion, or input/keyboard automation is used.
 * Member names come from exact-version publisher mappings or a pinned, bytecode-verified adapter.
 */
public final class LegacyWorldAgent {
    private LegacyWorldAgent() { }

    public static void premain(String configuration, Instrumentation instrumentation) throws Exception {
        final Properties names = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get(configuration))) { names.load(input); }
        for (String key : new String[]{"minecraft", "instance", "screen", "title", "load", "world", "gameDir"})
            if (names.getProperty(key, "").isEmpty()) throw new IllegalArgumentException("Missing legacy launch setting: " + key);
        String world = names.getProperty("world");
        if (world.equals(".") || world.equals("..") || world.indexOf('/') >= 0 || world.indexOf('\\') >= 0 || world.indexOf(':') >= 0)
            throw new IllegalArgumentException("Invalid save folder.");
        validateSave(names);
        final AtomicBoolean finished = new AtomicBoolean();
        final AtomicBoolean queued = new AtomicBoolean();
        Thread watcher = new Thread(() -> {
            long deadline = System.nanoTime() + 300_000_000_000L;
            try {
                Class<?> minecraft = null;
                while (!finished.get() && System.nanoTime() < deadline) {
                    // Do not proactively load game classes. Discover classes loaded by
                    // the normal entry point; reflective access also waits for class init.
                    if (minecraft == null) for (Class<?> loaded : instrumentation.getAllLoadedClasses())
                        if (loaded.getName().equals(names.getProperty("minecraft"))) { minecraft = loaded; break; }
                    if (minecraft != null) {
                        Object instance = method(minecraft, names.getProperty("instance")).invoke(null);
                        if (instance != null && queued.compareAndSet(false, true)) {
                            Runnable action = () -> {
                                try {
                                    if (!finished.get()) tryOpen(instance, names, () -> {
                                        if (!finished.compareAndSet(false, true)) throw new IllegalStateException("Automatic world entry was cancelled before handoff.");
                                    });
                                } catch (Throwable failure) {
                                    finished.set(true);
                                    reportFailure(failure);
                                } finally { queued.set(false); }
                            };
                            if (names.getProperty("scheduler") != null)
                                method(minecraft, names.getProperty("scheduler"), Runnable.class).invoke(instance, action);
                            else if (instance instanceof Executor) ((Executor) instance).execute(action);
                            else throw new IllegalStateException("Minecraft does not expose the expected main-thread executor.");
                        }
                    }
                    Thread.sleep(250);
                }
                if (finished.compareAndSet(false, true)) {
                    System.err.println("[AutoPlug] Automatic world entry timed out while waiting for Minecraft's title screen. Check the game's startup or consent screen; no save was changed by the helper.");
                }
            } catch (Throwable failure) { finished.set(true); reportFailure(failure); }
        }, "AutoPlug legacy world entry");
        watcher.setDaemon(true);
        watcher.start();
        System.out.println("[AutoPlug] Automatic world entry is waiting for Minecraft to finish loading.");
    }

    /** Must run on the Minecraft executor. Returning false preserves startup/consent screens. */
    public static boolean tryOpen(Object minecraft, Properties names) throws Exception {
        return tryOpen(minecraft, names, () -> { });
    }

    private static boolean tryOpen(Object minecraft, Properties names, Runnable beforeLoad) throws Exception {
        Field screenField = minecraft.getClass().getDeclaredField(names.getProperty("screen"));
        screenField.setAccessible(true);
        Object screen = screenField.get(minecraft);
        if (screen == null || !screen.getClass().getName().equals(names.getProperty("title"))) return false;
        if (names.getProperty("overlay") != null && method(minecraft.getClass(), names.getProperty("overlay")).invoke(minecraft) != null) return false;
        // Recheck after a potentially long game startup. A removed or replaced save
        // must not become a newly created world or an external-directory write.
        validateSave(names);
        String flow = names.getProperty("flows", "");
        if (names.getProperty("levelSource") != null) {
            Object source = method(minecraft.getClass(), names.getProperty("levelSource")).invoke(minecraft);
            String folder = names.getProperty("world");
            if (!Boolean.TRUE.equals(method(source.getClass(), names.getProperty("levelExists"), String.class).invoke(source, folder)))
                throw new IOException("The selected save no longer exists; refusing to create a replacement.");
            Object summaries = method(source.getClass(), names.getProperty("levelList")).invoke(source);
            String displayName = null;
            for (Object summary : (Iterable<?>) summaries) {
                if (folder.equals(method(summary.getClass(), names.getProperty("levelId")).invoke(summary))) {
                    displayName = (String) method(summary.getClass(), names.getProperty("levelName")).invoke(summary); break;
                }
            }
            if (displayName == null) throw new IOException("Minecraft could not read the selected save's original name.");
            Class<?> settings = Class.forName(names.getProperty("settingsType"), false, minecraft.getClass().getClassLoader());
            // 1.14/1.15 write this display name back. Match WorldListEntry exactly;
            // substituting the directory name would silently rename the save.
            beforeLoad.run();
            method(minecraft.getClass(), names.getProperty("load"), String.class, String.class, settings).invoke(minecraft, folder, displayName, null);
        } else if (flow.isEmpty()) {
            beforeLoad.run();
            method(minecraft.getClass(), names.getProperty("load"), String.class).invoke(minecraft, names.getProperty("world"));
        } else {
            Object opener = method(minecraft.getClass(), flow).invoke(minecraft);
            if (names.getProperty("screenType") == null) {
                beforeLoad.run();
                method(opener.getClass(), names.getProperty("load"), String.class).invoke(opener, names.getProperty("world"));
            } else {
                Class<?> screenType = Class.forName(names.getProperty("screenType"), false, minecraft.getClass().getClassLoader());
                beforeLoad.run();
                method(opener.getClass(), names.getProperty("load"), screenType, String.class).invoke(opener, screen, names.getProperty("world"));
            }
        }
        System.out.println("[AutoPlug] Requested the selected save through Minecraft's normal integrated-world loading flow.");
        return true;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws Exception {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Method result = current.getDeclaredMethod(name, parameters);
                result.setAccessible(true);
                return result;
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
    }

    private static void validateSave(Properties names) throws IOException {
        Path game = Paths.get(names.getProperty("gameDir")).toAbsolutePath().normalize();
        Path saves = game.resolve("saves"), world = saves.resolve(names.getProperty("world"));
        Path level = world.resolve("level.dat");
        if (!Files.isDirectory(saves, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(saves)
                || !saves.toRealPath().equals(game.toRealPath().resolve("saves"))
                || !Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(world)
                || !world.toRealPath().equals(saves.toRealPath().resolve(world.getFileName()))
                || !Files.isRegularFile(level, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(level)
                || !level.toRealPath().equals(world.toRealPath().resolve("level.dat")))
            throw new IOException("The original save is missing or linked; automatic world entry was cancelled.");
    }

    private static void reportFailure(Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException ? ((InvocationTargetException) failure).getTargetException() : failure;
        System.err.println("[AutoPlug] Automatic world entry failed: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        cause.printStackTrace(System.err);
    }
}
