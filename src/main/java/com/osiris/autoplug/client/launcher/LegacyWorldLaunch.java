package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adapts exact publisher mappings to vanilla's integrated-world entry API. */
final class LegacyWorldLaunch {
    static final String AGENT = "com/osiris/autoplug/legacy/LegacyWorldAgent.class";
    final Path agent;
    final Path configuration;

    private LegacyWorldLaunch(Path agent, Path configuration) { this.agent = agent; this.configuration = configuration; }

    static LegacyWorldLaunch prepare(Path cache, JsonObject metadata, LaunchRequest request, Consumer<String> progress) throws IOException {
        if (!"VANILLA".equals(request.loader))
            throw new IOException("Legacy automatic world entry requires the original vanilla game; modded saves must use their matching modded setup.");
        JsonObject downloads = metadata.getAsJsonObject("downloads");
        progress.accept("Preparing automatic entry for the selected legacy world");
        Properties names;
        if (downloads != null && downloads.has("client_mappings")) {
            JsonObject descriptor = downloads.getAsJsonObject("client_mappings");
            Path mapping = cache.resolve("versions").resolve(LaunchRequest.safeVersion(request.version)).resolve("client-mappings.txt");
            LauncherFiles.download(descriptor.get("url").getAsString(), mapping,
                    descriptor.get("sha1").getAsString(), descriptor.get("size").getAsLong(), progress);
            if (Files.size(mapping) > 32 * 1024 * 1024) throw new IOException("Minecraft mapping file exceeds the supported size.");
            names = resolve(new String(Files.readAllBytes(mapping), StandardCharsets.UTF_8));
        } else {
            names = bundled(request.version, downloads == null || !downloads.has("client") ? "" : downloads.getAsJsonObject("client").get("sha1").getAsString());
        }
        names.setProperty("world", request.singleplayerWorld);
        names.setProperty("gameDir", request.gameDir.toString());
        Path agent = packageAgent(cache.resolve("legacy-world-helper"));
        Path directory = request.gameDir.resolve(".autoplug");
        Files.createDirectories(directory);
        Path configuration = Files.createTempFile(directory, "legacy-world-", ".properties");
        try {
            AccountStore.restrict(configuration);
            try (OutputStream output = Files.newOutputStream(configuration)) { names.store(output, "AutoPlug exact-version world entry"); }
            return new LegacyWorldLaunch(agent, configuration);
        } catch (IOException failure) { Files.deleteIfExists(configuration); throw failure; }
    }

    String argument() { return "-javaagent:" + agent + "=" + configuration; }

    static Properties bundled(String version, String clientSha1) throws IOException {
        Properties result = new Properties();
        try (InputStream input = LegacyWorldLaunch.class.getResourceAsStream("/launcher/legacy/" + LaunchRequest.safeVersion(version) + ".properties")) {
            if (input == null) throw new IOException("No verified automatic world-entry adapter is available for Minecraft " + version + ".");
            result.load(input);
        }
        if (!clientSha1.equals(result.getProperty("clientSha1"))) throw new IOException("The legacy Minecraft client differs from the verified adapter.");
        return result;
    }

    static Properties resolve(String mappings) throws IOException {
        Map<String, String> classes = new HashMap<>();
        Map<String, Map<String, String>> members = new HashMap<>();
        Pattern classLine = Pattern.compile("^([^ #].*) -> ([^ ]+):$");
        Pattern methodLine = Pattern.compile("^\\s+(?:\\d+:\\d+:)?[^ ]+ ([^ (]+\\([^)]*\\))(?::\\d+(?::\\d+)?)? -> ([^ ]+)$");
        Pattern fieldLine = Pattern.compile("^\\s+[^ ]+ ([^ ()]+) -> ([^ ]+)$");
        String owner = null;
        for (String line : mappings.split("\\r?\\n")) {
            Matcher type = classLine.matcher(line);
            if (type.matches()) { owner = type.group(1); classes.put(owner, type.group(2)); members.put(owner, new HashMap<>()); }
            else if (owner != null) {
                Matcher method = methodLine.matcher(line), field = fieldLine.matcher(line);
                if (method.matches()) members.get(owner).put(method.group(1), method.group(2));
                else if (field.matches()) members.get(owner).put(field.group(1), field.group(2));
            }
        }
        String minecraft = "net.minecraft.client.Minecraft";
        Map<String, String> client = members.get(minecraft);
        if (client == null) throw new IOException("Publisher mappings do not contain the vanilla Minecraft client.");
        Properties result = new Properties();
        require(result, "minecraft", classes.get(minecraft));
        require(result, "title", classes.get("net.minecraft.client.gui.screens.TitleScreen"));
        require(result, "instance", client.get("getInstance()"));
        require(result, "screen", client.get("screen"));
        require(result, "overlay", client.get("getOverlay()"));
        if (client.containsKey("loadLevel(java.lang.String)")) {
            require(result, "load", client.get("loadLevel(java.lang.String)"));
        } else if (client.containsKey("selectLevel(java.lang.String,java.lang.String,net.minecraft.world.level.LevelSettings)")) {
            require(result, "load", client.get("selectLevel(java.lang.String,java.lang.String,net.minecraft.world.level.LevelSettings)"));
            require(result, "levelSource", client.get("getLevelSource()"));
            require(result, "settingsType", classes.get("net.minecraft.world.level.LevelSettings"));
            Map<String, String> storage = members.get("net.minecraft.world.level.storage.LevelStorageSource");
            Map<String, String> summary = members.get("net.minecraft.world.level.storage.LevelSummary");
            require(result, "levelList", storage == null ? null : storage.get("getLevelList()"));
            require(result, "levelExists", storage == null ? null : storage.get("levelExists(java.lang.String)"));
            require(result, "levelId", summary == null ? null : summary.get("getLevelId()"));
            require(result, "levelName", summary == null ? null : summary.get("getLevelName()"));
        } else {
            require(result, "flows", client.get("createWorldOpenFlows()"));
            Map<String, String> flows = members.get("net.minecraft.client.gui.screens.worldselection.WorldOpenFlows");
            if (flows != null && flows.containsKey("loadLevel(java.lang.String)")) {
                require(result, "load", flows.get("loadLevel(java.lang.String)"));
            } else {
                require(result, "load", flows == null ? null : flows.get("loadLevel(net.minecraft.client.gui.screens.Screen,java.lang.String)"));
                require(result, "screenType", classes.get("net.minecraft.client.gui.screens.Screen"));
            }
        }
        return result;
    }

    private static void require(Properties result, String key, String value) throws IOException {
        if (value == null || value.isEmpty()) throw new IOException("This Minecraft mapping does not expose the supported automatic world entry API (" + key + ").");
        result.setProperty(key, value);
    }

    static Path packageAgent(Path directory) throws IOException {
        byte[] bytes;
        try (InputStream input = LegacyWorldLaunch.class.getResourceAsStream("/" + AGENT)) {
            if (input == null) throw new IOException("AutoPlug's Java 8 world-entry helper is missing from this build.");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            bytes = output.toByteArray();
        }
        if (bytes.length < 8 || bytes[6] != 0 || bytes[7] != 52)
            throw new IOException("World-entry helper must be compiled for Java 8.");
        String hash = com.osiris.autoplug.client.utils.UtilsCrypto.calculateSHA1Hash(bytes);
        Files.createDirectories(directory);
        Path target = directory.resolve("autoplug-world-entry-" + hash + ".jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Premain-Class", AGENT.replace('/', '.').replace(".class", ""));
        ByteArrayOutputStream packaged = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(packaged)) {
            JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF"); manifestEntry.setTime(0);
            jar.putNextEntry(manifestEntry); manifest.write(jar); jar.closeEntry();
            JarEntry classEntry = new JarEntry(AGENT); classEntry.setTime(0);
            jar.putNextEntry(classEntry); jar.write(bytes); jar.closeEntry();
        }
        byte[] expected = packaged.toByteArray();
        // Validate the complete cached JAR and reuse identical bytes, including
        // when another running Java 8 client holds it open on Windows.
        if (Files.isRegularFile(target) && Files.size(target) == expected.length && Arrays.equals(expected, Files.readAllBytes(target))) return target.toAbsolutePath();
        Path temporary = Files.createTempFile(directory, "helper-", ".jar");
        try {
            Files.write(temporary, expected);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toAbsolutePath();
        } finally { Files.deleteIfExists(temporary); }
    }
}
