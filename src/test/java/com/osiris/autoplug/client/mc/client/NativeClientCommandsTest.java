package com.osiris.autoplug.client.mc.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osiris.autoplug.client.mc.launch.Cancellation;
import com.osiris.autoplug.client.mc.launch.LaunchTransport;
import com.osiris.autoplug.client.mc.profiles.Profile;
import com.osiris.autoplug.client.mc.profiles.ProfileType;
import com.osiris.autoplug.client.mc.update.ProfileUpdateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NativeClientCommandsTest {
    @TempDir Path temporaryDirectory;

    @Test void tokenizerPreservesQuotedPathsWindowsBackslashesAndEmptyValues() {
        assertArrayEquals(new String[]{".mc", "settings", "set", "javaHomes", "C:\\Program Files\\Java"},
                NativeClientCommands.tokenize(".mc settings set javaHomes \"C:\\Program Files\\Java\""));
        assertArrayEquals(new String[]{".mc", "settings", "set", "defaultTemplate", ""}, NativeClientCommands.tokenize(".mc settings set defaultTemplate ''"));
        assertArrayEquals(new String[]{"one path", "two words", "a\"b"}, NativeClientCommands.tokenize("one\\ path 'two words' \"a\\\"b\""));
        assertThrows(IllegalArgumentException.class, () -> NativeClientCommands.tokenize(".mc launch \"unterminated"));
        assertArrayEquals(new String[0], NativeClientCommands.tokenize("  "));
    }

    @Test void leavesLegacyCommandsForTheExistingDispatcher() throws Exception {
        try (NativeClientContext context = context()) {
            NativeClientCommands commands = new NativeClientCommands(context);
            PrintStream out = new PrintStream(new ByteArrayOutputStream());
            assertFalse(commands.dispatch(new String[0], out));
            assertFalse(commands.dispatch(NativeClientCommands.tokenize(".check mods"), out));
            assertFalse(commands.dispatch(NativeClientCommands.tokenize(".update server"), out));
            assertFalse(commands.dispatch(NativeClientCommands.tokenize(".restart"), out));
        }
    }

    @Test void profileCrudAndTemplateCommandsUseTheSharedRepository() throws Exception {
        try (NativeClientContext context = context()) {
            NativeClientCommands commands = new NativeClientCommands(context);
            dispatch(commands, ".profiles create base 1.20.4 FABRIC --template");
            assertTrue(context.profiles().require("base").isTemplate());
            dispatch(commands, ".profiles clone base copied");
            assertFalse(context.profiles().require("copied").isTemplate());
            dispatch(commands, ".profiles rename copied renamed");
            assertFalse(context.profiles().find("copied").isPresent());
            dispatch(commands, ".profiles template renamed true");
            assertTrue(context.profiles().require("renamed").isTemplate());
            dispatch(commands, ".profiles create server 1.20.4 NEOFORGE --type MODS-SERVER");
            assertEquals(ProfileType.MODS_SERVER, context.profiles().require("server").getType());
            assertTrue(dispatch(commands, ".profiles list --type MODS-SERVER").contains("server"));
            dispatch(commands, ".profiles delete renamed");
            assertFalse(context.profiles().find("renamed").isPresent());
        }
    }

    @Test void checkIsReadOnlyAndUpdateNeedsExplicitDisabledAssetConfirmation() throws Exception {
        try (NativeClientContext context = context()) {
            Profile profile = context.profiles().create("client", ProfileType.MODS, "1.20.4", "FABRIC", false);
            try (java.util.jar.JarOutputStream jar = new java.util.jar.JarOutputStream(Files.newOutputStream(profile.getAssetsDirectory().resolve("fixture.jar")))) {
                jar.putNextEntry(new java.util.jar.JarEntry("synthetic.txt"));
                jar.write("Synthetic CLI fixture".getBytes(StandardCharsets.UTF_8)); jar.closeEntry();
            }
            NativeClientCommands commands = new NativeClientCommands(context);
            assertTrue(dispatch(commands, ".check mods --profile=client --version 1.21").contains("DISABLE"));
            assertTrue(Files.exists(profile.getAssetsDirectory().resolve("fixture.jar")));
            assertEquals("1.20.4", context.profiles().require("client").getGameVersion());
            assertTrue(dispatch(commands, ".update mods --profile client --version 1.21").contains("Nothing changed"));
            assertTrue(Files.exists(profile.getAssetsDirectory().resolve("fixture.jar")));
            dispatch(commands, ".update mods --profile client --version 1.21 --confirm-disable");
            assertFalse(Files.exists(profile.getAssetsDirectory().resolve("fixture.jar")));
            assertTrue(Files.exists(profile.getAssetsDirectory().resolve("fixture.jar.disabled")));
            assertEquals("1.21", context.profiles().require("client").getGameVersion());
        }
    }

    @Test void invalidOptionsAndMismatchedAssetTypesDoNotMutateProfiles() throws Exception {
        try (NativeClientContext context = context()) {
            NativeClientCommands commands = new NativeClientCommands(context);
            assertThrows(IllegalArgumentException.class, () -> dispatch(commands, ".profiles create bad 1.20.4 FABRIC --typo ignored"));
            assertFalse(context.profiles().find("bad").isPresent());
            context.profiles().create("client", ProfileType.MODS, "1.20.4", "FABRIC", false);
            assertThrows(IllegalArgumentException.class, () -> dispatch(commands, ".update plugins --profile client"));
            assertThrows(IllegalArgumentException.class, () -> dispatch(commands, ".profiles template client perhaps"));
        }
    }

    @Test void referencedWorldProfilesCannotBeRenamedDeletedOrTurnedIntoTemplates() throws Exception {
        try (NativeClientContext context = context()) {
            context.profiles().create("server", ProfileType.PLUGINS, "1.20.4", "PAPER", false);
            NativeClientCommands commands = new NativeClientCommands(context);
            dispatch(commands, ".mc worlds create world server");
            assertThrows(IOException.class, () -> dispatch(commands, ".profiles rename server renamed"));
            assertThrows(IOException.class, () -> dispatch(commands, ".profiles delete server"));
            assertThrows(IOException.class, () -> dispatch(commands, ".profiles template server true"));
            assertTrue(dispatch(commands, ".mc worlds list").contains("world | server"));
            assertThrows(IOException.class, () -> dispatch(commands, ".mc worlds share world"));
            assertEquals("false", readEula(context.worlds().find("world").orElseThrow(AssertionError::new).getDirectory().resolve("eula.txt")));
        }
    }

    @Test void addsAndImportsServersReadOnlyWithoutPingingOrConnecting() throws Exception {
        try (NativeClientContext context = context()) {
            NativeClientCommands commands = new NativeClientCommands(context);
            dispatch(commands, ".mc servers add 'Synthetic Server' localhost:25566 VANILLA");
            assertEquals(1, context.servers().list().size());
            assertTrue(dispatch(commands, ".mc servers list").contains("Synthetic Server"));
            Path input = temporaryDirectory.resolve("servers.dat"); byte[] original = serversFixture(); Files.write(input, original);
            assertTrue(dispatch(commands, ".mc servers import '" + input + "' VANILLA").contains("Imported 1"));
            assertEquals(2, context.servers().list().size());
            assertArrayEquals(original, Files.readAllBytes(input));
        }
    }

    @Test void joinWithoutAnExplicitLoaderOrCompatibleProfileDoesNotStartProcesses() throws Exception {
        try (NativeClientContext context = context()) {
            NativeClientCommands commands = new NativeClientCommands(context);
            assertThrows(IllegalArgumentException.class, () -> dispatch(commands, ".mc servers join example.invalid --version 1.20.4"));
            assertTrue(dispatch(commands, ".mc servers join example.invalid --version 1.20.4 --loader VANILLA").contains("No compatible profile"));
            context.profiles().create("base", ProfileType.MODS, "1.20.4", "VANILLA", true);
            context.settings().set("defaultTemplate", "base");
            assertTrue(dispatch(commands, ".mc servers join example.invalid --version 1.20.4 --loader VANILLA").contains("require approval"));
            assertEquals(1, context.profiles().list().size());
        }
    }

    @Test void fixtureModeAndNonSecretSettingsAreExplicitAndNeverAcceptTokenSettings() throws Exception {
        try (NativeClientContext context = context()) {
            NativeClientCommands commands = new NativeClientCommands(context);
            assertTrue(dispatch(commands, ".mc offline Fixture").contains("not Microsoft authentication"));
            assertEquals("true", context.settings().get("offlineFixtureMode", ""));
            dispatch(commands, ".mc settings set clientId fixture-public-id");
            assertTrue(dispatch(commands, ".mc settings show").contains("clientId=fixture-public-id"));
            assertThrows(IllegalArgumentException.class, () -> dispatch(commands, ".mc settings set accessToken should-not-save"));
            assertFalse(new String(Files.readAllBytes(context.getDataRoot().resolve("client-settings.properties")), StandardCharsets.UTF_8).contains("should-not-save"));
        }
    }

    @Test void missingClientIdFailsBeforeStartingAnyAuthenticationWorker() throws Exception {
        try (NativeClientContext context = context()) {
            NativeClientCommands commands = new NativeClientCommands(context);
            assertThrows(IllegalArgumentException.class, () -> dispatch(commands, ".mc signin"));
            assertThrows(IllegalArgumentException.class, () -> dispatch(commands, ".mc device login"));
            assertTrue(dispatch(commands, ".mc device cancel").contains("cancellation requested"));
            assertEquals("Not signed in", context.getAccountLabel());
        }
    }

    @Test void approvedVanillaTemplateCloneContinuesToLaunchAndStillRequiresAnExplicitSession() throws Exception {
        try (NativeClientContext context = context()) {
            context.profiles().create("base", ProfileType.MODS, "1.20.4", "VANILLA", true);
            context.settings().set("defaultTemplate", "base");
            IOException error = assertThrows(IOException.class, () -> dispatch(new NativeClientCommands(context),
                    ".mc servers join example.invalid --version 1.20.4 --loader VANILLA --clone approved"));
            assertTrue(error.getMessage().contains("Sign in"));
            assertFalse(context.profiles().require("approved").isTemplate());
            assertTrue(Files.exists(context.profiles().require("approved").getDirectory().resolve("loader.properties")));
            assertEquals("Not signed in", context.getAccountLabel());
        }
    }

    @Test void closingTheSharedCommandInstanceCancelsPendingDeviceLoginWithoutXboxRequests() throws Exception {
        java.util.concurrent.CountDownLatch requested = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        LaunchTransport transport = new LaunchTransport() {
            @Override public JsonObject json(String method, URI uri, Map<String, String> headers, byte[] body, Cancellation cancellation) throws IOException {
                cancellation.check(); calls.incrementAndGet(); requested.countDown();
                if (!uri.toString().endsWith("/devicecode")) throw new IOException("No Xbox/authentication service calls in this fixture");
                return JsonParser.parseString("{\"device_code\":\"synthetic-only\",\"user_code\":\"FAKE-CODE\",\"verification_uri\":\"https://microsoft.com/devicelogin\",\"expires_in\":300,\"interval\":5}").getAsJsonObject();
            }
            @Override public void download(URI uri, Path path, Cancellation cancellation) throws IOException { throw new IOException("No downloads"); }
        };
        try (NativeClientContext context = context(transport)) {
            context.settings().set("clientId", "fixture-public-id");
            NativeClientCommands commands = new NativeClientCommands(context);
            dispatch(commands, ".mc signin");
            assertTrue(requested.await(2, java.util.concurrent.TimeUnit.SECONDS));
            commands.close(); commands.close();
            assertEquals(1, calls.get()); assertEquals("Not signed in", context.getAccountLabel());
            assertThrows(IOException.class, () -> dispatch(commands, ".mc help"));
        }
    }

    @Test void renamingAndDeletingTheDefaultTemplateKeepsPersistedSettingsConsistent() throws Exception {
        try (NativeClientContext context = context()) {
            context.profiles().create("base", ProfileType.MODS, "1.20.4", "VANILLA", true);
            context.settings().set("defaultTemplate", "base"); context.settings().save();
            NativeClientCommands commands = new NativeClientCommands(context);
            dispatch(commands, ".profiles rename base renamed");
            assertEquals("renamed", new ClientSettings(context.getDataRoot()).get("defaultTemplate", ""));
            dispatch(commands, ".profiles delete renamed");
            assertEquals("", new ClientSettings(context.getDataRoot()).get("defaultTemplate", ""));
        }
    }

    @Test void worldLaunchConfigurationIsPersistedAndProtectsItsClientProfileReference() throws Exception {
        try (NativeClientContext context = context()) {
            context.profiles().create("server", ProfileType.PLUGINS, "1.20.4", "PAPER", false);
            context.profiles().create("client", ProfileType.MODS, "1.20.4", "VANILLA", false);
            NativeClientCommands commands = new NativeClientCommands(context); dispatch(commands, ".mc worlds create world server");
            Path serverJar = temporaryDirectory.resolve("synthetic server.jar");
            try (java.util.jar.JarOutputStream jar = new java.util.jar.JarOutputStream(Files.newOutputStream(serverJar))) {
                jar.putNextEntry(new java.util.jar.JarEntry("synthetic.txt")); jar.write(new byte[]{1}); jar.closeEntry();
            }
            assertTrue(dispatch(commands, ".mc worlds configure world --client client --server-jar '" + serverJar + "'").contains("no EULA was accepted"));
            assertEquals("client", context.worldConfig("world").getClientProfileId());
            assertEquals(serverJar.toRealPath(), context.worldConfig("world").getServerJar());
            assertThrows(IOException.class, () -> dispatch(commands, ".profiles rename client renamed"));
            assertThrows(IOException.class, () -> dispatch(commands, ".profiles delete client"));
            assertEquals("false", readEula(context.worlds().find("world").orElseThrow(AssertionError::new).getDirectory().resolve("eula.txt")));
        }
    }

    private NativeClientContext context() throws Exception {
        LaunchTransport transport = new LaunchTransport() {
            @Override public JsonObject json(String method, URI uri, Map<String, String> headers, byte[] body, Cancellation cancellation) throws IOException { throw new IOException("No external requests in CLI fixtures"); }
            @Override public void download(URI uri, Path target, Cancellation cancellation) throws IOException { throw new IOException("No downloads in CLI fixtures"); }
        };
        return context(transport);
    }

    private NativeClientContext context(LaunchTransport transport) throws Exception {
        Path root = temporaryDirectory.toRealPath().resolve("data");
        ClientSettings settings = new ClientSettings(root); settings.set("cacheDirectory", temporaryDirectory.toRealPath().resolve("isolated-cache").toString()); settings.save();
        ProfileUpdateService.Engine engine = new ProfileUpdateService.Engine() {
            @Override public ProfileUpdateService.Decision resolve(Profile profile, Path jar, String target) {
                return target.equals("1.21") ? ProfileUpdateService.Decision.incompatible("Synthetic provider reports no target release") : ProfileUpdateService.Decision.keep("Synthetic compatible fixture");
            }
            @Override public void download(ProfileUpdateService.Decision decision, Path target) throws IOException { throw new IOException("No downloads in CLI fixtures"); }
        };
        return new NativeClientContext(root, temporaryDirectory, transport, engine);
    }

    private String dispatch(NativeClientCommands commands, String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(commands.dispatch(NativeClientCommands.tokenize(text), new PrintStream(output, true, "UTF-8")));
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
    private String readEula(Path file) throws IOException {
        java.util.Properties properties = new java.util.Properties();
        try (java.io.InputStream input = Files.newInputStream(file)) { properties.load(input); }
        return properties.getProperty("eula");
    }
    private byte[] serversFixture() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(10); output.writeUTF(""); output.writeByte(9); output.writeUTF("servers"); output.writeByte(10); output.writeInt(1);
            output.writeByte(8); output.writeUTF("name"); output.writeUTF("Imported synthetic");
            output.writeByte(8); output.writeUTF("ip"); output.writeUTF("127.0.0.1:25567"); output.writeByte(0); output.writeByte(0);
        }
        return bytes.toByteArray();
    }
}
