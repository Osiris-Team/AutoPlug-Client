/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.profiles;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** A reviewable, profile-scoped adapter around AutoPlug's existing resource/updater services. */
public final class ProfileUpdater {
    public interface Resolver {
        Resolution resolve(Profile profile, Path installed) throws Exception;
        default Resolution dependency(Profile profile, Dependency dependency) throws Exception { throw new IOException("Unresolved required dependency: " + dependency.projectId); }
    }
    public interface Downloader { void download(String url, Path destination) throws IOException; }
    public static final class Dependency {
        public final String projectId, versionId;
        public Dependency(String projectId, String versionId) { this.projectId = projectId; this.versionId = versionId; }
    }
    public static final class Resolution {
        public final boolean compatible;
        public final String projectId, versionId, version, fileName, url, sha1, sha512, md5;
        public final List<Dependency> dependencies;
        public final List<String> incompatibleProjects;
        public Resolution(boolean compatible, String projectId, String versionId, String version, String fileName, String url, String sha1, String sha512, List<Dependency> dependencies) {
            this(compatible, projectId, versionId, version, fileName, url, sha1, sha512, null, dependencies);
        }
        public Resolution(boolean compatible, String projectId, String versionId, String version, String fileName, String url, String sha1, String sha512, String md5, List<Dependency> dependencies) {
            this(compatible, projectId, versionId, version, fileName, url, sha1, sha512, md5, dependencies, Collections.emptyList());
        }
        public Resolution(boolean compatible, String projectId, String versionId, String version, String fileName, String url, String sha1, String sha512, String md5, List<Dependency> dependencies, List<String> incompatibleProjects) {
            this.compatible = compatible; this.projectId = projectId; this.versionId = versionId; this.version = version;
            this.fileName = fileName; this.url = url; this.sha1 = sha1; this.sha512 = sha512; this.md5 = md5;
            this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
            this.incompatibleProjects = Collections.unmodifiableList(new ArrayList<>(incompatibleProjects));
        }
        public static Resolution unsupported(String projectId) { return new Resolution(false, projectId, null, null, null, null, null, null, Collections.emptyList()); }
    }
    public enum Action { KEEP, UPDATE, ENABLE, DISABLE, ADD }
    public static final class Change {
        public final Path installed, destination;
        public final String originalSHA256;
        public final Action action;
        public final Resolution resolution;
        Change(Path installed, Path destination, String originalSHA256, Action action, Resolution resolution) {
            this.installed = installed; this.destination = destination; this.originalSHA256 = originalSHA256; this.action = action; this.resolution = resolution;
        }
        @Override public String toString() { return action + " " + (installed == null ? resolution.fileName : installed.getFileName()) + (resolution.version == null ? "" : " -> " + resolution.version); }
    }
    public static final class Plan {
        public final Profile profile;
        public final List<Change> changes;
        public final List<String> errors;
        Plan(Profile profile, List<Change> changes, List<String> errors) {
            this.profile = profile; this.changes = Collections.unmodifiableList(new ArrayList<>(changes)); this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }
        public List<Change> disabled() { List<Change> result = new ArrayList<>(); for (Change c : changes) if (c.action == Action.DISABLE) result.add(c); return Collections.unmodifiableList(result); }
        public boolean hasChanges() { for (Change c : changes) if (c.action != Action.KEEP) return true; return false; }
        public String summary() { StringBuilder text = new StringBuilder(profile.toString()); for (Change c : changes) text.append('\n').append(c); for (String error : errors) text.append("\nERROR: ").append(error); return text.toString(); }
    }
    private final SharedArtifactCache cache;
    private final Resolver resolver;
    private final Downloader downloader;
    public ProfileUpdater(SharedArtifactCache cache) { this(cache, new AutoPlugProfileResolver(), ProfileUpdater::downloadHttps); }
    public ProfileUpdater(SharedArtifactCache cache, Resolver resolver, Downloader downloader) {
        this.cache = Objects.requireNonNull(cache); this.resolver = Objects.requireNonNull(resolver); this.downloader = Objects.requireNonNull(downloader);
    }
    public Plan check(Profile profile) throws IOException {
        Path directory = profile.assetsDirectory();
        ProfileFiles.contained(profile.directoryPath, directory);
        Files.createDirectories(directory);
        List<Change> changes = new ArrayList<>(); List<String> errors = new ArrayList<>();
        Set<String> projects = new HashSet<>(); Set<Path> destinations = new HashSet<>();
        List<Dependency> dependencies = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            List<Path> ordered = new ArrayList<>(); for (Path file : files) ordered.add(file); ordered.sort(Comparator.comparing(p -> p.getFileName().toString()));
            for (Path installed : ordered) {
                String name = installed.getFileName().toString();
                if (!name.endsWith(".jar") && !name.endsWith(".jar.disabled")) continue;
                try {
                    if (!Files.isRegularFile(installed, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Linked/special artifact rejected");
                    String originalHash = SharedArtifactCache.hash(installed, "SHA-256");
                    Resolution resolution = resolver.resolve(profile, installed);
                    if (resolution == null) throw new IOException("Resolver returned no result");
                    boolean disabled = name.endsWith(".disabled");
                    Action action; Path destination;
                    if (!resolution.compatible) {
                        action = disabled ? Action.KEEP : Action.DISABLE;
                        destination = disabled ? installed : installed.resolveSibling(name + ".disabled");
                    } else {
                        validateResolution(resolution);
                        projects.add(resolution.projectId); dependencies.addAll(resolution.dependencies);
                        boolean same = resolution.sha512 != null ? resolution.sha512.equalsIgnoreCase(SharedArtifactCache.hash(installed, "SHA-512")) : resolution.sha1 != null ? resolution.sha1.equalsIgnoreCase(SharedArtifactCache.hash(installed, "SHA-1")) : resolution.md5 != null && resolution.md5.equalsIgnoreCase(SharedArtifactCache.hash(installed, "MD5"));
                        action = disabled ? Action.ENABLE : same ? Action.KEEP : Action.UPDATE;
                        destination = action == Action.KEEP ? installed : directory.resolve(resolution.fileName);
                    }
                    if (!destinations.add(destination)) throw new IOException("Two artifacts target the same filename: " + destination.getFileName());
                    changes.add(new Change(installed, destination, originalHash, action, resolution));
                } catch (Exception exception) { errors.add(name + ": " + exception.getMessage()); }
            }
        }
        Set<String> visited = new HashSet<>(); Map<String, String> pins = new HashMap<>();
        for (int at = 0; at < dependencies.size(); at++) {
            Dependency dependency = dependencies.get(at);
            String key = dependency.versionId == null ? dependency.projectId : dependency.projectId + ":" + dependency.versionId;
            if (!visited.add(key)) continue;
            if (dependencies.size() > 512) { errors.add("Dependency graph exceeds 512 entries"); break; }
            try {
                Resolution resolution = resolver.dependency(profile, dependency);
                if (!resolution.compatible) throw new IOException("No compatible required dependency: " + dependency.projectId);
                validateResolution(resolution);
                // A pinned dependency must be the selected version, not an arbitrary newer project release.
                Change existing = null;
                for (Change change : changes) if (Objects.equals(change.resolution.projectId, resolution.projectId)) { existing = change; break; }
                if (dependency.versionId != null) {
                    String previous = pins.putIfAbsent(resolution.projectId, dependency.versionId);
                    if (previous != null && !previous.equals(dependency.versionId)) throw new IOException("Conflicting pinned dependencies for " + resolution.projectId);
                }
                if (existing != null) {
                    if (!existing.resolution.compatible || dependency.versionId != null && !dependency.versionId.equals(existing.resolution.versionId)) {
                        Path destination = directory.resolve(resolution.fileName);
                        destinations.remove(existing.destination);
                        if (!destinations.add(destination)) throw new IOException("Pinned dependency filename collides: " + resolution.fileName);
                        boolean disabled = existing.installed != null && existing.installed.getFileName().toString().endsWith(".disabled");
                        changes.set(changes.indexOf(existing), new Change(existing.installed, destination, existing.originalSHA256, existing.installed == null ? Action.ADD : disabled ? Action.ENABLE : Action.UPDATE, resolution));
                        dependencies.addAll(resolution.dependencies);
                    }
                    continue;
                }
                Path destination = directory.resolve(resolution.fileName);
                if (!destinations.add(destination)) throw new IOException("Dependency filename collides: " + resolution.fileName);
                changes.add(new Change(null, destination, null, Action.ADD, resolution)); projects.add(resolution.projectId); dependencies.addAll(resolution.dependencies);
            } catch (Exception exception) { errors.add("Dependency " + key + ": " + exception.getMessage()); }
        }
        Set<String> activeProjects = new HashSet<>(); for (Change change : changes) if (change.resolution.compatible) activeProjects.add(change.resolution.projectId);
        for (Change change : changes) for (String incompatible : change.resolution.incompatibleProjects) if (activeProjects.contains(incompatible)) errors.add("Incompatible projects: " + change.resolution.projectId + " and " + incompatible);
        return new Plan(profile, changes, errors);
    }
    public void apply(Plan plan, boolean confirmDisabling) throws IOException {
        if (!plan.errors.isEmpty()) throw new IOException("Check failed; no profile changes applied: " + String.join("; ", plan.errors));
        if (!plan.disabled().isEmpty() && !confirmDisabling) throw new IOException("Confirmation required before disabling incompatible artifacts");
        Path profileRoot = plan.profile.directoryPath;
        Files.createDirectories(profileRoot);
        Path lockPath = profileRoot.resolve(".autoplug-update.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock;
            try { lock = channel.tryLock(); } catch (OverlappingFileLockException busy) { throw new IOException("Profile is already being updated", busy); }
            if (lock == null) throw new IOException("Profile is already being updated");
            try { applyLocked(plan); } finally { lock.release(); }
        }
    }
    private void applyLocked(Plan plan) throws IOException {
        Path profileRoot = plan.profile.directoryPath;
        Path transactions = profileRoot.resolve(".autoplug-transactions"); Files.createDirectories(transactions);
        recover(profileRoot, transactions);
        List<Change> changed = new ArrayList<>(); Set<Path> originals = new HashSet<>();
        for (Change change : plan.changes) {
            ProfileFiles.contained(profileRoot, change.destination);
            if (change.installed != null) {
                ProfileFiles.contained(profileRoot, change.installed);
                if (!change.originalSHA256.equals(SharedArtifactCache.hash(change.installed, "SHA-256"))) throw new IOException("Artifact changed after check: " + change.installed);
                originals.add(change.installed);
            }
            if (change.action != Action.KEEP) changed.add(change);
        }
        for (Change change : changed) if (Files.exists(change.destination) && !originals.contains(change.destination)) throw new IOException("Refusing to replace an unreviewed file: " + change.destination);
        if (changed.isEmpty()) return;
        Properties journal = new Properties(); journal.setProperty("state", "STAGING"); journal.setProperty("count", String.valueOf(changed.size()));
        Path sourceFile = profileRoot.resolve("asset-sources.properties");
        boolean sourceExisted = Files.exists(sourceFile, LinkOption.NOFOLLOW_LINKS);
        if (sourceExisted && !Files.isRegularFile(sourceFile, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid asset source metadata file");
        String sourceHash = sourceExisted ? SharedArtifactCache.hash(sourceFile, "SHA-256") : null;
        Properties sourceMetadata = new Properties();
        if (sourceExisted) try (InputStream input = Files.newInputStream(sourceFile)) { sourceMetadata.load(input); }
        boolean sourceChanged = false;
        Path transaction = Files.createTempDirectory(transactions, "update-");
        List<Path> staged = new ArrayList<>();
        boolean prepared = false;
        try {
            for (int index = 0; index < changed.size(); index++) {
                Change change = changed.get(index);
                journal.setProperty(index + ".destination", profileRoot.relativize(change.destination).toString());
                if (change.installed != null) {
                    journal.setProperty(index + ".original", profileRoot.relativize(change.installed).toString());
                    Files.copy(change.installed, transaction.resolve(index + ".backup"));
                }
                Path cached = null;
                if (change.action == Action.DISABLE) cached = cache.store(change.installed);
                else {
                    Path download = transaction.resolve(index + ".jar"); downloader.download(change.resolution.url, download);
                    verify(download, change.resolution); cached = cache.store(download);
                }
                staged.add(cached);
                if (change.resolution.compatible) {
                    String project = change.resolution.projectId, provider = "modrinth", id = project;
                    int colon = project.indexOf(':');
                    if (colon != -1) { provider = project.substring(0, colon); id = project.substring(colon + 1); }
                    sourceMetadata.setProperty(change.destination.getFileName().toString() + "." + provider, id);
                    sourceChanged = true;
                }
            }
            if (sourceChanged) {
                int metadataIndex = changed.size(); journal.setProperty("count", String.valueOf(metadataIndex + 1)); journal.setProperty(metadataIndex + ".destination", "asset-sources.properties");
                if (sourceExisted) { journal.setProperty(metadataIndex + ".original", "asset-sources.properties"); Files.copy(sourceFile, transaction.resolve(metadataIndex + ".backup")); }
                try (OutputStream output = Files.newOutputStream(transaction.resolve("asset-sources.part"))) { sourceMetadata.store(output, "Verified artifact project identities; explicit entries may be added here"); }
            }
            // Network staging can take time: verify reviewed originals a second time immediately before committing.
            for (Change change : changed) if (change.installed != null && !change.originalSHA256.equals(SharedArtifactCache.hash(change.installed, "SHA-256"))) throw new IOException("Artifact changed during download staging: " + change.installed);
            for (Change change : changed) if (Files.exists(change.destination) && !originals.contains(change.destination)) throw new IOException("Unreviewed destination appeared during download staging: " + change.destination);
            if (sourceChanged && (sourceExisted ? !sourceHash.equals(SharedArtifactCache.hash(sourceFile, "SHA-256")) : Files.exists(sourceFile))) throw new IOException("Asset source metadata changed during download staging");
            journal.setProperty("state", "PREPARED"); writeJournal(transaction, journal); prepared = true;
            // Remove reviewed original paths before installing new names; this also handles legitimate filename swaps.
            for (Change change : changed) if (change.installed != null) Files.delete(change.installed);
            for (int index = 0; index < changed.size(); index++) {
                Change change = changed.get(index);
                cache.link(staged.get(index), change.destination);
            }
            if (sourceChanged) ProfileFiles.move(transaction.resolve("asset-sources.part"), sourceFile);
            journal.setProperty("state", "COMMITTED"); writeJournal(transaction, journal);
        } catch (IOException | RuntimeException failure) {
            if (prepared) try { rollback(profileRoot, transaction, journal); } catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); throw failure; }
            ProfileFiles.deleteTree(transactions, transaction); throw failure;
        }
        ProfileFiles.deleteTree(transactions, transaction);
    }
    private static void recover(Path profileRoot, Path transactions) throws IOException {
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(transactions, "update-*")) {
            for (Path transaction : directories) {
                ProfileFiles.contained(transactions, transaction);
                Path file = transaction.resolve("journal.properties");
                if (Files.exists(file)) {
                    Properties journal = new Properties(); try (InputStream input = Files.newInputStream(file)) { journal.load(input); }
                    if ("PREPARED".equals(journal.getProperty("state"))) rollback(profileRoot, transaction, journal);
                }
                ProfileFiles.deleteTree(transactions, transaction);
            }
        }
    }
    private static void rollback(Path root, Path transaction, Properties journal) throws IOException {
        int count = Integer.parseInt(journal.getProperty("count"));
        if (count < 1 || count > 4096) throw new IOException("Invalid profile transaction size");
        Map<Path, Path> originals = new HashMap<>();
        for (int index = 0; index < count; index++) {
            String original = journal.getProperty(index + ".original");
            if (original != null) originals.put(ProfileFiles.contained(root, root.resolve(original)), transaction.resolve(index + ".backup"));
        }
        IOException failures = null;
        for (int index = 0; index < count; index++) {
            try {
                Path destination = ProfileFiles.contained(root, root.resolve(journal.getProperty(index + ".destination")));
                Path backup = originals.get(destination);
                // An original which could not be removed (for example an open Windows jar) already has the correct bytes.
                if (backup != null && sameBytes(destination, backup)) continue;
                Files.deleteIfExists(destination);
            } catch (IOException failure) { if (failures == null) failures = failure; else failures.addSuppressed(failure); }
        }
        for (Map.Entry<Path, Path> original : originals.entrySet()) try {
            if (!sameBytes(original.getKey(), original.getValue())) Files.copy(original.getValue(), original.getKey(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) { if (failures == null) failures = failure; else failures.addSuppressed(failure); }
        if (failures != null) throw failures;
    }
    private static boolean sameBytes(Path current, Path backup) throws IOException {
        return Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)
                && SharedArtifactCache.hash(current, "SHA-256").equals(SharedArtifactCache.hash(backup, "SHA-256"));
    }
    private static void writeJournal(Path transaction, Properties journal) throws IOException {
        Path temporary = transaction.resolve("journal.part");
        try (OutputStream output = Files.newOutputStream(temporary)) { journal.store(output, "Profile transaction"); }
        ProfileFiles.move(temporary, transaction.resolve("journal.properties"));
    }
    private static void validateResolution(Resolution resolution) throws IOException {
        if (resolution.fileName == null || !resolution.fileName.endsWith(".jar") || !Paths.get(resolution.fileName).getFileName().toString().equals(resolution.fileName) || resolution.fileName.contains("/") || resolution.fileName.contains("\\")) throw new IOException("Invalid artifact filename");
        if (resolution.url == null || resolution.projectId == null) throw new IOException("Missing artifact identity or download URL");
    }
    private static void verify(Path download, Resolution resolution) throws IOException {
        if (resolution.sha1 != null && !resolution.sha1.equalsIgnoreCase(SharedArtifactCache.hash(download, "SHA-1"))) throw new IOException("Artifact SHA-1 mismatch");
        if (resolution.sha512 != null && !resolution.sha512.equalsIgnoreCase(SharedArtifactCache.hash(download, "SHA-512"))) throw new IOException("Artifact SHA-512 mismatch");
        if (resolution.md5 != null && !resolution.md5.equalsIgnoreCase(SharedArtifactCache.hash(download, "MD5"))) throw new IOException("Artifact MD5 mismatch");
        try (ZipFile jar = new ZipFile(download.toFile())) { if (!jar.entries().hasMoreElements()) throw new IOException("Empty artifact jar"); }
    }
    private static void downloadHttps(String value, Path destination) throws IOException {
        URL url = new URL(value);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("Artifact download must use HTTPS");
        URLConnection connection = url.openConnection(); connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setRequestProperty("User-Agent", "AutoPlug-Client/10.2.0 (native profiles)");
        try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[65536]; long total = 0; int read;
            while ((read = input.read(buffer)) != -1) { total += read; if (total > 1024L * 1024 * 1024) throw new IOException("Artifact exceeds 1 GiB"); output.write(buffer, 0, read); }
        }
    }
}
