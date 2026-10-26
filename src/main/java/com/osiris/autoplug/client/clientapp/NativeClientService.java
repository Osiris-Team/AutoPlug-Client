package com.osiris.autoplug.client.clientapp;

import com.osiris.autoplug.client.browser.*;
import com.osiris.autoplug.client.launcher.*;
import com.osiris.autoplug.client.profiles.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Instant;

/** Shared UI/CLI orchestration. A launch never mutates an existing server installation. */
public final class NativeClientService {
    public interface Confirmation { boolean confirm(String message); }
    public interface LaunchExecutor { Process launch(Profile profile, LaunchAccount account, ServerAddress address) throws Exception; }
    public interface Migration {
        ProfileUpdater.Plan check(Profile profile) throws Exception;
        void apply(ProfileUpdater.Plan plan, boolean allowDisabling) throws Exception;
    }
    public final ProfileStore profiles;
    public final ServerCatalog servers;
    public final ClientSettings settings;
    private final LaunchExecutor launcher;
    private final Migration migration;
    private final AtomicBoolean working = new AtomicBoolean();
    private volatile LaunchAccount authenticatedAccount;
    private volatile MicrosoftOAuth oauthSession;
    private volatile long authenticationGeneration;
    private static volatile NativeClientService instance;
    public NativeClientService(ProfileStore profiles, ServerCatalog servers, ClientSettings settings, LaunchExecutor launcher, Migration migration) {
        this.profiles = profiles; this.servers = servers; this.settings = settings; this.launcher = launcher; this.migration = migration;
    }
    public static synchronized NativeClientService getDefault() throws IOException {
        if (instance == null) {
            ProfileStore store = ProfileStore.defaultStore(); ClientSettings settings = new ClientSettings(store.getRoot());
            Path minecraftCache = store.getRoot().resolve("cache").resolve("minecraft");
            ManagedJavaRuntime runtimes = new ManagedJavaRuntime(minecraftCache.resolve("runtime"));
            NativeLauncher nativeLauncher = new NativeLauncher(minecraftCache, new LauncherDownloads.HttpsTransport(), LauncherPlatform.current(), major -> {
                Path override = settings.javaOverride(major);
                if (override != null) { ManagedJavaRuntime.verifyMajor(override, major); return override; }
                return runtimes.resolve(major);
            });
            ProfileUpdater updater = new ProfileUpdater(store.getCache());
            instance = new NativeClientService(store, new ServerCatalog(store.getRoot()), settings, (profile, account, address) -> {
                LauncherDownloads.checkInterrupted();
                LaunchPlan plan = nativeLauncher.prepare(profile.gameVersion, profile.loader, profile.directoryPath, account);
                Path override = settings.javaOverride(plan.getJavaMajor());
                if (override != null) ManagedJavaRuntime.verifyMajor(override, plan.getJavaMajor());
                Path java = override != null ? override : nativeLauncher.resolveJava(plan);
                LauncherDownloads.checkInterrupted();
                return plan.launch(java, address == null ? null : address.host, address == null ? 25565 : address.port);
            }, new Migration() {
                public ProfileUpdater.Plan check(Profile profile) throws Exception { return updater.check(profile); }
                public void apply(ProfileUpdater.Plan plan, boolean allowDisabling) throws Exception { updater.apply(plan, allowDisabling); }
            });
        }
        return instance;
    }
    public synchronized void setAuthenticatedAccount(LaunchAccount account) { setAuthenticatedAccount(account, null); }
    public synchronized void setAuthenticatedAccount(LaunchAccount account, MicrosoftOAuth oauth) {
        Objects.requireNonNull(account);
        authenticationGeneration++;
        if (oauthSession != null && oauthSession != oauth) oauthSession.clearSession();
        oauthSession = oauth; authenticatedAccount = account;
    }
    public synchronized void signOut() { authenticationGeneration++; MicrosoftOAuth oauth = oauthSession; if (oauth != null) oauth.clearSession(); oauthSession = null; authenticatedAccount = null; }
    public synchronized long beginAuthentication() { return ++authenticationGeneration; }
    public boolean authenticationCurrent(long attempt) { return attempt == authenticationGeneration; }
    public synchronized void cancelAuthentication(long attempt) { if (authenticationCurrent(attempt)) authenticationGeneration++; }
    public synchronized boolean completeAuthentication(long attempt, LaunchAccount account, MicrosoftOAuth oauth) {
        if (!authenticationCurrent(attempt)) { if (oauth != null) oauth.clearSession(); return false; }
        setAuthenticatedAccount(account, oauth); return true;
    }
    /** Preference changes cancel pending authentication without discarding an unchanged online session. */
    public synchronized void applyPreferences(String offlineName, boolean offlineMode, String clientId, String templateId, Map<Integer, String> paths) throws IOException {
        applyPreferences(offlineName, offlineMode, clientId, templateId, paths, settings.worldPort(), settings.upnpAllowed());
    }
    public synchronized void applyPreferences(String offlineName, boolean offlineMode, String clientId, String templateId, Map<Integer, String> paths, int worldPort, boolean upnpAllowed) throws IOException {
        String previousClient = settings.microsoftClientId;
        settings.apply(offlineName, offlineMode, clientId, templateId, paths, worldPort, upnpAllowed);
        if (offlineMode || !Objects.equals(previousClient, clientId)) signOut();
        else authenticationGeneration++;
    }
    public synchronized void useOfflineAccount(String name) throws IOException {
        applyPreferences(name, true, settings.microsoftClientId, settings.templateId, settings.javaPaths);
    }
    /** Check cancellation, persist the mode and install the account as one service transaction. */
    public synchronized boolean completeAuthenticationAndSave(long attempt, LaunchAccount account, MicrosoftOAuth oauth, String clientId) throws IOException {
        if (!authenticationCurrent(attempt)) { if (oauth != null) oauth.clearSession(); return false; }
        Objects.requireNonNull(account);
        try { settings.apply(settings.offlineName, false, clientId, settings.templateId, settings.javaPaths); }
        catch (IOException | RuntimeException failure) { if (oauth != null) oauth.clearSession(); cancelAuthentication(attempt); throw failure; }
        setAuthenticatedAccount(account, oauth); return true;
    }
    public boolean isSignedIn() { return authenticatedAccount != null; }
    public String accountLabel() { LaunchAccount account = authenticatedAccount; return account == null ? "Not signed in" : account.getName(); }
    private LaunchAccount account() throws IOException {
        if (settings.offlineMode) return LaunchAccount.offline(settings.offlineName);
        LaunchAccount account;
        MicrosoftOAuth session;
        synchronized (this) { account = authenticatedAccount; session = oauthSession; }
        if (account == null) throw new IllegalStateException("Sign in with Microsoft in Settings first, or explicitly enable offline testing");
        if (session != null && session.getAccessTokenExpiresAt() != null && Instant.now().plusSeconds(60).isAfter(session.getAccessTokenExpiresAt())) {
            String refresh = session.getRefreshToken();
            if (refresh == null) { signOut(); throw new IOException("Account session expired. Sign in again."); }
            LaunchAccount refreshed = session.refresh(refresh);
            synchronized (this) {
                if (session != oauthSession || account != authenticatedAccount) { session.clearSession(); throw new IOException("Account session changed while refreshing; launch cancelled"); }
                account = refreshed; authenticatedAccount = account;
            }
        }
        return account;
    }
    public Process launch(String id, ServerAddress address) throws Exception {
        if (!working.compareAndSet(false, true)) throw new IllegalStateException("A client preparation is already running");
        try { Profile profile = profiles.get(id); requireClient(profile); return launcher.launch(profile, account(), address); }
        finally { working.set(false); }
    }
    public Process autoPlay(ServerStatus status, Confirmation confirmation) throws Exception {
        if (!working.compareAndSet(false, true)) throw new IllegalStateException("A client preparation is already running");
        Profile cloned = null;
        try {
            LaunchAccount account = account();
            String version = status.gameVersion(), loader = status.server.loaderHint;
            for (Profile profile : profiles.list()) {
                if (profile.type == Profile.Type.MODS && profile.gameVersion.equals(version) && profile.loader.equals(loader) && !profile.isTemplate)
                    return launcher.launch(profile, account, status.server.address);
            }
            Profile template = selectTemplate(loader);
            if (!confirmation.confirm("No " + loader + " profile for " + version + ". Clone template '" + template.id + "', migrate its mods and join " + status.server.address + "?")) return null;
            cloned = profiles.clone(template.id, version);
            ProfileUpdater.Plan plan = migration.check(cloned);
            if (!plan.errors.isEmpty()) throw new IOException(String.join("\n", plan.errors));
            boolean disabling = !plan.disabled().isEmpty();
            if (plan.hasChanges() && !confirmation.confirm(plan.summary() + "\nApply these changes to the new profile?")) { profiles.delete(cloned.id); cloned = null; return null; }
            migration.apply(plan, disabling);
            Process process = launcher.launch(cloned, account, status.server.address);
            cloned = null; return process;
        } catch (Exception failure) {
            if (cloned != null) try { profiles.delete(cloned.id); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        } finally { working.set(false); }
    }
    private Profile selectTemplate(String loader) throws IOException {
        if (!settings.templateId.isEmpty()) {
            Profile selected = profiles.get(settings.templateId);
            if (selected.type != Profile.Type.MODS || !selected.isTemplate || !selected.loader.equals(loader)) throw new IOException("Default template must be a client template using " + loader);
            return selected;
        }
        for (Profile profile : profiles.list()) if (profile.type == Profile.Type.MODS && profile.isTemplate && profile.loader.equals(loader)) return profile;
        throw new IOException("Create a " + loader + " client template in Profiles and choose it in Settings");
    }
    private static void requireClient(Profile profile) { if (profile.type != Profile.Type.MODS) throw new IllegalArgumentException("Only MODS client profiles can launch a Minecraft client; server profiles require a virtual world"); }
}
