package com.osiris.autoplug.client.mc.launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Synthetic byte fixtures only; an unknown URL fails instead of reaching the network. */
final class LaunchFixtures {
    final FakeTransport transport = new FakeTransport();
    final ArtifactCache cache;
    final MojangMetadata metadata;
    final JavaRuntimeSelector runtimes;
    final JsonObject version = new JsonObject();
    final Path root;

    LaunchFixtures(Path root) throws Exception {
        this.root = root;
        cache = new ArtifactCache(root.resolve("cache"), transport);
        metadata = new MojangMetadata(transport, cache);
        Path javaExecutable = Files.createDirectories(root.resolve("jre17/bin")).resolve("java");
        Files.write(javaExecutable, new byte[0]);
        Files.write(root.resolve("jre17/release"), "JAVA_VERSION=\"17.0.12\"\nOS_ARCH=\"x86_64\"\n".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(root.resolve("jre17-arm64/bin")); Files.write(root.resolve("jre17-arm64/bin/java"), new byte[0]);
        Files.write(root.resolve("jre17-arm64/release"), "JAVA_VERSION=\"17.0.12\"\nOS_ARCH=\"aarch64\"\n".getBytes(StandardCharsets.UTF_8));
        runtimes = new JavaRuntimeSelector(java.util.Arrays.asList(root.resolve("jre17"), root.resolve("jre17-arm64")), false);
        version.addProperty("id", "1.20.4");
        version.addProperty("type", "release");
        version.addProperty("mainClass", "example.fixture.Main");
        version.add("javaVersion", json("{\"majorVersion\":17}"));
        JsonObject downloads = new JsonObject();
        downloads.add("client", artifact("https://fixture.invalid/client.jar", new byte[]{1, 2, 3}));
        version.add("downloads", downloads);
        JsonArray libraries = new JsonArray();
        JsonObject library = new JsonObject(); library.addProperty("name", "example:library:1.0");
        JsonObject libraryDownloads = new JsonObject();
        JsonObject descriptor = artifact("https://fixture.invalid/library.jar", new byte[]{4, 5});
        descriptor.addProperty("path", "example/library/1.0/library-1.0.jar");
        libraryDownloads.add("artifact", descriptor); library.add("downloads", libraryDownloads); libraries.add(library);
        version.add("libraries", libraries);
        version.add("arguments", json("{\"jvm\":[\"-Djava.library.path=${natives_directory}\",\"-cp\",\"${classpath}\"],\"game\":[\"--username\",\"${auth_player_name}\",\"--gameDir\",\"${game_directory}\",\"--accessToken\",\"${auth_access_token}\"]}"));
        byte[] asset = "synthetic sound".getBytes(StandardCharsets.UTF_8);
        String hash = hash(asset);
        JsonObject index = json("{\"virtual\":true,\"map_to_resources\":true,\"objects\":{}}");
        JsonObject item = new JsonObject(); item.addProperty("hash", hash); item.addProperty("size", asset.length);
        index.getAsJsonObject("objects").add("sounds/example.txt", item);
        transport.bytes.put("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash, asset);
        JsonObject indexDescriptor = artifact("https://fixture.invalid/assets.json", index.toString().getBytes(StandardCharsets.UTF_8));
        indexDescriptor.addProperty("id", "fixture-assets"); version.add("assetIndex", indexDescriptor);
        publishVersion();
    }

    MinecraftLauncher launcher(String os, String architecture) {
        return new MinecraftLauncher(cache, metadata, new LaunchPlatform(os, architecture, "fixture", Collections.emptyMap()), runtimes);
    }

    void publishVersion() throws Exception {
        JsonObject descriptor = artifact("https://fixture.invalid/version.json", version.toString().getBytes(StandardCharsets.UTF_8));
        descriptor.addProperty("id", version.get("id").getAsString());
        JsonObject manifest = new JsonObject(); JsonArray versions = new JsonArray(); versions.add(descriptor);
        manifest.add("versions", versions);
        transport.responses.put("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", manifest);
    }

    JsonObject artifact(String url, byte[] bytes) throws Exception {
        transport.bytes.put(url, bytes);
        JsonObject result = new JsonObject(); result.addProperty("url", url); result.addProperty("size", bytes.length); result.addProperty("sha1", hash(bytes));
        return result;
    }

    String hash(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-1").digest(bytes)) result.append(String.format("%02x", value & 255));
        return result.toString();
    }

    JsonObject json(String json) { return JsonParser.parseString(json).getAsJsonObject(); }

    byte[] zip(String name, String content) throws IOException {
        return zip(Collections.singletonMap(name, content));
    }

    byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey())); zip.write(file.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    final class FakeTransport implements LaunchTransport {
        final Map<String, byte[]> bytes = new HashMap<>();
        final Map<String, JsonObject> responses = new HashMap<>();
        final List<String> downloads = new ArrayList<>();
        @Override public JsonObject json(String method, URI uri, Map<String, String> headers, byte[] body, Cancellation cancellation) throws IOException {
            cancellation.check();
            JsonObject response = responses.get(uri.toString());
            if (response == null) throw new IOException("No synthetic JSON fixture for " + uri);
            return response.deepCopy();
        }
        @Override public void download(URI uri, Path destination, Cancellation cancellation) throws IOException {
            cancellation.check();
            byte[] data = bytes.get(uri.toString());
            if (data == null) throw new IOException("No synthetic download fixture for " + uri);
            downloads.add(uri.toString()); Files.write(destination, data);
        }
    }
}
