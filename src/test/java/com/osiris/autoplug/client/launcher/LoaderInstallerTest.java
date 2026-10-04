package com.osiris.autoplug.client.launcher;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class LoaderInstallerTest {
    @TempDir Path temp;
    static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String,byte[]> entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
        }
        return bytes.toByteArray();
    }
    @Test void realForgeAndNeoForgeClientProcessorsResolveEveryOfficialArgument() throws Exception {
        for (String kind : Arrays.asList("forge", "neoforge", "forge-26.3", "neoforge-26.3")) {
            String minecraft = kind.endsWith("26.3") ? "26.3" : "1.20.4";
            JsonObject profile = NativeLauncherTest.fixture(kind + "-install_profile.json");
            Map<String,byte[]> entries = new HashMap<>(); entries.put("data/client.lzma", new byte[]{1,2,3});
            Path installerJar = temp.resolve(kind + "-installer.jar"); Files.write(installerJar, zip(entries));
            LauncherDownloads downloads = new LauncherDownloads(temp.resolve(kind + "-cache"), uri -> { throw new IOException("Fixture must not access a network"); });
            LoaderInstaller installer = new LoaderInstaller(downloads,new LauncherPlatform("linux","amd64","6"),major -> temp.resolve("java"));
            Path root = downloads.resolve("work/.marker").getParent(); Files.createDirectories(root);
            Map<String,String> data = installer.installerData(profile,installerJar,root,temp.resolve("minecraft.jar"),minecraft);
            assertEquals("client",data.get("SIDE")); assertTrue(data.get("PATCHED").startsWith(downloads.getRoot().toString()));
            assertEquals(downloads.getRoot().resolve("libraries"), Paths.get(installer.expression("{ROOT}/libraries", data)).normalize());
            int clients = 0;
            for (JsonElement p : profile.getAsJsonArray("processors")) {
                JsonObject processor = p.getAsJsonObject(); if (!LoaderInstaller.runsOnClient(processor)) continue; clients++;
                NativeLauncher.mavenPath(processor.get("jar").getAsString());
                for (JsonElement coordinate : processor.getAsJsonArray("classpath")) NativeLauncher.mavenPath(coordinate.getAsString());
                for (JsonElement argument : processor.getAsJsonArray("args")) {
                    String resolved = installer.expression(argument.getAsString(),data); assertFalse(resolved.matches(".*\\{[^}]+}.*"));
                }
                if (processor.has("outputs")) assertFalse(installer.outputsValid(processor,data));
            }
            assertTrue(clients >= (minecraft.equals("26.3") ? 1 : 5), "Fixture must cover the actual declared client install chain");
            JsonObject metadata = NativeLauncher.merge(NativeLauncherTest.fixture(minecraft + ".json"),NativeLauncherTest.fixture(kind + "-version.json"));
            LaunchPlan plan = new LaunchPlan(metadata,minecraft,temp.resolve("game"),temp.resolve("natives"),temp.resolve("assets"),downloads.getRoot().resolve("libraries"),temp.resolve("minecraft.jar"),Collections.singletonList(temp.resolve("minecraft.jar")),LaunchAccount.offline("Fixture"),new LauncherPlatform("linux","amd64","6"),null);
            List<String> command = plan.buildCommand(temp.resolve("java"),"localhost",25565);
            assertFalse(command.stream().anyMatch(s -> s.contains("${"))); assertTrue(command.contains("--quickPlayMultiplayer"));
            if (kind.equals("neoforge")) assertTrue(command.contains("ALL-MODULE-PATH"));
        }
    }
    byte[] processorJar() throws IOException {
        Path source = temp.resolve("FixtureProcessor.java");
        Files.write(source, ("import java.nio.file.*; public class FixtureProcessor { public static void main(String[] args)throws Exception { Path output=Paths.get(args[0]); Files.createDirectories(output.getParent()); Files.write(output,args[1].getBytes(\"UTF-8\")); } }").getBytes(StandardCharsets.UTF_8));
        assertNotNull(ToolProvider.getSystemJavaCompiler(),"Fixture subprocess requires the build JDK, never a Minecraft account");
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","9","-d",temp.toString(),source.toString()));
        Manifest manifest = new Manifest(); manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,"1.0"); manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS,"FixtureProcessor");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes,manifest)) { jar.putNextEntry(new JarEntry("FixtureProcessor.class")); jar.write(Files.readAllBytes(temp.resolve("FixtureProcessor.class"))); jar.closeEntry(); }
        return bytes.toByteArray();
    }
    @Test void completeInstallerFixtureRunsJavaProcessorChecksOutputAndReusesVerifiedInstallation() throws Exception {
        byte[] processor = processorJar(), payload = "patched-fixture".getBytes(StandardCharsets.UTF_8);
        String processorPath = NativeLauncher.mavenPath("fixture:processor:1"); String patchedPath = NativeLauncher.mavenPath("fixture:client:1");
        JsonObject processorArtifact = new JsonObject(); processorArtifact.addProperty("path",processorPath); processorArtifact.addProperty("url",""); processorArtifact.addProperty("sha1",NativeLauncherTest.sha1(processor)); processorArtifact.addProperty("size",processor.length);
        JsonObject processorLibrary = new JsonObject(); processorLibrary.addProperty("name","fixture:processor:1"); JsonObject processorDownloads = new JsonObject(); processorDownloads.add("artifact",processorArtifact); processorLibrary.add("downloads",processorDownloads);
        JsonObject generatedArtifact = new JsonObject(); generatedArtifact.addProperty("path",patchedPath); generatedArtifact.addProperty("url",""); generatedArtifact.addProperty("sha1",NativeLauncherTest.sha1(payload)); generatedArtifact.addProperty("size",payload.length);
        JsonObject generatedLibrary = new JsonObject(); generatedLibrary.addProperty("name","fixture:client:1"); JsonObject generatedDownloads = new JsonObject(); generatedDownloads.add("artifact",generatedArtifact); generatedLibrary.add("downloads",generatedDownloads);
        JsonObject profile = JsonParser.parseString("{spec:1,minecraft:'1.20.4',data:{PATCHED:{client:'[fixture:client:1]'}},processors:[{jar:'fixture:processor:1',classpath:[],args:['{PATCHED}','patched-fixture'],outputs:{'{PATCHED}':'"+NativeLauncherTest.sha1(payload)+"'}}]}").getAsJsonObject();
        JsonArray installationLibraries = new JsonArray(); installationLibraries.add(processorLibrary); profile.add("libraries",installationLibraries);
        JsonObject version = JsonParser.parseString("{id:'1.20.4-forge-fixture',inheritsFrom:'1.20.4',mainClass:'fixture.Main'}").getAsJsonObject(); JsonArray versionLibraries = new JsonArray(); versionLibraries.add(generatedLibrary); version.add("libraries",versionLibraries);
        Map<String,byte[]> entries = new LinkedHashMap<>(); entries.put("install_profile.json",profile.toString().getBytes(StandardCharsets.UTF_8)); entries.put("version.json",version.toString().getBytes(StandardCharsets.UTF_8)); entries.put("maven/"+processorPath,processor); byte[] archive = zip(entries);
        AtomicInteger requests = new AtomicInteger(), javaResolutions = new AtomicInteger(); String installerHash = NativeLauncherTest.sha1(archive);
        LauncherDownloads downloads = new LauncherDownloads(temp.resolve("cache"),uri -> { requests.incrementAndGet(); if(uri.toString().endsWith(".sha1")) return new ByteArrayInputStream(installerHash.getBytes(StandardCharsets.UTF_8)); if(uri.toString().endsWith("-installer.jar")) return new ByteArrayInputStream(archive); throw new IOException("Unexpected fixture request"); });
        Path java = Paths.get(System.getProperty("java.home"),"bin",LauncherPlatform.current().os.equals("windows")?"java.exe":"java");
        LoaderInstaller installer = new LoaderInstaller(downloads,LauncherPlatform.current(),major -> { javaResolutions.incrementAndGet(); return java; });
        Path client = temp.resolve("minecraft.jar"); Files.write(client,new byte[]{1});
        JsonObject actual = installer.install("1.20.4","FORGE","49.2.0",client,Runtime.version().major());
        assertEquals(version,actual); assertArrayEquals(payload,Files.readAllBytes(downloads.resolve("libraries/"+patchedPath))); assertEquals(1,javaResolutions.get());
        installer.install("1.20.4","FORGE","49.2.0",client,Runtime.version().major()); assertEquals(1,javaResolutions.get(),"Verified generated outputs should avoid re-running every processor");
        Files.write(downloads.resolve("libraries/"+patchedPath),new byte[]{0});
        installer.install("1.20.4","FORGE","49.2.0",client,Runtime.version().major()); assertEquals(2,javaResolutions.get()); assertArrayEquals(payload,Files.readAllBytes(downloads.resolve("libraries/"+patchedPath)));
        assertEquals(4,requests.get(),"Only the first installer artifact and fresh checksum reads need the transport");
    }
    @Test void processorOutputsValidateAllPathsBeforeExecutingAnyJar() throws Exception {
        LauncherDownloads downloads = new LauncherDownloads(temp.resolve("cache"),uri -> { throw new IOException("No network"); }); LoaderInstaller installer = new LoaderInstaller(downloads,LauncherPlatform.current(),major -> temp.resolve("java"));
        Map<String,String> data = new HashMap<>(); data.put("ROOT",downloads.getRoot().toString()); data.put("OUTSIDE",temp.resolve("outside").toString());
        JsonObject processor = JsonParser.parseString("{jar:'fixture:processor:1',outputs:{'{ROOT}/missing.jar':'0000000000000000000000000000000000000000','{OUTSIDE}':'0000000000000000000000000000000000000000'}}").getAsJsonObject();
        assertThrows(IOException.class,()->installer.runProcessor(processor,data,temp.resolve("missing-java"),downloads.getRoot())); assertFalse(Files.exists(temp.resolve("outside")));
    }
}
