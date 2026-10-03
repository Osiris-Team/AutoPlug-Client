package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Optional local account persistence. Call save only when the user chooses to remember an account. */
public final class AccountStore {
    private final Path file;
    public AccountStore(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public synchronized List<MinecraftAccount> list() throws IOException {
        if (!Files.exists(file)) return new ArrayList<>();
        List<MinecraftAccount> result = new ArrayList<>();
        try {
            JsonArray accounts = LauncherFiles.readJson(file).getAsJsonArray("accounts");
            for (JsonElement value : accounts) {
                JsonObject a = value.getAsJsonObject();
                result.add(new MinecraftAccount(a.get("username").getAsString(), a.get("uuid").getAsString(),
                        a.get("accessToken").getAsString(), a.get("refreshToken").getAsString(),
                        a.get("clientId").getAsString(), a.get("xuid").getAsString(), a.get("offline").getAsBoolean(),
                        Instant.parse(a.get("expiresAt").getAsString())));
            }
        } catch (RuntimeException e) { throw new IOException("Account settings could not be read. Sign in again or restore the settings file.", e); }
        return result;
    }
    public synchronized void save(MinecraftAccount account) throws IOException {
        List<MinecraftAccount> accounts = list();
        accounts.removeIf(existing -> existing.uuid.equals(account.uuid) && existing.offline == account.offline);
        accounts.add(account);
        write(accounts);
    }
    public synchronized void remove(String uuid) throws IOException {
        List<MinecraftAccount> accounts = list();
        accounts.removeIf(account -> account.uuid.equals(uuid.replace("-", "")));
        write(accounts);
    }
    private void write(List<MinecraftAccount> accounts) throws IOException {
        Files.createDirectories(file.getParent());
        JsonArray array = new JsonArray();
        for (MinecraftAccount account : accounts) {
            JsonObject value = new JsonObject();
            value.addProperty("username", account.username); value.addProperty("uuid", account.uuid);
            value.addProperty("accessToken", account.accessToken); value.addProperty("refreshToken", account.refreshToken);
            value.addProperty("clientId", account.clientId); value.addProperty("xuid", account.xuid);
            value.addProperty("offline", account.offline); value.addProperty("expiresAt", account.expiresAt.toString());
            array.add(value);
        }
        JsonObject root = new JsonObject(); root.add("accounts", array);
        Path temporary = Files.createTempFile(file.getParent(), ".accounts-", ".tmp");
        try {
            restrict(temporary);
            Files.write(temporary, root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            LauncherFiles.move(temporary, file);
        } finally { Files.deleteIfExists(temporary); }
    }
    static void restrict(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        else {
            AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (view == null) throw new IOException("This filesystem cannot protect account credentials. Choose a local account/profile folder with owner-only file permissions.");
            view.setAcl(Collections.singletonList(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(Files.getOwner(path)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        }
    }
}
