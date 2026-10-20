package com.osiris.autoplug.client.clientapp;

import com.osiris.autoplug.client.browser.*;
import com.osiris.autoplug.client.launcher.*;
import java.util.*;
import java.util.function.Consumer;

/** Headless client commands share the same profile store, preferences and launch service as Swing. */
public final class NativeClientCommands {
    private final NativeClientService service;
    public NativeClientCommands(NativeClientService service) { this.service = service; }
    public static boolean tryHandle(String command, Consumer<String> output) {
        if (command == null || !(command.trim().equals(".client") || command.trim().startsWith(".client "))) return false;
        try { return new NativeClientCommands(NativeClientService.getDefault()).handle(command, output); }
        catch (Exception exception) { output.accept("Client command failed: " + exception.getMessage()); return true; }
    }
    public boolean handle(String command, Consumer<String> output) {
        if (command == null || !(command.trim().equals(".client") || command.trim().startsWith(".client "))) return false;
        try {
            String[] words = command.trim().split("\\s+");
            if (words.length == 1 || words[1].equals("help")) {
                output.accept(".client launch <profile> [--server <host[:port]>]\n.client servers list|import|export\n.client auth offline <name>\n.client auth microsoft <registered-public-client-id>\n.client auth signout");
            } else if (words[1].equals("launch")) {
                if (words.length != 3 && !(words.length == 5 && words[3].equals("--server"))) throw new IllegalArgumentException("Usage: .client launch <profile> [--server <host[:port]>]");
                service.launch(words[2], words.length == 5 ? ServerAddress.parse(words[4]) : null);
                output.accept("Minecraft process started for " + words[2]);
            } else if (words[1].equals("servers") && words.length == 3) {
                switch (words[2]) {
                    case "list": for (SavedServer server : service.servers.list()) {
                        ServerStatus status = ServerStatus.ping(server);
                        output.accept(server + " | " + (status.online ? status.version + " protocol=" + status.protocol + " | " + status.players + "/" + status.capacity + " | " + status.latency + "ms | " + status.motd : status.failure));
                    } break;
                    case "import": output.accept("Imported " + service.servers.importVanilla(ServerCatalog.vanillaPath()) + " servers"); break;
                    case "export": output.accept("Exported servers.dat; backup: " + service.servers.exportVanilla(ServerCatalog.vanillaPath())); break;
                    default: throw new IllegalArgumentException("Usage: .client servers list|import|export");
                }
            } else if (words[1].equals("auth")) {
                if (words.length == 3 && words[2].equals("signout")) { service.signOut(); output.accept("Signed out"); }
                else if (words.length == 4 && words[2].equals("offline")) {
                    service.useOfflineAccount(words[3]);
                    output.accept("Offline testing enabled; online authenticated servers require a licensed Microsoft account");
                } else if (words.length == 4 && words[2].equals("microsoft")) {
                    MicrosoftOAuth oauth = new MicrosoftOAuth(words[3]);
                    long attempt = service.beginAuthentication();
                    MicrosoftOAuth.DeviceAuthorization device = oauth.beginDeviceAuthorization();
                    output.accept("Open " + device.getVerificationUri() + " and enter " + device.getUserCode() + ". Waiting for your sign-in until " + device.getExpiresAt());
                    LaunchAccount account = oauth.awaitMinecraftAccount(device, () -> Thread.currentThread().isInterrupted() || !service.authenticationCurrent(attempt));
                    if (!service.completeAuthenticationAndSave(attempt, account, oauth, words[3])) throw new IllegalStateException("Sign-in cancelled");
                    output.accept("Signed in as " + account.getName() + "; session is kept only in memory");
                } else throw new IllegalArgumentException("Usage: .client auth offline <name>|microsoft <client-id>|signout");
            } else throw new IllegalArgumentException("Unknown client command. Enter .client help");
        } catch (Exception exception) { output.accept("Client command failed: " + exception.getMessage()); }
        return true;
    }
}
