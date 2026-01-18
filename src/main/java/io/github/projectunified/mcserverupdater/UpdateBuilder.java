package io.github.projectunified.mcserverupdater;

import io.github.projectunified.mcserverupdater.api.Checksum;
import io.github.projectunified.mcserverupdater.api.DebugConsumer;
import io.github.projectunified.mcserverupdater.api.Updater;
import io.github.projectunified.mcserverupdater.updater.*;
import io.github.projectunified.mcserverupdater.util.Utils;
import io.github.projectunified.mcserverupdater.util.VersionQuery;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Where to create the update process
 */
public final class UpdateBuilder {
    private static final Map<String, Function<VersionQuery, Updater>> UPDATERS = new HashMap<>();

    static {
        registerUpdater(versionQuery -> new PaperUpdater(versionQuery, "paper"), "paper", "papermc", "paperspigot");
        registerUpdater(versionQuery -> new PaperUpdater(versionQuery, "travertine"), "travertine");
        registerUpdater(versionQuery -> new PaperUpdater(versionQuery, "waterfall"), "waterfall");
        registerUpdater(versionQuery -> new PaperUpdater(versionQuery, "velocity"), "velocity");
        registerUpdater(versionQuery -> new PaperUpdater(versionQuery, "folia"), "folia");
        registerUpdater(versionQuery -> new PandaSpigotUpdater(versionQuery, "pandaspigot"), "pandaspigot");
        registerUpdater(PurpurUpdater::new, "purpur", "purpurmc");
        registerUpdater(BungeeCordUpdater::new, "bungeecord", "bungee");
        registerUpdater(SpigotUpdater::new, "spigot", "spigotmc");
        registerUpdater(PatinaUpdater::new, "patina", "patinamc");
        registerUpdater(PufferfishUpdater::new, "pufferfish");
        registerUpdater(versionQuery -> new FabricUpdater(versionQuery, true), "fabricmc", "fabric");
        registerUpdater(versionQuery -> new FabricUpdater(versionQuery, false), "fabricmc-dev", "fabric-dev");
        for (SpongeUpdater.Type type : SpongeUpdater.Type.values()) {
            registerUpdater(versionQuery -> new SpongeUpdater(versionQuery, type, false), type.getName());
            registerUpdater(versionQuery -> new SpongeUpdater(versionQuery, type, true), type.getName() + "-recommended");
        }
        registerUpdater(versionQuery -> new MohistUpdater(versionQuery, "mohist"), "mohist");
        registerUpdater(versionQuery -> new MohistUpdater(versionQuery, "banner"), "banner");
        registerUpdater(versionQuery -> new MohistUpdater(versionQuery, "youer"), "youer");
        registerUpdater(PlazmaUpdater::new, "plazma");
        registerUpdater(DivineUpdater::new, "divine", "divinemc");
        registerUpdater(LeafUpdater::new, "leaf");
        registerUpdater(versionQuery -> new LeavesUpdater(versionQuery, "leaves"), "leaves", "leavesmc");
        registerUpdater(LuminolUpdater::new, "luminol", "luminolmc");
        registerUpdater(CanvasUpdater::new, "canvas", "canvasmc");
        registerUpdater(versionQuery -> new MultiPaperUpdater(versionQuery, "multipaper"), "multipaper");
        registerUpdater(versionQuery -> new MultiPaperUpdater(versionQuery, "multipaper", "master"), "multipaper-master");
        registerUpdater(versionQuery -> new MultiPaperUpdater(versionQuery, "shreddedpaper"), "shreddedpaper");
        registerUpdater(GeyserUpdater::new, "geyser", "geysermc");
    }

    private final String project;
    private String version = "default";
    private File outputFile = new File("server.jar");
    private File workingDirectory = new File(".");
    private boolean checkOnly = false;
    private ChecksumSupplier checksumSupplier = () -> "";
    private ChecksumConsumer checksumConsumer = s -> {
    };
    private DebugConsumer debugConsumer = s -> {
    };
    private String userAgent;

    private UpdateBuilder(String project) {
        this.project = project;
    }

    /**
     * Register a updater
     *
     * @param updater the updater
     * @param names   the names
     */
    public static void registerUpdater(Function<VersionQuery, Updater> updater, String... names) {
        for (String name : names) {
            UPDATERS.put(name, updater);
        }
    }

    /**
     * Get the names of available updaters
     *
     * @return the names
     */
    public static Set<String> getUpdaterNames() {
        return UPDATERS.keySet();
    }

    /**
     * Create the update process
     *
     * @param project the project
     * @return the update process
     */
    public static UpdateBuilder updateProject(String project) {
        return new UpdateBuilder(project);
    }

    /**
     * Set the version
     *
     * @param version the version
     * @return the update process
     */
    public UpdateBuilder version(String version) {
        this.version = version;
        return this;
    }

    /**
     * Set the output file
     *
     * @param outputFile the output file
     * @return the update process
     */
    public UpdateBuilder outputFile(File outputFile) {
        this.outputFile = outputFile;
        return this;
    }

    /**
     * Set the output file
     *
     * @param outputFile the output file
     * @return the update process
     */
    public UpdateBuilder outputFile(String outputFile) {
        return outputFile(new File(outputFile));
    }

    /**
     * Set the working directory
     *
     * @param workingDirectory the working directory
     * @return the update process
     */
    public UpdateBuilder workingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
        return this;
    }

    /**
     * Set the working directory
     *
     * @param workingDirectory the working directory
     * @return the update process
     */
    public UpdateBuilder workingDirectory(String workingDirectory) {
        return workingDirectory(new File(workingDirectory));
    }

    /**
     * Set the checksum supplier
     *
     * @param checksumSupplier the checksum supplier
     * @return the update process
     */
    public UpdateBuilder checksumSupplier(ChecksumSupplier checksumSupplier) {
        this.checksumSupplier = checksumSupplier;
        return this;
    }

    /**
     * Set the checksum consumer
     *
     * @param checksumConsumer the checksum consumer
     * @return the update process
     */
    public UpdateBuilder checksumConsumer(ChecksumConsumer checksumConsumer) {
        this.checksumConsumer = checksumConsumer;
        return this;
    }

    /**
     * Set the debug consumer
     *
     * @param debugConsumer the debug consumer
     * @return the update process
     */
    public UpdateBuilder debugConsumer(DebugConsumer debugConsumer) {
        this.debugConsumer = debugConsumer;
        return this;
    }

    /**
     * Set the checksum file
     *
     * @param checksumFile the checksum file
     * @return the update process
     */
    public UpdateBuilder checksumFile(File checksumFile) {
        return this
                .checksumSupplier(() -> {
                    if (!checksumFile.exists() && Utils.isFailedToCreateFile(checksumFile)) {
                        return "";
                    }
                    return Utils.getString(checksumFile);
                })
                .checksumConsumer(checksum -> {
                    if (checksumFile.exists() || !Utils.isFailedToCreateFile(checksumFile)) {
                        Utils.writeString(checksumFile, checksum);
                    }
                });
    }

    /**
     * Set the checksum file
     *
     * @param checksumFile the checksum file
     * @return the update process
     */
    public UpdateBuilder checksumFile(String checksumFile) {
        return checksumFile(new File(workingDirectory, checksumFile));
    }

    /**
     * Set if the update process should only check the checksum
     *
     * @param checkOnly the check only
     * @return the update process
     */
    public UpdateBuilder checkOnly(boolean checkOnly) {
        this.checkOnly = checkOnly;
        return this;
    }

    /**
     * Set the user agent for the update process
     *
     * @param userAgent the user agent string
     * @return the update process
     */
    public UpdateBuilder userAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Get the checksum consumer
     *
     * @return the checksum consumer
     */
    public ChecksumConsumer checksumConsumer() {
        return checksumConsumer;
    }

    /**
     * Get the checksum supplier
     *
     * @return the checksum supplier
     */
    public ChecksumSupplier checksumSupplier() {
        return checksumSupplier;
    }

    /**
     * Get the working directory
     *
     * @param create if the directory should be created if it doesn't exist
     * @return the working directory
     */
    public File workingDirectory(boolean create) {
        if (!workingDirectory.exists() && create) {
            workingDirectory.mkdirs();
        }
        return workingDirectory;
    }

    /**
     * Get the working directory
     *
     * @return the working directory
     */
    public File workingDirectory() {
        return workingDirectory(true);
    }

    /**
     * Get the debug consumer
     *
     * @return the debug consumer
     */
    public DebugConsumer debugConsumer() {
        return debugConsumer;
    }

    /**
     * Get the user agent
     *
     * @return the user agent string
     */
    public String userAgent() {
        return userAgent;
    }

    /**
     * Debug a message
     *
     * @param message the message
     */
    public void debug(String message) {
        debugConsumer.consume(message);
    }

    /**
     * Execute the update process
     *
     * @return the update status
     */
    public CompletableFuture<UpdateStatus> executeAsync() {
        return CompletableFuture.supplyAsync(() -> {
            VersionQuery versionQuery = new VersionQuery(version, this);
            Updater update = Optional.ofNullable(UPDATERS.get(project.toLowerCase(Locale.ROOT))).map(f -> f.apply(versionQuery)).orElse(null);
            if (update == null) {
                return UpdateStatus.NO_PROJECT;
            }

            Checksum checksum = update.getChecksumChecker();

            if (checkOnly && checksum == null) {
                return UpdateStatus.FAILED;
            }

            try {
                if (outputFile.exists()) {
                    if (checksum != null) {
                        if (checksum.checksum(outputFile)) {
                            return UpdateStatus.UP_TO_DATE;
                        } else if (checkOnly) {
                            return UpdateStatus.OUT_OF_DATE;
                        }
                    }
                } else if (Utils.isFailedToCreateFile(outputFile)) {
                    return UpdateStatus.FILE_FAILED;
                }
            } catch (Exception e) {
                throw new CompletionException(e);
            }

            try {
                if (update.update(outputFile)) {
                    if (checksum != null) {
                        checksum.setChecksum(outputFile);
                    }
                    return UpdateStatus.SUCCESS;
                } else {
                    return UpdateStatus.FAILED;
                }
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).exceptionally(UpdateStatus::unknownError);
    }

    /**
     * Execute the update process
     *
     * @return the status of the process
     */
    public UpdateStatus execute() throws ExecutionException, InterruptedException {
        return executeAsync().get();
    }

    public interface ChecksumSupplier {
        String get() throws IOException;
    }

    public interface ChecksumConsumer {
        void accept(String checksum) throws IOException;
    }
}
