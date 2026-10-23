package com.osiris.autoplug.client.profiles;

import com.osiris.autoplug.client.tasks.updater.TaskDownload;
import com.osiris.autoplug.client.tasks.updater.mods.InstalledModLoader;
import com.osiris.autoplug.client.tasks.updater.mods.MinecraftMod;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.plugins.ResourceFinder;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.betterthread.BThreadManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Shared updater engine adapter. Every writable path is scoped to the selected profile/cache. */
public class ProfileUpdates {
    public interface Resolver { SearchResult resolve(Profile profile, CollectionEntry entry, Path installed) throws Exception; }
    public interface Downloader { void download(SearchResult result, Path destination) throws Exception; }
    public enum Status { CURRENT, REPLACE, INCOMPATIBLE, UNVERIFIED, EXCLUDED, ERROR }
    public class Change {
        public CollectionEntry entry;
        public SearchResult release;
        public Status status;
        public String reason, originalDigest;
    }
    public class Plan {
        public Profile profile;
        public ProfileCollection collection;
        private String collectionFingerprint;
        public List<Change> changes = new ArrayList<>();
        public boolean hasErrors() { return changes.stream().anyMatch(c -> c.status == Status.ERROR); }
        public String summary() {
            StringBuilder out = new StringBuilder(profile.name + " — " + profile.gameVersion + " / " + profile.loader + "\n");
            for (Change c : changes) out.append(c.entry.file).append(": ").append(c.status).append(" — ").append(c.reason).append('\n');
            if (changes.isEmpty()) out.append("Collection is empty.\n");
            if (profile.migrationPending) out.append("Migration: incompatible or unverified JARs will become .jar.disabled after confirmation. Configuration files are preserved.\n");
            if (hasErrors()) out.append("A provider lookup failed. No changes can be applied until it succeeds.\n");
            return out.toString();
        }
    }
    private final ProfileStore profiles;
    private final Resolver resolver;
    private final Downloader downloader;
    public ProfileUpdates(ProfileStore profiles) {
        this.profiles = profiles; this.resolver = this::findRelease;
        this.downloader = (release, file) -> {
            if (release.downloadUrl == null || !release.downloadUrl.startsWith("https://")) throw new IOException("A secure download URL is required");
            new TaskDownload("Profile artifact", new BThreadManager(), release.downloadUrl, file.toFile(), true).runAtStart();
        };
    }
    public ProfileUpdates(ProfileStore profiles, Resolver resolver, Downloader downloader) {
        this.profiles = profiles; this.resolver = resolver; this.downloader = downloader;
    }
    public Plan check(String id) throws Exception {
        Profile profile = profiles.get(id);
        try (ProfileLease ignored = new ProfileLease(profile.getDirectory())) { return plan(profile); }
    }
    private Plan plan(Profile profile) throws Exception {
        Plan plan = new Plan(); plan.profile = profile; plan.collection = new ProfileCollection().scan(profile);
        plan.collectionFingerprint = fingerprint(plan.collection);
        for (CollectionEntry entry : plan.collection.entries) {
            Change change = new Change(); change.entry = entry; plan.changes.add(change);
            Path file = plan.collection.resolve(profile, entry.file);
            change.originalDigest = profiles.getCache().digest(file);
            if (entry.excluded) {
                change.status = profile.migrationPending ? Status.UNVERIFIED : Status.EXCLUDED;
                change.reason = "Excluded from provider updates"; continue;
            }
            try {
                change.release = resolver.resolve(profile, entry, file);
                if (change.release == null) { change.status = Status.UNVERIFIED; change.reason = "No version-aware provider identity configured"; }
                else if (change.release.type == SearchResult.Type.API_ERROR) {
                    change.status = Status.ERROR; change.reason = change.release.exception == null ? "Provider request failed" : change.release.exception.getMessage();
                } else if (change.release.type == SearchResult.Type.RESOURCE_NOT_FOUND) {
                    change.status = Status.INCOMPATIBLE; change.reason = "No release for this game version and loader";
                } else if (change.release.type == SearchResult.Type.UP_TO_DATE) {
                    change.status = Status.CURRENT; change.reason = "Matches compatible release " + change.release.latestVersion;
                } else if (change.release.type == SearchResult.Type.UPDATE_AVAILABLE && !change.release.isPremium) {
                    change.status = Status.REPLACE; change.reason = "Install compatible release " + change.release.latestVersion;
                } else { change.status = Status.UNVERIFIED; change.reason = "Provider cannot supply an installable compatible artifact"; }
            } catch (Exception e) { change.status = Status.ERROR; change.reason = e.getMessage(); }
        }
        return plan;
    }
    /** The caller must display the returned check plan and obtain confirmation before invoking. */
    public String update(String id, boolean allowDisable) throws Exception { return apply(check(id), allowDisable); }
    public String apply(Plan plan, boolean allowDisable) throws Exception {
        Profile current = profiles.get(plan.profile.id);
        try (ProfileLease ignored = new ProfileLease(current.getDirectory())) {
            if (!Objects.equals(current.gameVersion, plan.profile.gameVersion) || !Objects.equals(current.loader, plan.profile.loader)
                    || !Objects.equals(current.loaderVersion, plan.profile.loaderVersion) || current.type != plan.profile.type
                    || current.migrationPending != plan.profile.migrationPending) throw new IOException("Profile changed; check again");
            if (!Objects.equals(plan.collectionFingerprint, fingerprint(new ProfileCollection().scan(current)))) throw new IOException("Collection or provider settings changed; check again");
            if (plan.hasErrors()) throw new IOException("Provider lookup failed. Nothing was changed.\n" + plan.summary());
            if (current.migrationPending && !allowDisable && plan.changes.stream().anyMatch(c -> c.status == Status.INCOMPATIBLE || c.status == Status.UNVERIFIED))
                throw new IOException("Confirmation required before disabling incompatible or unverified JARs.\n" + plan.summary());
            Map<Change, Path> replacements = new LinkedHashMap<>();
            Path staging = current.getDirectory().resolve(".updates").resolve(UUID.randomUUID().toString());
            Files.createDirectories(staging);
            // Stage and verify every download before changing any installed JAR.
            for (Change c : plan.changes) {
                Path file = plan.collection.resolve(current, c.entry.file);
                if (!Objects.equals(c.originalDigest, profiles.getCache().digest(file))) throw new IOException("Collection changed; check again: " + c.entry.file);
                if (c.status == Status.REPLACE) {
                    Path temp = staging.resolve(c.entry.file);
                    downloader.download(c.release, temp); verify(c.release, temp);
                    replacements.put(c, profiles.getCache().store(temp));
                }
                if (current.migrationPending && (c.status == Status.INCOMPATIBLE || c.status == Status.UNVERIFIED)
                        && Files.exists(file.resolveSibling(file.getFileName() + ".disabled")))
                    throw new IOException("A disabled backup already exists; rename it before migration: " + file);
            }
            Map<Path, Path> originals = new LinkedHashMap<>(); List<Path> disabled = new ArrayList<>();
            Path collectionFile = current.getDirectory().resolve("collection.json"), profileFile = current.getDirectory().resolve("profile.json");
            byte[] oldCollection = Files.exists(collectionFile) ? Files.readAllBytes(collectionFile) : null;
            byte[] oldProfile = Files.readAllBytes(profileFile);
            // Downloads may take time; recheck external edits immediately before the transaction.
            if (!Objects.equals(plan.collectionFingerprint, fingerprint(new ProfileCollection().scan(current)))) throw new IOException("Collection changed during download; check again");
            for (Change c : plan.changes) if (!Objects.equals(c.originalDigest, profiles.getCache().digest(plan.collection.resolve(current, c.entry.file)))) throw new IOException("Artifact changed during download; check again: " + c.entry.file);
            try {
                for (Change c : plan.changes) {
                    Path file = plan.collection.resolve(current, c.entry.file);
                    if (replacements.containsKey(c)) {
                        originals.put(file, profiles.getCache().store(file));
                        profiles.getCache().link(replacements.get(c), file);
                        c.entry.version = c.release.latestVersion;
                    } else if (current.migrationPending && (c.status == Status.INCOMPATIBLE || c.status == Status.UNVERIFIED)) {
                        Path dest = file.resolveSibling(file.getFileName() + ".disabled");
                        Files.move(file, dest); disabled.add(dest);
                    } else profiles.getCache().link(profiles.getCache().store(file), file);
                }
                for (Change c : plan.changes) if (c.release != null && c.release.modrinthProjectId != null)
                    c.entry.modrinthId = c.release.modrinthProjectId;
                plan.collection.save(current);
                current.migrationPending = false; current.migrationSummary = plan.summary(); profiles.save(current);
            } catch (Exception e) {
                for (Map.Entry<Path, Path> original : originals.entrySet()) try { profiles.getCache().link(original.getValue(), original.getKey()); } catch (Exception rollback) { e.addSuppressed(rollback); }
                for (Path file : disabled) try { Files.move(file, file.resolveSibling(file.getFileName().toString().replaceFirst("\\.disabled$", ""))); } catch (Exception rollback) { e.addSuppressed(rollback); }
                try { if (oldCollection == null) Files.deleteIfExists(collectionFile); else restoreMetadata(collectionFile, oldCollection); } catch (Exception rollback) { e.addSuppressed(rollback); }
                try { restoreMetadata(profileFile, oldProfile); } catch (Exception rollback) { e.addSuppressed(rollback); }
                throw e;
            }
            for (Path temp : replacements.keySet().stream().map(c -> staging.resolve(c.entry.file)).toArray(Path[]::new)) Files.deleteIfExists(temp);
            Files.deleteIfExists(staging);
            return "Applied updates.\n" + plan.summary();
        }
    }
    private String fingerprint(ProfileCollection collection) {
        List<CollectionEntry> entries = new ArrayList<>(collection.entries); entries.sort(Comparator.comparing(e -> e.file));
        return new com.google.gson.Gson().toJson(entries);
    }
    private void restoreMetadata(Path path, byte[] content) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), ".rollback-", ".tmp");
        try {
            Files.write(temporary, content);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private SearchResult findRelease(Profile profile, CollectionEntry entry, Path file) throws Exception {
        ResourceFinder finder = new ResourceFinder();
        SearchResult modrinth = null;
        if (entry.modrinthId != null && !entry.modrinthId.isEmpty()) {
            modrinth = finder.findCompatibleModrinthArtifact(compatibleLoaders(profile), entry.modrinthId, profile.gameVersion, file);
            if (modrinth.type != SearchResult.Type.RESOURCE_NOT_FOUND) return modrinth;
        }
        // Embedded mod ids and plugin names are not necessarily provider project ids.
        // An exact hash lookup avoids guessing identities for generic collections.
        String identified = finder.findModrinthProject(file);
        if (identified != null && !identified.equals(entry.modrinthId)) {
            modrinth = finder.findCompatibleModrinthArtifact(compatibleLoaders(profile), identified, profile.gameVersion, file);
            if (modrinth.type != SearchResult.Type.RESOURCE_NOT_FOUND) return modrinth;
        }
        if (profile.type != ProfileType.PLUGINS && entry.curseforgeId != null) {
            MinecraftMod mod = new MinecraftMod(file.toString(), entry.name, entry.version, entry.author, null, entry.curseforgeId, entry.customDownloadUrl);
            return finder.findModByCurseforgeId(new InstalledModLoader(profile.loader), mod, profile.gameVersion, false);
        }
        if (modrinth != null) return modrinth;
        // Generic Spigot/GitHub/custom releases lack target-game compatibility metadata.
        // They remain available for ordinary updates, but a migration must get an explicit compatible identity.
        if (profile.migrationPending) return null;
        MinecraftPlugin plugin = new MinecraftPlugin(file.toString(), entry.name, entry.version, entry.author, entry.spigotId, entry.bukkitId, entry.customDownloadUrl);
        plugin.githubRepoName = entry.githubRepo; plugin.githubAssetName = entry.githubAsset; plugin.customCheckURL = entry.customCheckUrl;
        if (entry.customCheckUrl != null) return finder.findByCustomCheckURL(plugin);
        if (entry.githubRepo != null) return finder.findByGithubUrl(plugin);
        if (entry.spigotId > 0) return finder.findPluginBySpigotId(plugin);
        if (entry.bukkitId > 0) return finder.findPluginByBukkitId(plugin);
        return null;
    }
    private List<String> compatibleLoaders(Profile profile) {
        switch (profile.loader) {
            case "PURPUR": return Arrays.asList("purpur", "paper", "spigot", "bukkit");
            case "PAPER": return Arrays.asList("paper", "spigot", "bukkit");
            case "SPIGOT": return Arrays.asList("spigot", "bukkit");
            case "QUILT": return Arrays.asList("quilt", "fabric");
            default: return List.of(profile.loader.toLowerCase(Locale.ROOT));
        }
    }
    static void verify(SearchResult result, Path path) throws Exception {
        if (!Files.isRegularFile(path) || Files.size(path) == 0) throw new IOException("Downloaded artifact is empty");
        if (result.fileSize >= 0 && Files.size(path) != result.fileSize) throw new IOException("Downloaded artifact size mismatch");
        String expected = result.sha512 != null ? result.sha512 : result.sha1;
        if (expected != null) {
            MessageDigest digest = MessageDigest.getInstance(result.sha512 != null ? "SHA-512" : "SHA-1");
            try (InputStream in = Files.newInputStream(path)) { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) != -1) digest.update(buf, 0, n); }
            StringBuilder actual = new StringBuilder(); for (byte b : digest.digest()) actual.append(String.format("%02x", b & 255));
            if (!expected.equalsIgnoreCase(actual.toString())) throw new IOException("Downloaded artifact checksum mismatch");
        }
        try (ZipFile jar = new ZipFile(path.toFile())) { if (!jar.entries().hasMoreElements()) throw new IOException("Downloaded JAR is empty"); }
    }
    /** Shared provider download and artifact verification for first-install recipes. */
    void downloadVerified(SearchResult result, Path destination) throws Exception {
        downloader.download(result, destination); verify(result, destination);
    }
}
