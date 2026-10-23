package com.osiris.autoplug.client.mc.update;

import com.osiris.autoplug.client.mc.profiles.Profile;
import com.osiris.autoplug.client.mc.profiles.ProfileRepository;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Plans target-version changes first, then applies the approved plan without global configuration. */
public final class ProfileUpdateService {
    public interface Engine {
        Decision resolve(Profile profile, Path installedJar, String targetVersion) throws IOException;
        void download(Decision decision, Path destination) throws IOException;
    }

    public enum Action { KEEP, REPLACE, DISABLE, ERROR }

    public static final class Decision {
        private final Action action;
        private final String downloadUrl, sha256, explanation;
        private Decision(Action action, String url, String hash, String explanation) {
            this.action = action; this.downloadUrl = url; this.sha256 = hash;
            this.explanation = explanation == null ? "" : explanation;
        }
        public static Decision keep(String explanation) { return new Decision(Action.KEEP, null, null, explanation); }
        public static Decision replace(String url, String hash, String explanation) {
            if (url == null || url.trim().isEmpty()) throw new IllegalArgumentException("Missing download URL");
            return new Decision(Action.REPLACE, url, hash, explanation);
        }
        /** Only use after the provider positively reports no release for the requested version/loader. */
        public static Decision incompatible(String explanation) { return new Decision(Action.DISABLE, null, null, explanation); }
        public static Decision error(String explanation) { return new Decision(Action.ERROR, null, null, explanation); }
        public Action getAction() { return action; }
        public String getDownloadUrl() { return downloadUrl; }
        public String getSha256() { return sha256; }
        public String getExplanation() { return explanation; }
    }

    public static final class Change {
        private final String fileName;
        private final Decision decision;
        private Change(String name, Decision decision) { this.fileName = name; this.decision = decision; }
        public String getFileName() { return fileName; }
        public Decision getDecision() { return decision; }
        public Action getAction() { return decision.getAction(); }
        public String getExplanation() { return decision.getExplanation(); }
    }

    public static final class Plan {
        private final Profile source;
        private final String targetId, targetVersion;
        private final List<Change> changes;
        private final Map<String, String> snapshot;
        private final ProfileUpdateService owner;
        private Plan(ProfileUpdateService owner, Profile source, String targetId, String version,
                     List<Change> changes, Map<String, String> snapshot) {
            this.owner = owner; this.source = source; this.targetId = targetId; this.targetVersion = version;
            this.changes = Collections.unmodifiableList(changes); this.snapshot = snapshot;
        }
        public Profile getProfile() { return source; }
        public String getTargetProfileId() { return targetId; }
        public String getTargetVersion() { return targetVersion; }
        public boolean isClone() { return !source.getId().equals(targetId); }
        public List<Change> getChanges() { return changes; }
        public boolean canApply() { return changes.stream().noneMatch(c -> c.getAction() == Action.ERROR); }
        public List<Change> getDisabledAssets() {
            List<Change> disabled = new ArrayList<>();
            for (Change change : changes) if (change.getAction() == Action.DISABLE) disabled.add(change);
            return Collections.unmodifiableList(disabled);
        }
        public String getSummary() {
            StringBuilder result = new StringBuilder(source.getId()).append(" -> ").append(targetId)
                    .append(" / Minecraft ").append(targetVersion).append(" / ").append(source.getLoader());
            for (Change change : changes) result.append('\n').append(change.getAction()).append(' ')
                    .append(change.getFileName()).append(": ").append(change.getExplanation());
            if (!getDisabledAssets().isEmpty()) result.append("\nIncompatible jars will be renamed .jar.disabled; explicit approval is required.");
            return result.toString();
        }
    }

    private final ProfileRepository repository;
    private final SharedArtifactCache cache;
    private final Engine engine;

    public ProfileUpdateService(ProfileRepository repository, SharedArtifactCache cache, Engine engine) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.cache = java.util.Objects.requireNonNull(cache);
        this.engine = java.util.Objects.requireNonNull(engine);
    }

    /** Read-only check. Neither profile metadata nor configuration is changed. */
    public synchronized Plan check(String profileId, String targetGameVersion) throws IOException {
        return check(profileId, targetGameVersion, () -> false);
    }

    public synchronized Plan check(String profileId, String targetGameVersion,
                                   java.util.function.BooleanSupplier cancelled) throws IOException {
        checkCancelled(cancelled);
        return plan(repository.require(profileId), profileId, targetGameVersion, cancelled);
    }

    /** Read-only proposal for an independent clone and explicit version migration. */
    public synchronized Plan planClone(String sourceId, String targetId, String targetGameVersion) throws IOException {
        return planClone(sourceId, targetId, targetGameVersion, () -> false);
    }

    public synchronized Plan planClone(String sourceId, String targetId, String targetGameVersion,
                                       java.util.function.BooleanSupplier cancelled) throws IOException {
        checkCancelled(cancelled);
        if (sourceId.equals(targetId)) throw new IllegalArgumentException("Clone needs a new profile ID");
        if (repository.find(targetId).isPresent()) throw new IOException("Profile already exists: " + targetId);
        return plan(repository.require(sourceId), targetId, targetGameVersion, cancelled);
    }

    private Plan plan(Profile profile, String targetId, String version, java.util.function.BooleanSupplier cancelled) throws IOException {
        checkCancelled(cancelled);
        if (version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,79}")) {
            throw new IllegalArgumentException("Invalid target Minecraft version");
        }
        Map<String, String> before = snapshot(profile.getDirectory());
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(profile.getAssetsDirectory())) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (name.endsWith(".jar") || name.endsWith(".jar.disabled")) {
                    if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a direct jar: " + entry);
                    jars.add(entry);
                }
            }
        }
        jars.sort(Comparator.comparing(p -> p.getFileName().toString()));
        List<Change> changes = new ArrayList<>();
        for (Path jar : jars) {
            checkCancelled(cancelled);
            Decision decision;
            try {
                decision = java.util.Objects.requireNonNull(engine.resolve(profile, jar, version));
                if (decision.getAction() == Action.REPLACE && decision.getSha256() != null
                        && !jar.getFileName().toString().endsWith(".disabled")
                        && decision.getSha256().equalsIgnoreCase(SharedArtifactCache.sha256(jar))) {
                    decision = Decision.keep("Already matches the compatible target artifact");
                }
            } catch (IOException failure) {
                decision = Decision.error(failure.getMessage());
            }
            checkCancelled(cancelled);
            changes.add(new Change(jar.getFileName().toString(), decision));
        }
        checkCancelled(cancelled);
        if (!before.equals(snapshot(profile.getDirectory()))) throw new IOException("Profile changed during the check; check again");
        return new Plan(this, profile, targetId, version, changes, before);
    }

    /** Download failures leave the old profile intact; unresolved checks are never applied. */
    public synchronized Profile apply(Plan plan, boolean confirmDisabling) throws IOException {
        return apply(plan, confirmDisabling, () -> false);
    }

    public synchronized Profile apply(Plan plan, boolean confirmDisabling,
                                      java.util.function.BooleanSupplier cancelled) throws IOException {
        synchronized (repository) {
            return applyLocked(plan, confirmDisabling, cancelled);
        }
    }

    private Profile applyLocked(Plan plan, boolean confirmDisabling,
                                java.util.function.BooleanSupplier cancelled) throws IOException {
        checkCancelled(cancelled);
        if (plan == null || plan.owner != this) throw new IllegalArgumentException("Plan belongs to another updater");
        repository.requireNotInUse(plan.source.getId());
        repository.requireNotInUse(plan.targetId);
        if (!plan.canApply()) throw new IOException("Resolve the check errors before applying this plan");
        if (!plan.getDisabledAssets().isEmpty() && !confirmDisabling) throw new IOException("The disabled-asset summary needs explicit approval");
        Profile current = repository.require(plan.source.getId());
        if (!plan.snapshot.equals(snapshot(current.getDirectory()))) throw new IOException("Profile changed since approval; check again");
        if (plan.isClone() && repository.find(plan.targetId).isPresent()) throw new IOException("Clone target already exists");

        Path staging = Files.createTempDirectory(repository.getProfilesDirectory(), ".update-");
        Profile target = current;
        boolean cloned = false, replaced = false;
        Path backup = staging.resolve("old-assets");
        try {
            Path prepared = staging.resolve("prepared");
            copyTree(current.getAssetsDirectory(), prepared, cancelled);
            for (Change change : plan.changes) {
                checkCancelled(cancelled);
                Path staged = prepared.resolve(change.fileName);
                Files.delete(staged);
                String enabledName = change.fileName.endsWith(".disabled")
                        ? change.fileName.substring(0, change.fileName.length() - ".disabled".length()) : change.fileName;
                String destinationName = change.getAction() == Action.KEEP ? change.fileName
                        : change.getAction() == Action.DISABLE ? enabledName + ".disabled" : enabledName;
                Path destination = prepared.resolve(destinationName);
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Conflicting enabled/disabled jars: " + enabledName);
                Path object = change.getAction() == Action.REPLACE
                        ? cache.fetch(change.decision.sha256, path -> {
                            checkCancelled(cancelled);
                            engine.download(change.decision, path);
                            checkCancelled(cancelled);
                        })
                        : cache.store(current.getAssetsDirectory().resolve(change.fileName));
                checkCancelled(cancelled);
                cache.link(object, destination);
            }
            // Re-check after potentially slow downloads and before the first profile mutation.
            if (!plan.snapshot.equals(snapshot(current.getDirectory()))) throw new IOException("Profile changed during preparation; check again");
            checkCancelled(cancelled);
            if (plan.isClone()) {
                target = repository.cloneProfile(current.getId(), plan.targetId);
                cloned = true;
            }
            checkCancelled(cancelled);
            // From this first move through metadata commit, finish or roll back as one unit.
            // A cancellation arriving after commit must not misreport a successful update as cancelled.
            Files.move(target.getAssetsDirectory(), backup);
            try {
                Files.move(prepared, target.getAssetsDirectory());
                replaced = true;
                target = repository.recordMigratedVersion(target.getId(), plan.targetVersion);
            } catch (IOException failure) {
                if (replaced) removeTree(target.getAssetsDirectory());
                Files.move(backup, target.getAssetsDirectory());
                throw failure;
            }
            return target;
        } catch (IOException | RuntimeException failure) {
            if (cloned) {
                try { repository.delete(plan.targetId); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        } finally {
            removeTree(staging);
        }
    }

    private static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> values = new TreeMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) {
                values.put(root.relativize(path).toString() + "/", "directory");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile()) throw new IOException("Linked/special profile files are not supported: " + path);
                values.put(root.relativize(path).toString(), SharedArtifactCache.sha256(path));
                return FileVisitResult.CONTINUE;
            }
        });
        return values;
    }

    private static void copyTree(Path source, Path target, java.util.function.BooleanSupplier cancelled) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) throws IOException {
                checkCancelled(cancelled);
                Files.createDirectory(target.resolve(source.relativize(path)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                checkCancelled(cancelled);
                if (!attributes.isRegularFile()) throw new IOException("Cannot copy linked/special profile file: " + path);
                Files.copy(path, target.resolve(source.relativize(path)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void checkCancelled(java.util.function.BooleanSupplier cancelled) throws IOException {
        if (Thread.currentThread().isInterrupted() || (cancelled != null && cancelled.getAsBoolean())) {
            throw new java.io.InterruptedIOException("Profile update cancelled before commit; profile unchanged");
        }
    }

    private static void removeTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(directory); return FileVisitResult.CONTINUE;
            }
        });
    }
}
