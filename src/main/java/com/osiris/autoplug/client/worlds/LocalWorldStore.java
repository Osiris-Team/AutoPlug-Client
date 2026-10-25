package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.browser.ServerBrowserService;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Discovers standard local saves in place. Discovery never writes to the saves. */
public final class LocalWorldStore {
    private static final String PREFIX = "local:";
    private final Path minecraft;
    private final Consumer<String> progress;

    public LocalWorldStore(Path minecraft) { this(minecraft, ignored -> { }); }
    public LocalWorldStore(Path minecraft, Consumer<String> progress) {
        this.minecraft = minecraft.toAbsolutePath().normalize();
        this.progress = progress == null ? ignored -> { } : progress;
    }
    public static Path defaultMinecraftDirectory() { return ServerBrowserService.defaults().vanillaFile().getParent(); }
    public Path getMinecraftDirectory() { return minecraft; }

    public List<LocalWorld> list() throws IOException {
        Path saves = minecraft.resolve("saves");
        if (!Files.exists(saves, LinkOption.NOFOLLOW_LINKS)) return Collections.emptyList();
        validateSaves(saves);
        List<LocalWorld> result = new ArrayList<>();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(saves)) {
            int count = 0;
            for (Path child : children) {
                if (++count > 10000) throw new IOException("Too many entries in the Minecraft saves folder");
                if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) continue;
                if (!Files.exists(child.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) continue;
                try { result.add(get(PREFIX + child.getFileName())); }
                catch (IOException | IllegalArgumentException e) { progress.accept("Skipped local save '" + child.getFileName() + "': " + e.getMessage()); }
            }
        }
        result.sort(Comparator.comparing((LocalWorld world) -> world.name, String.CASE_INSENSITIVE_ORDER).thenComparing(world -> world.id));
        return result;
    }

    public LocalWorld get(String id) throws IOException {
        if (id == null || !id.startsWith(PREFIX)) throw new IOException("Invalid local world identifier");
        String name = id.substring(PREFIX.length());
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.indexOf(':') >= 0 || name.chars().anyMatch(Character::isISOControl))
            throw new IOException("Invalid local world directory");
        Path saves = minecraft.resolve("saves"); validateSaves(saves);
        Path directory = saves.resolve(name).normalize();
        if (!directory.getParent().equals(saves) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory) || !directory.toRealPath().equals(saves.toRealPath().resolve(name)))
            throw new IOException("Linked or external save directories are not supported");
        return inspect(id, directory, minecraft);
    }

    /** Read only bounded metadata; used for both existing local saves and managed server saves. */
    public static LocalWorld inspect(String id, Path directory, Path gameDirectory) throws IOException {
        directory = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)
                || !directory.toRealPath().equals(directory.getParent().toRealPath().resolve(directory.getFileName())))
            throw new IOException("Linked or external save directories are not supported");
        Path level = directory.resolve("level.dat");
        if (!regularChild(level, directory)) throw new IOException("Missing or linked level.dat");
        LevelDatReader.Metadata metadata = new LevelDatReader().read(level);
        String display = metadata.name.trim();
        if (display.isEmpty()) display = directory.getFileName().toString();
        Path icon = directory.resolve("icon.png");
        try { if (!safeIcon(icon, directory)) icon = null; }
        catch (IOException e) { icon = null; } // An unreadable thumbnail must not hide a valid save.
        long[] size = directorySize(directory, 10000);
        return new LocalWorld(id, display, metadata.version, directory, gameDirectory, icon, metadata.modded,
                metadata.lastPlayed, size[0], size[1] == 1, metadata.dataPacks, metadata.cheats, metadata.hardcore);
    }

    /** A lower bound is returned if traversal hits its entry/depth/time budget or inaccessible data. */
    static long[] directorySize(Path directory, int maxEntries) {
        long[] result = {0, 1}; int[] entries = {0}; long deadline = System.nanoTime() + 100_000_000L;
        try {
            Files.walkFileTree(directory, EnumSet.noneOf(FileVisitOption.class), 32, new SimpleFileVisitor<Path>() {
                private FileVisitResult budget() {
                    if (++entries[0] > maxEntries || System.nanoTime() > deadline) { result[1] = 0; return FileVisitResult.TERMINATE; }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) { return budget(); }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (budget() == FileVisitResult.TERMINATE) return FileVisitResult.TERMINATE;
                    if (attrs.isRegularFile()) result[0] += attrs.size(); else result[1] = 0;
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException error) { result[1] = 0; return budget(); }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) { if (error != null) result[1] = 0; return FileVisitResult.CONTINUE; }
            });
        } catch (IOException | SecurityException e) { result[1] = 0; }
        return result;
    }

    /** Preserve the exact recorded build; require an explicit choice for older unknown saves. */
    public String launchVersion(LocalWorld world, String selected) throws IOException {
        String version = canonicalVersion(selected == null ? "" : selected.trim());
        if (world.modded) throw new IOException("This save was marked as modded. Open it with its original modded launcher to preserve its content.");
        if (!world.gameVersion.isEmpty()) {
            String recorded = canonicalVersion(world.gameVersion);
            if (version.isEmpty()) version = recorded;
            if (!version.equals(recorded)) throw new IOException("This save records Minecraft " + world.gameVersion + "; selecting another version could change it. Use the recorded version.");
        }
        if (version.isEmpty()) throw new IOException("This save does not record a Minecraft version. Choose its original version explicitly.");
        if (version.equals(".") || version.contains("..") || !version.matches("[A-Za-z0-9._+ -]+"))
            throw new IOException("The save's Minecraft version is not a valid published version identifier");
        return version;
    }

    /** Publisher display names and manifest IDs can identify the same prerelease build. */
    private static String canonicalVersion(String version) {
        Matcher display = Pattern.compile("^([0-9]+\\.[0-9]+(?:\\.[0-9]+)?) (Pre-release|Release Candidate) ([1-9][0-9]*)$", Pattern.CASE_INSENSITIVE).matcher(version);
        if (display.matches()) return display.group(1) + (display.group(2).equalsIgnoreCase("Pre-release") ? "-pre" : "-rc") + display.group(3);
        Matcher id = Pattern.compile("^([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)-(pre|rc)([1-9][0-9]*)$", Pattern.CASE_INSENSITIVE).matcher(version);
        return id.matches() ? id.group(1) + "-" + id.group(2).toLowerCase(Locale.ROOT) + id.group(3) : version;
    }

    private void validateSaves(Path saves) throws IOException {
        if (!Files.isDirectory(saves, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(saves)
                || !saves.toRealPath().equals(minecraft.toRealPath().resolve("saves")))
            throw new IOException("Linked or external Minecraft saves folders are not supported");
    }
    private static boolean regularChild(Path file, Path directory) throws IOException {
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                && file.toRealPath().equals(directory.toRealPath().resolve(file.getFileName()));
    }
    private static boolean safeIcon(Path icon, Path directory) throws IOException {
        if (!regularChild(icon, directory) || Files.size(icon) < 24 || Files.size(icon) > 1024 * 1024) return false;
        try (DataInputStream input = new DataInputStream(Files.newInputStream(icon))) {
            if (input.readLong() != 0x89504e470d0a1a0aL || input.readInt() != 13 || input.readInt() != 0x49484452) return false;
            int width = input.readInt(), height = input.readInt();
            return width > 0 && width <= 1024 && height > 0 && height <= 1024;
        } catch (EOFException e) { return false; }
    }
}
