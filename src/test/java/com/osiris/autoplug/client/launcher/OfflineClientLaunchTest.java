package com.osiris.autoplug.client.launcher;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes an original tiny offline fixture JAR, never Minecraft, Microsoft sign-in or a real server. */
class OfflineClientLaunchTest {
    @TempDir Path temp;
    @Test void fullPrepareAndProcessBuilderLaunchPreserveOfflineIdentityDirectConnectAndIsolation() throws Exception {
        Path source = temp.resolve("OfflineFixtureClient.java");
        String code = "import java.nio.file.*; import java.util.*; import java.io.*; "
                + "public class OfflineFixtureClient { public static void main(String[] args)throws Exception { "
                + "Properties p=new Properties(); p.setProperty(\"workingDirectory\",System.getProperty(\"user.dir\")); "
                + "for(int i=0;i<args.length;i++)p.setProperty(\"arg.\"+i,args[i]); "
                + "try(OutputStream out=Files.newOutputStream(Paths.get(\"launch-record.properties\"))){p.store(out,\"Synthetic offline fixture arguments only\");} } }";
        Files.write(source,code.getBytes(StandardCharsets.UTF_8));
        assertNotNull(ToolProvider.getSystemJavaCompiler());
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","9","-d",temp.toString(),source.toString()));
        Manifest jarManifest = new Manifest(); jarManifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,"1.0");
        ByteArrayOutputStream jarBytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(jarBytes,jarManifest)) {
            jar.putNextEntry(new JarEntry("OfflineFixtureClient.class")); jar.write(Files.readAllBytes(temp.resolve("OfflineFixtureClient.class"))); jar.closeEntry();
        }
        byte[] client = jarBytes.toByteArray();
        Map<String,byte[]> responses = new HashMap<>(); responses.put("https://fixture.test/client.jar",client);
        JsonObject metadata = NativeLauncherTest.fixture("1.20.4.json"); metadata.addProperty("mainClass","OfflineFixtureClient");
        JsonObject clientArtifact = NativeLauncherTest.artifact("https://fixture.test/client.jar",null,responses);
        JsonObject downloads = new JsonObject(); downloads.add("client",clientArtifact); metadata.add("downloads",downloads);
        metadata.add("libraries",new JsonArray()); metadata.remove("assetIndex"); metadata.remove("logging");
        byte[] metadataBytes = metadata.toString().getBytes(StandardCharsets.UTF_8); responses.put("https://fixture.test/1.20.4.json",metadataBytes);
        JsonObject entry = new JsonObject(); entry.addProperty("id","1.20.4"); entry.addProperty("url","https://fixture.test/1.20.4.json"); entry.addProperty("sha1",NativeLauncherTest.sha1(metadataBytes));
        JsonArray versions = new JsonArray(); versions.add(entry); JsonObject manifest = new JsonObject(); manifest.add("versions",versions);
        responses.put(NativeLauncher.VERSION_MANIFEST,manifest.toString().getBytes(StandardCharsets.UTF_8));
        List<String> requests = new ArrayList<>();
        NativeLauncher launcher = new NativeLauncher(temp.resolve("shared cache"),uri -> {
            requests.add(uri.toString()); byte[] bytes = responses.get(uri.toString());
            if(bytes==null)throw new IOException("Unexpected offline fixture request"); return new ByteArrayInputStream(bytes);
        },LauncherPlatform.current(),major -> Paths.get(System.getProperty("java.home"),"bin",LauncherPlatform.current().os.equals("windows")?"java.exe":"java"));
        Path profile = temp.resolve("isolated profile with spaces"); LaunchAccount account = LaunchAccount.offline("OfflineFixture");
        LaunchPlan prepared = launcher.prepare("1.20.4","VANILLA",profile,account);
        Path java = launcher.resolveJava(prepared); ManagedJavaRuntime.verifyMajor(java,Runtime.version().major());
        Process child = prepared.launch(java,"localhost",25566);
        try { assertTrue(child.waitFor(10,TimeUnit.SECONDS),"Offline fixture process must terminate"); assertEquals(0,child.exitValue()); }
        finally { if(child.isAlive())child.destroyForcibly(); }
        assertEquals(Arrays.asList(NativeLauncher.VERSION_MANIFEST,"https://fixture.test/1.20.4.json","https://fixture.test/client.jar"),requests);
        Properties record = new Properties(); try(InputStream in=Files.newInputStream(profile.resolve("launch-record.properties"))){record.load(in);}
        assertEquals(profile.toString(),record.getProperty("workingDirectory")); List<String> arguments = new ArrayList<>();
        for(int i=0;record.containsKey("arg."+i);i++)arguments.add(record.getProperty("arg."+i));
        assertEquals("OfflineFixture",arguments.get(arguments.indexOf("--username")+1));
        assertEquals(account.getUuid(),arguments.get(arguments.indexOf("--uuid")+1)); assertEquals("0",arguments.get(arguments.indexOf("--accessToken")+1));
        assertEquals(profile.toString(),arguments.get(arguments.indexOf("--gameDir")+1));
        assertEquals("localhost:25566",arguments.get(arguments.indexOf("--quickPlayMultiplayer")+1));
        assertFalse(Files.exists(temp.resolve("launch-record.properties"))); assertFalse(arguments.contains("--server"));
    }
}
