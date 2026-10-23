package com.osiris.autoplug.client.mc.launch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.NodeList;

/** Runs only checksum-verified Forge/NeoForge vendor installers, in an explicit game directory. */
public final class OfficialLoaderInstaller {
    private final ArtifactCache cache;
    private final MojangMetadata metadata;
    private final LaunchTransport transport;
    private final JavaRuntimeSelector runtimes;
    private final ProcessRunner runner;

    public OfficialLoaderInstaller(ArtifactCache cache, MojangMetadata metadata, LaunchTransport transport, JavaRuntimeSelector runtimes) {
        this(cache, metadata, transport, runtimes, new InstallerProcessRunner());
    }

    public OfficialLoaderInstaller(ArtifactCache cache, MojangMetadata metadata, LaunchTransport transport,
                                   JavaRuntimeSelector runtimes, ProcessRunner runner) {
        this.cache = cache; this.metadata = metadata; this.transport = transport; this.runtimes = runtimes; this.runner = runner;
    }

    /** Null loaderVersion selects the vendor's recommended/latest stable release for that game version. */
    public String install(String loader, String gameVersion, String loaderVersion, Path gameDirectory,
                          Cancellation cancellation) throws IOException {
        cancellation.check();
        String brand = loader.toUpperCase(java.util.Locale.ROOT);
        if (!brand.equals("FORGE") && !brand.equals("NEOFORGE")) throw new IOException("Expected Forge or NeoForge");
        validateVersion(gameVersion);
        String selected = loaderVersion == null || loaderVersion.isEmpty() ? latest(brand, gameVersion, cancellation) : loaderVersion;
        validateVersion(selected);
        String coordinate;
        String base;
        if (brand.equals("FORGE")) {
            coordinate = "net/minecraftforge/forge/" + gameVersion + "-" + selected + "/forge-" + gameVersion + "-" + selected + "-installer.jar";
            base = "https://maven.minecraftforge.net/";
        } else {
            coordinate = "net/neoforged/neoforge/" + selected + "/neoforge-" + selected + "-installer.jar";
            base = "https://maven.neoforged.net/releases/";
        }
        Path installer = cache.obtainWithSidecar("installers/" + coordinate, URI.create(base + coordinate), cancellation);
        JsonObject vanilla = metadata.version(gameVersion, cancellation);
        int major = vanilla.has("javaVersion") ? vanilla.getAsJsonObject("javaVersion").get("majorVersion").getAsInt() : 8;
        Path javaExecutable = runtimes.select(major);
        Files.createDirectories(gameDirectory);
        Path game = gameDirectory.toRealPath();
        Path versions = game.resolve("versions");
        Path baseVersion = versions.resolve(gameVersion);
        Files.createDirectories(baseVersion);
        Files.write(baseVersion.resolve(gameVersion + ".json"), vanilla.toString().getBytes(StandardCharsets.UTF_8));
        JsonObject client = vanilla.getAsJsonObject("downloads").getAsJsonObject("client");
        Path clientJar = cache.obtain("versions/" + gameVersion + "/" + gameVersion + ".jar", URI.create(client.get("url").getAsString()),
                client.get("sha1").getAsString(), client.get("size").getAsLong(), cancellation);
        Files.copy(clientJar, baseVersion.resolve(gameVersion + ".jar"), StandardCopyOption.REPLACE_EXISTING);
        Path launcherProfiles = game.resolve("launcher_profiles.json");
        if (!Files.exists(launcherProfiles)) Files.write(launcherProfiles, "{\"profiles\":{}}".getBytes(StandardCharsets.UTF_8));
        int exit = runner.run(Arrays.asList(javaExecutable.toString(), "-jar", installer.toString(), "--installClient", game.toString()), game, cancellation);
        cancellation.check();
        if (exit != 0) throw new IOException("Official " + brand + " installer failed with exit code " + exit + "; inspect loader-install.log");
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(versions, 2)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".json")).collect(Collectors.toList())) {
                JsonObject profile = readJson(path);
                if (profile.has("inheritsFrom") && gameVersion.equals(profile.get("inheritsFrom").getAsString())
                        && profile.has("id") && profile.get("id").getAsString().toLowerCase(java.util.Locale.ROOT).contains("forge")
                        && profile.get("id").getAsString().contains(selected)) candidates.add(path);
            }
        }
        if (candidates.size() != 1) throw new IOException("Installer did not produce one identifiable loader version profile");
        Path libraries = game.resolve("libraries");
        if (Files.isDirectory(libraries)) {
            try (Stream<Path> paths = Files.walk(libraries)) {
                for (Path file : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    cancellation.check();
                    if (file.getFileName().toString().endsWith(".jar")) {
                        cache.importInstallerLibrary("libraries/" + libraries.relativize(file).toString().replace('\\', '/'), file);
                    }
                }
            }
        }
        return metadata.importInstalledLoaderProfile(candidates.get(0));
    }

    private String latest(String brand, String gameVersion, Cancellation cancellation) throws IOException {
        if (brand.equals("FORGE")) {
            JsonObject json = transport.json("GET", URI.create("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"),
                    Collections.emptyMap(), null, cancellation);
            JsonObject promotions = json.getAsJsonObject("promos");
            JsonElement selected = promotions.has(gameVersion + "-recommended") ? promotions.get(gameVersion + "-recommended") : promotions.get(gameVersion + "-latest");
            if (selected == null) throw new IOException("Forge has no published release for " + gameVersion);
            return selected.getAsString();
        }
        String[] game = gameVersion.split("\\.");
        if (game.length < 2 || !game[0].equals("1")) throw new IOException("Select an explicit NeoForge version for this Minecraft release");
        String prefix = game[1] + "." + (game.length > 2 ? game[2] : "0") + ".";
        Path xml = Files.createTempFile(cache.getRoot(), ".neoforge-versions-", ".xml");
        try {
            transport.download(URI.create("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"), xml, cancellation);
            if (Files.size(xml) > 2 * 1024 * 1024) throw new IOException("NeoForge version metadata too large");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            NodeList versions = factory.newDocumentBuilder().parse(xml.toFile()).getElementsByTagName("version");
            List<String> matches = new ArrayList<>();
            for (int i = 0; i < versions.getLength(); i++) {
                String version = versions.item(i).getTextContent().trim();
                if (version.startsWith(prefix) && version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) matches.add(version);
            }
            matches.sort((left, right) -> {
                String[] a = left.split("\\."); String[] b = right.split("\\.");
                for (int i = 0; i < 3; i++) {
                    int order = new java.math.BigInteger(a[i]).compareTo(new java.math.BigInteger(b[i]));
                    if (order != 0) return order;
                }
                return 0;
            });
            if (matches.isEmpty()) throw new IOException("NeoForge has no stable release for " + gameVersion);
            return matches.get(matches.size() - 1);
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("Invalid NeoForge version metadata", error); }
        finally { Files.deleteIfExists(xml); }
    }

    private JsonObject readJson(Path file) throws IOException {
        if (Files.size(file) > 8 * 1024 * 1024) throw new IOException("Loader metadata too large");
        try { return JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException error) { throw new IOException("Invalid installed loader JSON", error); }
    }

    private void validateVersion(String value) throws IOException {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,159}")) throw new IOException("Invalid loader/game version");
    }

    public interface ProcessRunner {
        int run(List<String> command, Path directory, Cancellation cancellation) throws IOException;
    }

    private static final class InstallerProcessRunner implements ProcessRunner {
        @Override public int run(List<String> command, Path directory, Cancellation cancellation) throws IOException {
            cancellation.check();
            Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
                    .redirectOutput(directory.resolve("loader-install.log").toFile()).start();
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(20);
            try (AutoCloseable ignored = cancellation.onCancel(process::destroy)) {
                while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    cancellation.check();
                    if (System.nanoTime() > deadline) throw new IOException("Loader installer timed out");
                }
                return process.exitValue();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt(); throw new IOException("Loader installation interrupted", error);
            } catch (IOException error) { throw error; }
            catch (Exception error) { throw new IOException("Loader installation failed", error); }
            finally { if (process.isAlive()) process.destroyForcibly(); }
        }
    }
}
