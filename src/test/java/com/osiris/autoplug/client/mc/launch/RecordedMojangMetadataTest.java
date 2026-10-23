package com.osiris.autoplug.client.mc.launch;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exact public Mojang 1.20.4 metadata recorded 2026-10-17 through the browser.
 * Source: https://piston-meta.mojang.com/v1/packages/2c592ccd71ca2d5dadebeb6543ad6b18e6f43305/1.20.4.json
 * No account, client jar or third-party implementation is contained in this fixture. */
class RecordedMojangMetadataTest {
    @TempDir Path temporary;
    @Test void readsTheOfficialHashedVersionAndNativeRulesWithoutNetwork() throws Exception {
        byte[] recorded;
        try(InputStream input=getClass().getResourceAsStream("/minecraft/mojang-1.20.4.json")) {assertNotNull(input);recorded=input.readAllBytes();}
        String hash=hex(java.security.MessageDigest.getInstance("SHA-1").digest(recorded));
        assertEquals("2c592ccd71ca2d5dadebeb6543ad6b18e6f43305",hash);
        String url="https://piston-meta.mojang.com/v1/packages/"+hash+"/1.20.4.json";
        LaunchTransport transport=new LaunchTransport(){
            public JsonObject json(String method,URI uri,Map<String,String> headers,byte[] body,Cancellation cancellation)throws IOException{
                if(!uri.toString().equals("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))throw new IOException("Unexpected network request forbidden");
                return JsonParser.parseString("{\"versions\":[{\"id\":\"1.20.4\",\"url\":\""+url+"\",\"sha1\":\""+hash+"\"}]}").getAsJsonObject();
            }
            public void download(URI uri,Path destination,Cancellation cancellation)throws IOException{
                if(!url.equals(uri.toString()))throw new IOException("Only the recorded metadata is available");Files.write(destination,recorded);
            }
        };
        JsonObject version=new MojangMetadata(transport,new ArtifactCache(temporary,transport)).version("1.20.4",new Cancellation());
        assertEquals("net.minecraft.client.main.Main",version.get("mainClass").getAsString());
        assertEquals(17,version.getAsJsonObject("javaVersion").get("majorVersion").getAsInt());
        assertEquals(88,version.getAsJsonArray("libraries").size());
        LaunchPlatform platform=new LaunchPlatform("osx","aarch64","fixture",Collections.emptyMap());
        int armNatives=0;
        for(JsonElement value:version.getAsJsonArray("libraries")){
            JsonObject library=value.getAsJsonObject();
            if(library.get("name").getAsString().endsWith(":natives-macos-arm64")&&platform.allows(library))armNatives++;
        }
        assertTrue(armNatives>0,"The public release includes Apple Silicon natives");
    }
    private static String hex(byte[] bytes){StringBuilder text=new StringBuilder();for(byte value:bytes)text.append(String.format("%02x",value&255));return text.toString();}
}
